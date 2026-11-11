import { readFileSync } from 'node:fs';

const read = (directory: string, file: string) =>
  JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/${directory}/${file}`, import.meta.url), 'utf8'));

/**
 * Core projection for one loader. The Fabric capture is stored in full; `neoforge-overrides.json`
 * lists the values the NeoForge capture reported differently, keyed by fixture name and dotted path.
 */
export function previewProjection(directory: string, loader: string, name: string) {
  const projection = read(directory, `fabric-${name}.json`);
  if (loader !== 'neoforge') return projection;
  for (const [path, value] of Object.entries(read(directory, 'neoforge-overrides.json')[name] ?? {})) {
    const keys = path.split('.');
    const last = keys.pop()!;
    keys.reduce((node, key) => node[key], projection)[last] = value;
  }
  return projection;
}
