import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { collectLocalizationKeys } from '../scripts/localization-keys.mjs';

test('Java path arguments are not translation keys', () => {
  const source = `
    directory.resolve("task.json");
    directory.resolveSibling("editor.json");
    Path.of("field.json");
    java.nio.file.Paths.get("status.json");
    taskDirectory(id).resolve /* path */ (
      "diagnostic.json");
    localized("task.started", "Task started");
  `;
  assert.deepEqual([...collectLocalizationKeys(source, '.java')], ['task.started']);
});

test('a path filename still needs translation when also used as localized text', () => {
  assert.deepEqual([...collectLocalizationKeys(
    'root.resolve("task.json"); localized("task.json", "JSON task");', '.java')], ['task.json']);
});

test('ordinary strings, JSON keys and TypeScript translation calls remain checked', () => {
  for (const [source, extension] of [
    ['String key = "task.missing";', '.java'],
    ['{"key": "task.missing", "args": {}}', '.json'],
    ['t("task.missing")', '.tsx'],
    ['t("task.json")', '.ts']
  ]) {
    assert.equal(collectLocalizationKeys(source, extension).size, 1);
  }
});

test('comments and string contents cannot turn a translation call into a path call', () => {
  const source = `
    String example = "root.resolve(";
    // root.resolve(
    localized("task.missing", "Missing");
    String another = /* root.resolve( */ "field.missing";
  `;
  assert.deepEqual([...collectLocalizationKeys(source, '.java')], ['task.missing', 'field.missing']);
});

test('the actual Blockbench modeling manifest does not require a task.json translation', () => {
  const source = readFileSync(new URL('../../src/main/java/dev/copperbench/assets/BlockbenchModelingService.java',
    import.meta.url), 'utf8');
  assert.match(source, /resolve\("task\.json"\)/);
  assert.equal(collectLocalizationKeys(source, '.java').has('task.json'), false);
});
