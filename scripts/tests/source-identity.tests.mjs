import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdtempSync, mkdirSync, writeFileSync, unlinkSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { captureSourceIdentity, isSourcePath, SOURCE_PATH_FILTER_VERSION } from '../evidence/source-identity.mjs';

function repository(t) {
  const root = mkdtempSync(join(tmpdir(), 'copperbench-source-identity-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const git = (...args) => execFileSync('git', ['-c', 'core.autocrlf=false', '-c', 'commit.gpgsign=false',
    '-c', 'user.name=Source identity fixture', '-c', 'user.email=identity@example.invalid', ...args],
    { cwd: root, env: { ...process.env, GIT_CONFIG_NOSYSTEM: '1', GIT_CONFIG_GLOBAL: process.platform === 'win32' ? 'NUL' : '/dev/null', GIT_TERMINAL_PROMPT: '0' } });
  const write = (path, contents) => { const absolute = join(root, path); mkdirSync(dirname(absolute), { recursive: true }); writeFileSync(absolute, contents); };
  git('init', '--quiet');
  write('src/Main.java', 'class Main {}\n');
  write('docs/build/setup.md', 'Build instructions.\n');
  write('scripts/evidence/source-identity.mjs', 'export const fixtureIdentityVersion = 1;\n');
  write('src/main/java/example/evidence/Record.java', 'class Record {}\n');
  write('src/main/java/example/output/Result.java', 'class Result {}\n');
  write('src/main/java/example/logs/LogEntry.java', 'class LogEntry {}\n');
  write('.gitignore', 'ignored.secret\n');
  write('build/compiled.class', 'generated artifact');
  write('output/run.json', '{}');
  write('evidence/old.json', '{}');
  write('ui-shell/node_modules/dependency/index.js', 'dependency bytes');
  git('add', '--all'); git('commit', '--quiet', '-m', 'Fixture input');
  return { root, git, write };
}

test('identity binds actual tracked bytes, added source and dirty delta while excluding dependency/report output', t => {
  const { root, write } = repository(t);
  const clean = captureSourceIdentity(root);
  assert.equal(clean.dirty, false);
  assert.ok(clean.files.some(file => file.path === 'docs/build/setup.md'));
  assert.ok(clean.files.every(file => !file.path.startsWith('build/') && !file.path.includes('node_modules') && !file.path.startsWith('output/') && !file.path.startsWith('evidence/')));
  write('src/Main.java', 'class Main { int changed; }\n');
  write('scripts/new tool.mjs', 'export const newlyAdded = true;\n');
  write('ignored.secret', 'ignored fixture data');
  const changed = captureSourceIdentity(root);
  assert.equal(changed.commit, clean.commit);
  assert.equal(changed.dirty, true);
  assert.notEqual(changed.sourceSha256, clean.sourceSha256);
  assert.notEqual(changed.dirtyDeltaSha256, clean.dirtyDeltaSha256);
  assert.deepEqual(changed.dirtyFiles.map(file => file.path), ['scripts/new tool.mjs', 'src/Main.java']);
  assert.ok(changed.files.every(file => file.path !== 'ignored.secret' && !file.path.startsWith(root)));
  write('output/run.json', '{"later":true}'); write('build/compiled.class', 'other artifact');
  write('ui-shell/node_modules/dependency/index.js', 'other dependency');
  const afterReports = captureSourceIdentity(root);
  assert.equal(afterReports.sourceSha256, changed.sourceSha256);
  assert.equal(afterReports.dirtyDeltaSha256, changed.dirtyDeltaSha256);
});

test('deletions and staged/unstaged changes remain identifiable', t => {
  const { root, git, write } = repository(t);
  write('src/Main.java', 'class Main { int edited; }\n');
  const unstaged = captureSourceIdentity(root);
  git('add', 'src/Main.java');
  const staged = captureSourceIdentity(root);
  assert.equal(staged.sourceSha256, unstaged.sourceSha256);
  assert.notEqual(staged.dirtyDeltaSha256, unstaged.dirtyDeltaSha256);
  unlinkSync(join(root, 'src/Main.java'));
  const removed = captureSourceIdentity(root);
  const deleted = removed.dirtyFiles.find(file => file.path === 'src/Main.java');
  assert.equal(deleted.kind, 'missing'); assert.equal(deleted.sha256, null);
  assert.notEqual(removed.sourceSha256, staged.sourceSha256);
});

