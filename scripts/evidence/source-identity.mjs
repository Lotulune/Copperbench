import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { lstatSync, readFileSync, readlinkSync } from 'node:fs';
import { resolve, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

// Cache/dependency directory names apply at any depth. Generated evidence roots
// do not: scripts/evidence and source packages named output/logs are source.
export const SOURCE_EXCLUSIONS = [
  '.git', '.gradle', '.tmp', '.codex', 'node_modules', '__pycache__',
];
const ROOT_SOURCE_EXCLUSIONS = ['jdk', 'output', 'evidence', 'logs', 'test-results',
  'playwright-report', 'blob-report', 'coverage'];
const GENERATED_SOURCE_PREFIXES = ['ui-shell/dist/', 'ui-shell/playwright-report/',
  'ui-shell/test-results/', 'ui-shell/blob-report/', 'ui-core/coverage/'];
export const SOURCE_PATH_FILTER_VERSION = 'source-paths-v2';

export function isSourcePath(path) {
  const parts = path.split('/');
  if (parts.some(part => SOURCE_EXCLUSIONS.includes(part))) return false;
  if (ROOT_SOURCE_EXCLUSIONS.includes(parts[0])) return false;
  // Preserve the generated-build boundary and the existing docs/build exception.
  if (parts.includes('build') && !(parts[0] === 'docs' && parts[1] === 'build')) return false;
  if (GENERATED_SOURCE_PREFIXES.some(prefix => path.startsWith(prefix))
    || path.endsWith('.pyc') || path.endsWith('.log')) return false;
  return true;
}

export const digest = value => createHash('sha256').update(value).digest('hex');

export function fileIdentity(path, name = undefined) {
  const stat = lstatSync(path);
  if (!stat.isFile()) throw new Error('An artifact identity requires a regular file.');
  return { name: name ?? path.split(/[\\/]/).at(-1), size: stat.size, sha256: digest(readFileSync(path)) };
}

export function captureSourceIdentity(repository) {
  const root = resolve(repository);
  const utf8 = bytes => new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  const git = (...args) => execFileSync('git', args, {
    cwd: root, env: { ...process.env, GIT_OPTIONAL_LOCKS: '0' },
    maxBuffer: 32 * 1024 * 1024, timeout: 60000,
  });
  const commit = git('rev-parse', 'HEAD').toString('ascii').trim();
  if (!/^[0-9a-f]{40}$/.test(commit)) throw new Error('Expected a full Git HEAD SHA.');
  const index = new Map();
  for (const entry of utf8(git('ls-files', '--stage', '-z')).split('\0').filter(Boolean)) {
    const match = /^(\d+) ([0-9a-f]+) (\d+)\t([\s\S]+)$/.exec(entry);
    if (!match || match[3] !== '0') throw new Error('Source identity cannot describe an unresolved Git index.');
    index.set(match[4], match[1]);
  }
  const paths = [...new Set(utf8(git('ls-files', '--cached', '--others', '--exclude-standard', '-z'))
    .split('\0').filter(Boolean))].filter(isSourcePath).sort();
  const files = paths.map(path => {
    const absolute = resolve(root, path);
    const rel = relative(root, absolute);
    if (rel === '..' || rel.startsWith(`..${sep}`) || resolve(absolute) === root)
      throw new Error('A source path escaped the repository.');
    let stat;
    try { stat = lstatSync(absolute); }
    catch (error) {
      if (error.code !== 'ENOENT') throw error;
      return { path, kind: 'missing', mode: index.get(path) ?? null, size: null, sha256: null };
    }
    if (stat.isSymbolicLink()) {
      const bytes = Buffer.from(readlinkSync(absolute));
      return { path, kind: 'symlink', mode: '120000', size: bytes.length, sha256: digest(bytes) };
    }
    if (!stat.isFile()) throw new Error(`Unsupported source entry: ${path}. Gitlinks need separate identity evidence.`);
    const mode = process.platform === 'win32' ? index.get(path) ?? '100644'
      : stat.mode & 0o111 ? '100755' : '100644';
    return { path, kind: 'file', mode, size: stat.size, sha256: digest(readFileSync(absolute)) };
  });
  const byPath = new Map(files.map(file => [file.path, file]));
  const status = utf8(git('status', '--porcelain=v1', '-z', '--untracked-files=all', '--no-renames'))
    .split('\0').filter(Boolean);
  const dirtyFiles = status.map(entry => ({ status: entry.slice(0, 2), path: entry.slice(3) }))
    .filter(entry => isSourcePath(entry.path)).sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0)
    .map(entry => ({ ...entry, ...(byPath.get(entry.path) ?? { kind: 'missing', sha256: null, size: null }) }));
  return {
    schemaVersion: '1.0', pathFilterVersion: SOURCE_PATH_FILTER_VERSION, commit, dirty: dirtyFiles.length > 0,
    scope: 'Git tracked files and non-ignored untracked files; deletions and symlinks included; no symlink traversal.',
    exclusions: [...SOURCE_EXCLUSIONS.map(name => `**/${name}/`), ...ROOT_SOURCE_EXCLUSIONS.map(name => `/${name}/`),
      ...GENERATED_SOURCE_PREFIXES, 'build except docs/build', '*.pyc', '*.log'],
    fileCount: files.length, sourceSha256: digest(JSON.stringify(files)),
    dirtyDeltaSha256: digest(JSON.stringify(dirtyFiles)), files, dirtyFiles,
    limitations: ['Ignored dependency/runtime/cache inputs and final binaries require separate artifact identities.',
      'Source hashes use the declared path-filter version; earlier manifests retain their original scope and must not be rewritten.'],
  };
}

if (typeof process !== 'undefined' && process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(JSON.stringify(captureSourceIdentity(process.argv[2] ?? process.cwd()), null, 2)); }
  catch (error) { console.error(`source identity: ${error.message}`); process.exitCode = 1; }
}
