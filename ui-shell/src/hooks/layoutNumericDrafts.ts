import { numericDraftValue } from './editorFieldChanges';

/** Only fields rendered by the existing layout editors as numeric controls are
 * converted. Unknown component data is carried through unchanged. */
export function layoutNumericDrafts(values: Record<string, unknown>, baseline: Record<string, unknown>) {
  const invalid: string[] = [];
  const number = (value: unknown, path: string) => {
    const parsed = numericDraftValue(value);
    if (parsed === undefined) { invalid.push(path); return value; }
    return parsed;
  };
  const result = { ...values };
  const numericPaths = ['/width', '/height', '/inventoryOffsetX', '/inventoryOffsetY',
    '/gridSettings/sx', '/gridSettings/sy', '/gridSettings/ox', '/gridSettings/oy'];
  for (const path of numericPaths) if (Object.prototype.hasOwnProperty.call(result, path)) result[path] = number(result[path], path);
  if (Array.isArray(values['/components'])) {
    const originals = Array.isArray(baseline['/components']) ? baseline['/components'] as Array<{ data?: Record<string, unknown> }> : [];
    result['/components'] = (values['/components'] as Array<{ type: string; data: Record<string, unknown> }>).map((component, index) => {
      if (!component?.data || typeof component.data !== 'object') return component;
      const data = { ...component.data };
      for (const [key, value] of Object.entries(data)) {
        if (typeof originals[index]?.data?.[key] === 'number'
          || ['x', 'y', 'width', 'height', 'id', 'spritesCount', 'scale', 'rotationX'].includes(key)) {
          data[key] = number(value, `/components/${index}/data/${key}`);
        }
      }
      const spriteIndex = data.spriteIndex;
      if (spriteIndex && typeof spriteIndex === 'object' && !Array.isArray(spriteIndex)
        && Object.prototype.hasOwnProperty.call(spriteIndex, 'fixedValue')) {
        const source = spriteIndex as Record<string, unknown>;
        data.spriteIndex = { ...source, fixedValue: number(source.fixedValue, `/components/${index}/data/spriteIndex/fixedValue`) };
      }
      return { ...component, data };
    });
  }
  return { values: result, invalid };
}
