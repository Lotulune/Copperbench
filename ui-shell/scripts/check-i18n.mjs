import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDirectory, '../..');
const catalogPath = join(projectRoot, 'ui-shell/src/i18n/zh.ts');
const sourceRoots = [
  join(projectRoot, 'src/main/java/dev/copperbench'),
  join(projectRoot, 'ui-shell/src'),
  join(projectRoot, 'ui-core/fixtures/v1.0')
];
const sourceExtensions = new Set(['.java', '.json', '.ts', '.tsx']);
const localizedPrefixes = [
  'action', 'approval', 'aria', 'capability', 'diagnostic', 'disposition', 'editor', 'field',
  'material', 'notice', 'placeholder', 'reason', 'scenario', 'status', 'task'
];
const dynamicKeys = [
  'workspace.default_name',
  'task.validate.started',
  'task.generate.started',
  'task.build.started',
  'task.export.started',
  'task.run_client.started',
  'task.generate.completed',
  'task.build.completed',
  'task.export.completed',
  'task.run_client.completed'
];

const files = [];
const visit = (directory) => {
  for (const name of readdirSync(directory)) {
    const candidate = join(directory, name);
    if (statSync(candidate).isDirectory()) visit(candidate);
    else if (sourceExtensions.has(extname(candidate)) && candidate !== catalogPath) files.push(candidate);
  }
};
sourceRoots.forEach(visit);

const catalogSource = readFileSync(catalogPath, 'utf8');
const catalogEntries = [...catalogSource.matchAll(/^\s*'([^']+)'\s*:\s*'((?:\\'|[^'])*)'/gm)];
const catalogKeys = new Set(catalogEntries.map((match) => match[1]));
const duplicateKeys = catalogEntries
  .map((match) => match[1])
  .filter((key, index, all) => all.indexOf(key) !== index);

const prefixPattern = localizedPrefixes.join('|');
const keyPattern = new RegExp(`["']((?:${prefixPattern})\\.[A-Za-z0-9_.-]+)["']`, 'g');
const referencedKeys = new Set(dynamicKeys);
for (const file of files) {
  const source = readFileSync(file, 'utf8');
  for (const match of source.matchAll(keyPattern)) referencedKeys.add(match[1]);
}

const missingKeys = [...referencedKeys].filter((key) => !catalogKeys.has(key)).sort();
const fallbackBypasses = files
  .filter((file) => file.includes(join('ui-shell', 'src', 'components')))
  .flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    return source.includes('.fallback') ? [file] : [];
  });

const failures = [];
if (missingKeys.length > 0) failures.push(`Missing Chinese translations:\n  ${missingKeys.join('\n  ')}`);
if (duplicateKeys.length > 0) failures.push(`Duplicate Chinese translation keys:\n  ${[...new Set(duplicateKeys)].join('\n  ')}`);
if (fallbackBypasses.length > 0) {
  failures.push(`Components bypassing t() through .fallback:\n  ${fallbackBypasses.map((file) => file.slice(projectRoot.length + 1)).join('\n  ')}`);
}

if (failures.length > 0) {
  console.error(failures.join('\n\n'));
  process.exit(1);
}

console.log(`Chinese localization gate passed: ${referencedKeys.size}/${referencedKeys.size} referenced keys translated.`);
