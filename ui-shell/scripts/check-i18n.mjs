import ts from 'typescript';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { collectLocalizationKeys } from './localization-keys.mjs';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDirectory, '../..');
const catalogPath = join(projectRoot, 'ui-shell/src/i18n/zh.ts');
const sourceRoots = [
  join(projectRoot, 'src/main/java/dev/copperbench'),
  join(projectRoot, 'ui-shell/src'),
  join(projectRoot, 'ui-core/fixtures/v1.0')
];
const sourceExtensions = new Set(['.java', '.json', '.ts', '.tsx']);
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

const referencedKeys = new Set(dynamicKeys);
for (const file of files) {
  const source = readFileSync(file, 'utf8');
  for (const key of collectLocalizationKeys(source, extname(file))) referencedKeys.add(key);
  if (file.endsWith('WorkspaceApplicationService.java')) {
    // These UI keys are assembled at runtime, so literal-key scanning misses them.
    const defaults = source.split('private JsonObject defaultElementValues(')[1]?.split('private Diagnostic validateElementValues(')[0] ?? '';
    for (const match of defaults.matchAll(/values\.(?:addProperty|add)\("([^"]+)"/g)) referencedKeys.add(`field.${match[1]}`);
    for (const match of source.matchAll(/procedureNode\(catalog, "([^"]+)"/g)) referencedKeys.add(`procedure.node.${match[1]}`);
  }
  if (file.endsWith('ProcedureIrCodec.java')) {
    for (const match of source.matchAll(/new ValidationIssue\("([^"]+)"/g)) referencedKeys.add(`diagnostic.${match[1].toLowerCase()}`);
  }
}

const missingKeys = [...referencedKeys].filter((key) => !catalogKeys.has(key)).sort();
const fallbackBypasses = files
  .filter((file) => file.includes(join('ui-shell', 'src', 'components')))
  .flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    return source.includes('.fallback') ? [file] : [];
  });

// Inspect rendered JSX and accessibility labels, not code, IDs or user content.
const technicalLiterals = new Set([
  'English', 'Copperbench', 'Minecraft', 'MCP:', 'SHA-256:', '&rarr;', '.mcreator', '.mcfunction', '.json',
  'data/', '/functions/', '/tags/functions/', 'minecraft:diamond', 'mod', 'workspace_imported_copy', 'pack_v1'
]);
const untranslatedUi = [];
for (const file of files.filter(file => file.endsWith('.tsx') && file.includes(join('src', 'components')))) {
  const source = readFileSync(file, 'utf8');
  const ast = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const visitNode = node => {
    let text;
    if (ts.isJsxText(node)) text = node.text.trim();
    if (ts.isJsxAttribute(node) && ['title', 'aria-label', 'placeholder'].includes(node.name.text)
        && node.initializer && ts.isStringLiteral(node.initializer)) text = node.initializer.text;
    if (text && /[A-Za-z]{3}/.test(text) && !/[\u3400-\u9fff]/.test(text) && !technicalLiterals.has(text)) {
      untranslatedUi.push(`${file.slice(projectRoot.length + 1)}:${ast.getLineAndCharacterOfPosition(node.pos).line + 1}: ${text}`);
    }
    ts.forEachChild(node, visitNode);
  };
  visitNode(ast);
  if (/\.(?:kind|state|ownership)\.toUpperCase\(\)/.test(source)) {
    untranslatedUi.push(`${file.slice(projectRoot.length + 1)}: untranslated locale or wire-value rendering`);
  }
}

const failures = [];
// Every authored UI literal must have an English counterpart with matching arguments.
const englishSource = readFileSync(join(projectRoot, 'ui-shell/src/i18n/enUi.ts'), 'utf8');
const englishAst = ts.createSourceFile('enUi.ts', englishSource, ts.ScriptTarget.Latest, true);
const englishMessages = new Map();
const readEnglish = node => {
  if (ts.isPropertyAssignment(node) && ts.isStringLiteral(node.name) && ts.isStringLiteral(node.initializer)) {
    if (englishMessages.has(node.name.text)) failures.push(`Duplicate English message: ${node.name.text}`);
    englishMessages.set(node.name.text, node.initializer.text);
  }
  ts.forEachChild(node, readEnglish);
};
readEnglish(englishAst);
for (const file of files.filter(file => /\.tsx?$/.test(file) && file.includes(join('ui-shell', 'src')))) {
  const ast = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true,
    file.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
  const visit = node => {
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'tr'
        && node.arguments[0] && ts.isStringLiteral(node.arguments[0]) && !englishMessages.has(node.arguments[0].text)) {
      failures.push(`Missing English translation in ${file}: ${node.arguments[0].text}`);
    }
    ts.forEachChild(node, visit);
  };
  visit(ast);
}
const placeholders = text => (text.match(/\{\d+\}/g) ?? []).sort().join(',');
for (const [source, translated] of englishMessages) {
  if (placeholders(source) !== placeholders(translated)) failures.push(`English placeholder mismatch: ${source}`);
}
const blocklySource = readFileSync(join(projectRoot, 'ui-shell/node_modules/blockly/msg/zh-hans.js'), 'utf8');
const blocklyOverrides = readFileSync(join(projectRoot, 'ui-shell/src/i18n/blocklyZh.ts'), 'utf8');
for (const line of blocklySource.split('\n').filter(line => line.includes('// untranslated'))) {
  const entry = line.match(/Blockly.Msg\["([^"]+)"\] = "(.*?)";/);
  if (entry && !/_(KEY|HELPURL|SYMBOL)$/.test(entry[1]) && /[A-Za-z]/.test(entry[2])
      && !blocklyOverrides.includes(`${entry[1]}:`)) failures.push(`Missing Blockly translation: ${entry[1]}`);
}

if (untranslatedUi.length) failures.push(`Untranslated interface text:\n  ${untranslatedUi.join("\n  ")}`);
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
console.log(`English localization gate passed: ${englishMessages.size} messages; source coverage and placeholders verified.`);
