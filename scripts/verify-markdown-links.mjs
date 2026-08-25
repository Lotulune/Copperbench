import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, normalize, resolve, sep } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ignoredDirectories = new Set(['.git', '.gradle', '.tmp', 'build', 'node_modules', 'test-results']);
const trackedPathList = execFileSync('git', ['ls-files', '-z'], {
  cwd: repositoryRoot,
  encoding: 'utf8',
  maxBuffer: 16 * 1024 * 1024,
}).split('\0').filter(Boolean).map((path) => normalize(resolve(repositoryRoot, path)));
const trackedPaths = new Set(trackedPathList);

function markdownFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    if (entry.isDirectory() && ignoredDirectories.has(entry.name)) return [];
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return markdownFiles(path);
    return extname(entry.name).toLowerCase() === '.md' ? [path] : [];
  });
}

function localTargets(markdown) {
  const targets = [];
  const markdownLinks = /!?(?:\[[^\]]*\])\(([^)]+)\)/g;
  const htmlLinks = /\b(?:href|src)=["']([^"']+)["']/gi;
  for (const pattern of [markdownLinks, htmlLinks]) {
    for (const match of markdown.matchAll(pattern)) targets.push(match[1].trim());
  }
  return targets;
}

const failures = [];
for (const file of markdownFiles(repositoryRoot)) {
  const markdown = readFileSync(file, 'utf8');
  for (let target of localTargets(markdown)) {
    if (!target || target.startsWith('#') || /^(?:[a-z]+:|\/\/)/i.test(target)) continue;
    if (target.startsWith('<') && target.endsWith('>')) target = target.slice(1, -1);
    target = target.split('#', 1)[0].split('?', 1)[0];
    if (!target) continue;
    let decoded;
    try {
      decoded = decodeURIComponent(target);
    } catch {
      failures.push(`${file}: invalid URL encoding in ${target}`);
      continue;
    }
    const resolved = normalize(resolve(dirname(file), decoded));
    const insideRepository = resolved === repositoryRoot || resolved.startsWith(`${repositoryRoot}${sep}`);
    const tracked = trackedPaths.has(resolved)
      || (existsSync(resolved) && statSync(resolved).isDirectory()
        && trackedPathList.some((path) => path.startsWith(`${resolved}${sep}`)));
    if (!insideRepository || !existsSync(resolved) || !tracked) {
      failures.push(`${file}: missing local target ${target}`);
    }
  }
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log('All local Markdown links resolve.');
