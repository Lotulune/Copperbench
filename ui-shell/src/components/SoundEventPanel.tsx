import React, { useEffect, useState } from 'react';
import { Music } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { t, uiText, useUiLocale } from '../i18n';
import type { RegistryEntry } from '../types/contract';
import { defaultSoundEventName, SOUND_CATEGORIES, soundFileValue } from './soundEvents';

/**
 * The sound events that play a sound file, and a short form to register one. Elements and code reference sound
 * events, not files, so an unregistered .ogg is not playable in game.
 */
export const SoundEventPanel: React.FC<{ relativePath: string }> = ({ relativePath }) => {
  useUiLocale();
  const { state, listWorkspaceRegistries, createRegistryEntry } = useWorkbench();
  const file = soundFileValue(relativePath);
  const [events, setEvents] = useState<RegistryEntry[] | null>(null);
  const [name, setName] = useState(file ? defaultSoundEventName(file) : '');
  const [category, setCategory] = useState<string>('neutral');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const revision = state.workbench?.workspace.revision;

  useEffect(() => {
    let active = true;
    if (!file) return;
    void listWorkspaceRegistries().then(projection => {
      if (!active) return;
      const sounds = projection?.registries?.sounds ?? [];
      setEvents(sounds.filter(entry => entry.files?.includes(file)));
    }).catch(() => { if (active) setEvents([]); });
    return () => { active = false; };
  }, [file, revision, listWorkspaceRegistries]);

  if (!file) return <p className="asset-sound-events-note" data-testid="asset-sound-events-unsupported">
    {uiText('只有 sounds/ 下的 .ogg 文件可以注册为声音事件。', 'Only .ogg files under sounds/ can be registered as sound events.')}</p>;

  const register = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true); setError(null);
    try {
      const result = await createRegistryEntry('sounds', { name: name.trim(), files: [file], category });
      if (result.status !== 'committed') setError(t(result.diagnostics[0]?.message, result.diagnostics[0]?.code ?? ''));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : String(failure));
    } finally { setBusy(false); }
  };

  return (
    <section className="asset-sound-events" aria-label={uiText('声音事件', 'Sound events')} data-testid="asset-sound-events">
      <h3><Music size={14} aria-hidden="true" />{uiText('声音事件', 'Sound events')}</h3>
      {events === null ? <p role="status">{uiText('正在读取声音事件…', 'Reading sound events…')}</p>
        : events.length > 0 ? <ul data-testid="asset-sound-event-list">{events.map(entry => (
          <li key={entry.id}><code>{entry.name}</code><small>{entry.category}{entry.subtitle ? ` · ${entry.subtitle}` : ''}</small></li>
        ))}</ul>
          : <form className="asset-sound-event-form" onSubmit={event => void register(event)} data-testid="asset-sound-event-form">
            <p>{uiText('这个文件还没有注册为声音事件，元素和代码只能播放已注册的事件。',
              'This file is not a sound event yet; elements and code can only play registered events.')}</p>
            <label><span>{uiText('事件名', 'Event name')}</span>
              <input value={name} onChange={event => setName(event.target.value)} disabled={busy} data-testid="asset-sound-event-name" /></label>
            <label><span>{uiText('声音类别', 'Sound category')}</span>
              <select value={category} onChange={event => setCategory(event.target.value)} disabled={busy} data-testid="asset-sound-event-category">
                {SOUND_CATEGORIES.map(value => <option key={value} value={value}>{value}</option>)}
              </select></label>
            <button type="submit" className="btn-secondary" disabled={busy || !name.trim()} data-testid="asset-sound-event-register">
              {busy ? uiText('正在注册…', 'Registering…') : uiText('注册为声音事件', 'Register sound event')}</button>
            {error && <p role="alert" className="asset-sound-events-error">{error}</p>}
          </form>}
    </section>
  );
};
