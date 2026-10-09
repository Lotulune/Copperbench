import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { build } from 'vite';

const root = fileURLToPath(new URL('../', import.meta.url));
const lock = JSON.parse(await readFile(path.join(root, 'package-lock.json'), 'utf8'));
// The evidence stays outside dist; it describes the shipped chunks rather than
// inferring browser reachability from npm's prod/dev dependency labels.
await build({
  root,
  plugins: [{
    name: 'copperbench-shipped-module-evidence',
    async writeBundle(_options, bundle) {
      const modules = new Set();
      const chunks = [];
      for (const output of Object.values(bundle)) {
        if (output.type !== 'chunk') continue;
        chunks.push({ file: output.fileName,
          sha256: createHash('sha256').update(output.code).digest('hex') });
        for (const [id, module] of Object.entries(output.modules)) {
          if (module.renderedLength > 0) modules.add(path.relative(root, id).replaceAll('\\', '/'));
        }
      }
      const packages = new Set();
      for (const id of modules) {
        for (const match of id.matchAll(/(?:^|\/)node_modules\/((?:@[^/]+\/)?[^/]+)/g)) packages.add(match[1]);
      }
      const report = { schemaVersion: 1, scope: 'production_browser_chunks',
        lockSha256: createHash('sha256').update(await readFile(path.join(root, 'package-lock.json'))).digest('hex'),
        packages: [...packages].sort().map(name => ({ name, version: lock.packages?.['node_modules/'+name]?.version ?? null })),
        chunks, modules: [...modules].sort() };
      const target = path.join(root, '../build/reports/ui-dependencies.json');
      await mkdir(path.dirname(target), { recursive: true });
      await writeFile(target, JSON.stringify(report, null, 2)+'\n', 'utf8');
    }
  }]
});
