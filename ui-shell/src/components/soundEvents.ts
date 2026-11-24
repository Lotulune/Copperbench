/** The game's sound sources, which MCreator offers as sound categories. */
export const SOUND_CATEGORIES = ['neutral', 'block', 'ambient', 'hostile', 'player', 'master', 'music', 'record', 'weather', 'voice'] as const;

/**
 * How a sound event names this file: its path under `sounds/` without `.ogg`, as MCreator writes sounds.json.
 * `null` when the file is not an `.ogg` under a sounds folder, which sound events cannot play.
 */
export function soundFileValue(relativePath: string): string | null {
  const path = relativePath.replace(/\\/g, '/');
  const start = path.indexOf('/sounds/');
  if (start < 0 || !/\.ogg$/i.test(path)) return null;
  return path.slice(start + '/sounds/'.length, -'.ogg'.length) || null;
}

/** A default event name: the file path with dots between folders, in the form vanilla sounds use (block.lamp.click). */
export function defaultSoundEventName(fileValue: string): string {
  return fileValue.toLowerCase().replace(/[^a-z0-9_./-]/g, '_').replace(/\//g, '.');
}