test('source scope distinguishes build documentation from generated outputs', () => {
  for (const path of ['docs/build/setup.md', 'scripts/evidence/source-identity.mjs',
    'src/main/java/example/evidence/Record.java', 'src/main/java/example/output/Result.java',
    'src/main/java/example/logs/LogEntry.java', 'src/main/java/example/coverage/Model.java'])
    assert.equal(isSourcePath(path), true, path);
  for (const path of ['output/run.json', 'ui-shell/dist/app.js', 'src/build/generated.java', '.git/index',
    'jdk/bin/java.exe', 'evidence/stage.json', 'logs/run.json', 'coverage/report.json',
    'ui-shell/playwright-report/index.html', 'ui-shell/test-results/run.json', 'ui-shell/blob-report/report.zip',
    'ui-core/coverage/report.json', 'nested/node_modules/dependency/index.js', 'nested/.gradle/cache.bin',
    'nested/__pycache__/fixture.pyc'])
    assert.equal(isSourcePath(path), false);
});

test('evidence tooling and same-named source folders affect identity while generated evidence remains excluded', t => {
  const { root, write } = repository(t);
  const before = captureSourceIdentity(root);
  assert.equal(before.pathFilterVersion, SOURCE_PATH_FILTER_VERSION);
  for (const path of ['scripts/evidence/source-identity.mjs', 'src/main/java/example/evidence/Record.java',
    'src/main/java/example/output/Result.java', 'src/main/java/example/logs/LogEntry.java'])
    assert.ok(before.files.some(file => file.path === path), path);
  write('scripts/evidence/source-identity.mjs', 'export const fixtureIdentityVersion = 2;\n');
  const changedTool = captureSourceIdentity(root);
  assert.equal(changedTool.commit, before.commit);
  assert.notEqual(changedTool.sourceSha256, before.sourceSha256);
  assert.notEqual(changedTool.dirtyDeltaSha256, before.dirtyDeltaSha256);
  assert.deepEqual(changedTool.dirtyFiles.map(file => file.path), ['scripts/evidence/source-identity.mjs']);
  write('src/main/java/example/evidence/Record.java', 'class Record { int changed; }\n');
  const changedSource = captureSourceIdentity(root);
  assert.notEqual(changedSource.sourceSha256, changedTool.sourceSha256);
  assert.notEqual(changedSource.dirtyDeltaSha256, changedTool.dirtyDeltaSha256);
  write('evidence/old.json', '{"later":true}');
  write('output/run.json', '{"later":true}');
  const afterGeneratedReports = captureSourceIdentity(root);
  assert.equal(afterGeneratedReports.sourceSha256, changedSource.sourceSha256);
  assert.equal(afterGeneratedReports.dirtyDeltaSha256, changedSource.dirtyDeltaSha256);
});

test('actual module imports and artifact reads work with no global process in a fresh runtime', t => {
  const root = mkdtempSync(join(tmpdir(), 'copperbench-processless-import-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const artifact = join(root, 'fixture.jar');
  const bytes = 'fixture artifact bytes';
  writeFileSync(artifact, bytes);
  const sourceModule = new URL('../evidence/source-identity.mjs', import.meta.url).href;
  const nativeModule = new URL('../performance/native-observation.mjs', import.meta.url).href;
  const script = `
    delete globalThis.process;
    if (typeof process !== 'undefined') throw new Error('This regression requires the real global to be absent');
    const source = await import(${JSON.stringify(sourceModule)});
    const native = await import(${JSON.stringify(nativeModule)});
    console.log(JSON.stringify({
      globalProcessAbsent: !Object.hasOwn(globalThis, 'process'),
      artifact: source.fileIdentity(${JSON.stringify(artifact)}),
      captureExport: typeof source.captureSourceIdentity,
      passiveRecorderExport: typeof native.NativeObservationRecorder
    }));
  `;
  const result = JSON.parse(execFileSync(process.execPath, ['--input-type=module', '--eval', script],
    { encoding: 'utf8', timeout: 30000 }));
  assert.equal(result.globalProcessAbsent, true);
  assert.deepEqual(result.artifact, { name: 'fixture.jar', size: Buffer.byteLength(bytes),
    sha256: createHash('sha256').update(bytes).digest('hex') });
  assert.equal(result.captureExport, 'function');
  assert.equal(result.passiveRecorderExport, 'function');
});

test('normal Node CLI still captures the actual repository identity', t => {
  const { root } = repository(t);
  const expected = captureSourceIdentity(root);
  const modulePath = fileURLToPath(new URL('../evidence/source-identity.mjs', import.meta.url));
  const actual = JSON.parse(execFileSync(process.execPath, [modulePath, root], { encoding: 'utf8', timeout: 60000 }));
  assert.equal(actual.commit, expected.commit);
  assert.equal(actual.sourceSha256, expected.sourceSha256);
  assert.equal(actual.dirtyDeltaSha256, expected.dirtyDeltaSha256);
  assert.deepEqual(actual.files, expected.files);
});
