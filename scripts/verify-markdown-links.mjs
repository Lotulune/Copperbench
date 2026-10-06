import { existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, normalize, resolve, sep } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const trackedPathList = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {
  cwd: repositoryRoot,
  encoding: 'utf8',
  maxBuffer: 16 * 1024 * 1024,
}).split('\0').filter(Boolean).map((path) => normalize(resolve(repositoryRoot, path)));
const trackedPaths = new Set(trackedPathList);

// Check repository documents, including new files, without traversing ignored
// build output or dependencies. Deleted tracked files are no longer sources.
const markdownFiles = [...trackedPaths].filter((path) =>
  extname(path).toLowerCase() === '.md' && existsSync(path) && statSync(path).isFile());

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
for (const file of markdownFiles) {
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
