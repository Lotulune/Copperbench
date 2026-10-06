import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';

const verifier = resolve(import.meta.dirname, '../verify-markdown-links.mjs');

function repository(t) {
  const prefix = resolve(tmpdir(), 'copperbench-markdown-links-');
  const root = mkdtempSync(prefix);
  t.after(() => {
    assert.ok(root.startsWith(prefix) && root.length > prefix.length);
    rmSync(root, { recursive: true, force: true });
  });
  const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8' });
  const write = (path, content) => {
    const target = resolve(root, path);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, content, 'utf8');
  };
  git('init', '--quiet');
  write('.gitignore', 'output/\n.venv/\n');
  write('scripts/verify-markdown-links.mjs', '');
  copyFileSync(verifier, resolve(root, 'scripts/verify-markdown-links.mjs'));
  return {
    root, git, write,
    verify: () => spawnSync(process.execPath, ['scripts/verify-markdown-links.mjs'], {
      cwd: root,
      encoding: 'utf8'
    })
  };
}

test('ignored generated documents do not affect repository links', (t) => {
  const fixture = repository(t);
  fixture.write('README.md', '[Guide](docs/guide%20one.md)\n[Docs](docs/)\n');
  fixture.write('docs/guide one.md', '[Home](../README.md)\n');
  fixture.write('deleted.md', '[Gone](missing.md)\n');
  fixture.git('add', '.');
  rmSync(resolve(fixture.root, 'deleted.md'));
  fixture.write('output/draft.md', '[Missing output](missing.md)\n');
  fixture.write('.venv/NOTICE.md', '[Missing dependency](missing.md)\n');
  const result = fixture.verify();
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /All local Markdown links resolve/);
});

test('new nonignored documents are checked before they are staged', (t) => {
  const fixture = repository(t);
  fixture.write('docs/new.md', '[Missing](missing.md)\n');
  const result = fixture.verify();
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stderr, /new\.md: missing local target missing\.md/);
});

test('tracked documents remain checked inside ignored directories', (t) => {
  const fixture = repository(t);
  fixture.write('output/record.md', '[Missing](missing.md)\n');
  fixture.git('add', '--force', 'output/record.md');
  const result = fixture.verify();
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stderr, /record\.md: missing local target missing\.md/);
});

test('existing ignored files cannot satisfy a repository link', (t) => {
  const fixture = repository(t);
  fixture.write('README.md', '[Local output](output/draft.md)\n');
  fixture.write('output/draft.md', 'Local only\n');
  const result = fixture.verify();
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stderr, /README\.md: missing local target output\/draft\.md/);
});
