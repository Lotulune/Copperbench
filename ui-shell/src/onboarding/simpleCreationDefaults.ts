/** Generate a legal default namespace without transliterating or changing a player's display name. */
export function creationModId(name: string, fallback: string): string {
  const slug = name.toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '');
  const value = /^[a-z]/.test(slug) ? slug : slug ? `work_${slug}` : fallback;
  return value.slice(0, 32).padEnd(2, '_');
}
