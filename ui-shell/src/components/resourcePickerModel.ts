import type { AssetProjectionAsset } from '../types/contract';

/** One choice in a resource field picker: the value written to the field and the asset that shows it. */
export interface ResourceChoice {
  value: string;
  label: string;
  relativePath: string;
  asset?: Pick<AssetProjectionAsset, 'id' | 'sha256' | 'mediaType' | 'category'>;
}

/**
 * The value a texture field stores for an asset: its path under `textures/<type>/` without `.png`, as MCreator
 * names textures, so files in subfolders keep their folder. `null` when the asset is not a texture of that type.
 */
export function textureFieldValue(relativePath: string, resourceType?: string): string | null {
  const path = relativePath.replace(/\\/g, '/');
  const lower = path.toLowerCase();
  if (!lower.endsWith('.png')) return null;
  const marker = resourceType ? `/textures/${resourceType.toLowerCase()}/` : '/textures/';
  const start = lower.indexOf(marker);
  if (start < 0) return null;
  const rest = path.slice(start + marker.length, -4);
  // Without a resource type the first folder after textures/ is the texture type, not part of the name.
  return resourceType ? rest || null : rest.split('/').slice(1).join('/') || null;
}

/** Older workspaces stored texture names with `.png`; MCreator still resolves them, so they match the same choice. */
export function sameResourceValue(left: string, right: string): boolean {
  const normalize = (value: string) => value.trim().replace(/\\/g, '/').replace(/\.png$/i, '').toLowerCase();
  return normalize(left) === normalize(right);
}

export function findResourceChoice(choices: readonly ResourceChoice[], value: string): ResourceChoice | undefined {
  return value.trim() ? choices.find(choice => sameResourceValue(choice.value, value)) : undefined;
}

/** Case-insensitive match on the stored value and the workspace path, in catalog order. */
export function filterResourceChoices(choices: readonly ResourceChoice[], query: string): ResourceChoice[] {
  const terms = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  if (!terms.length) return [...choices];
  return choices.filter(choice => {
    const haystack = `${choice.value} ${choice.relativePath}`.toLowerCase();
    return terms.every(term => haystack.includes(term));
  });
}
