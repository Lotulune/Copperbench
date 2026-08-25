import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ignoredDirectories = new Set(['.git', '.gradle', '.tmp', 'build', 'node_modules', 'test-results']);

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
    if (!resolved.startsWith(repositoryRoot) || !existsSync(resolved)) {
      failures.push(`${file}: missing local target ${target}`);
    }
  }
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log('All local Markdown links resolve.');
