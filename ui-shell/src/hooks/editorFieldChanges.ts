import type { EditorField, FieldChange, ModElementEditorProjection } from '../types/contract';

/** Decimal drafts are text until the submission boundary. Empty and partial
 * values must never acquire the meaning of zero through Number/parseFloat. */
export function numericDraftValue(value: unknown): number | undefined {
  if (typeof value === 'number') return Number.isFinite(value) ? value : undefined;
  if (typeof value !== 'string' || !/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(value)) return undefined;
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}

export class InvalidEditorField extends Error {
  constructor(public readonly path: string, public readonly control: 'number' | 'json') {
    super(`Invalid ${control} input at ${path}`);
  }
}

export function editorFieldValue(field: EditorField, value: unknown, path = field.path): unknown {
  return editorInputValue(field, value, path, false);
}

function editorInputValue(field: EditorField, value: unknown, path: string, includeReadOnly: boolean): unknown {
  if (field.control === 'number') {
    // Preserve an unedited optional null baseline. An edited empty string is invalid.
    if ((value === null || value === undefined) && value === field.value && !field.required) return value;
    const number = numericDraftValue(value);
    if (number === undefined) throw new InvalidEditorField(path, 'number');
    return number;
  }
  if (field.control === 'json' && typeof value === 'string') {
    try { return JSON.parse(value); } catch { throw new InvalidEditorField(path, 'json'); }
  }
  if (field.control === 'structured_list' && Array.isArray(value) && field.itemFields?.length) {
    return value.map((item, index) => {
      if (!item || typeof item !== 'object' || Array.isArray(item)) return item;
      const result = { ...item } as Record<string, unknown>;
      for (const child of field.itemFields!) {
        const name = child.path.split('/').filter(Boolean).at(-1)!;
        if ((includeReadOnly || !child.readOnly) && Object.prototype.hasOwnProperty.call(result, name))
          result[name] = editorInputValue(child, result[name], `${path}/${index}/${name}`, includeReadOnly);
      }
      return result;
    });
  }
  return value;
}

export function collectEditorFieldChanges(editor: ModElementEditorProjection | null, values: Record<string, unknown>) {
  const changes: FieldChange[] = [];
  const invalid: InvalidEditorField[] = [];
  for (const field of editor?.sections.flatMap(section => section.fields) ?? []) {
    if (field.readOnly) continue;
    try {
      const next = editorFieldValue(field, values[field.path]);
      if (JSON.stringify(next) !== JSON.stringify(field.value)) changes.push({ path: field.path, value: next });
    } catch (error) {
      if (!(error instanceof InvalidEditorField)) throw error;
      invalid.push(error);
    }
  }
  const hasInputDraft = (editor?.sections.flatMap(section => section.fields) ?? []).some(field => !field.readOnly
    && numericInputDraft(field, values[field.path], field.value));
  return { changes, invalid, hasInputDraft, invalidJson: invalid.some(issue => issue.control === 'json'), invalidNumber: invalid.some(issue => issue.control === 'number') };
}

function numericInputDraft(field: EditorField, value: unknown, baseline: unknown, includeReadOnly = false): boolean {
  if (field.control === 'number') return typeof value === 'string' && value !== String(baseline ?? '');
  if (field.control !== 'structured_list' || !Array.isArray(value)) return false;
  const originals = Array.isArray(baseline) ? baseline : [];
  return value.some((item, index) => item && typeof item === 'object' && field.itemFields?.some(child => {
    const name = child.path.split('/').filter(Boolean).at(-1)!;
    return (includeReadOnly || !child.readOnly) && numericInputDraft(child, item[name], originals[index]?.[name], includeReadOnly);
  }));
}

/** Retaining input does not grant permission to submit it. A confirmation can
 * make fields read only or remove them while their earlier draft still matters. */
export function hasEditorInputDraft(editor: ModElementEditorProjection | null, values: Record<string, unknown>): boolean {
  if (!editor) return false;
  const fields = editor.sections.flatMap(section => section.fields);
  if (Object.keys(values).some(path => !fields.some(field => field.path === path))) return true;
  return fields.some(field => {
    if (!Object.prototype.hasOwnProperty.call(values, field.path)) return false;
    try {
      return numericInputDraft(field, values[field.path], field.value, true)
        || JSON.stringify(editorInputValue(field, values[field.path], field.path, true)) !== JSON.stringify(field.value);
    } catch (error) {
      if (!(error instanceof InvalidEditorField)) throw error;
      return true;
    }
  });
}

/** Normalize the captured draft only when the user submits it. JSON and other
 * free-form input retain their spelling; only numeric controls are converted. */
export function normalizeEditorNumericDrafts(editor: ModElementEditorProjection, values: Record<string, unknown>) {
  const normalize = (field: EditorField, value: unknown): unknown => {
    if (field.readOnly) return value;
    if (field.control === 'number') return editorFieldValue(field, value);
    if (field.control !== 'structured_list' || !Array.isArray(value)) return value;
    return value.map(item => {
      if (!item || typeof item !== 'object' || Array.isArray(item)) return item;
      const result = { ...item } as Record<string, unknown>;
      for (const child of field.itemFields ?? []) {
        const name = child.path.split('/').filter(Boolean).at(-1)!;
        if (Object.prototype.hasOwnProperty.call(result, name)) result[name] = normalize(child, result[name]);
      }
      return result;
    });
  };
  return Object.fromEntries(Object.entries(values).map(([path, value]) => {
    const field = editor.sections.flatMap(section => section.fields).find(field => field.path === path);
    return [path, field ? normalize(field, value) : value];
  }));
}
