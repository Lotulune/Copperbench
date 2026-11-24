import type { DataPackArchiveInspection } from '../types/contract';

/** The ZIP's file name without `.zip`, as the default name of the pack it becomes. */
export function archiveDisplayName(path: string): string {
  const name = path.split(/[\\/]/).pop() ?? '';
  return name.replace(/\.zip$/i, '').trim().slice(0, 64);
}

/**
 * The generator to select for an inspected pack: the newest available one whose pack format the pack declares,
 * or `undefined` when none of the available generators matches and the user has to choose.
 */
export function importGenerator(inspection: Pick<DataPackArchiveInspection, 'supportedGeneratorIds'>,
  available: readonly string[]): string | undefined {
  return inspection.supportedGeneratorIds.find(generatorId => available.includes(generatorId));
}
