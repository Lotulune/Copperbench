/**
 * Element types the create dialog lists, in catalog order. Core reports the types the workspace generator can create,
 * so a data pack is not offered blocks or items. Before Core reports them, the whole catalog stays.
 */
export function offeredElementTypes<T extends string>(catalog: readonly T[], available?: readonly string[]): T[] {
  return available?.length ? catalog.filter(type => available.includes(type)) : [...catalog];
}
