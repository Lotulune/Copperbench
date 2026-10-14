import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Select } from '../../src/components/Select';
import '../../src/styles/global.css';
import '../../src/styles/controls.css';

function Fixture() {
  const [value, setValue] = useState('fabric');
  const [disabled, setDisabled] = useState(false);
  const [saved, setSaved] = useState('');
  return <main style={{ padding: 24 }}>
    <form onSubmit={event => { event.preventDefault(); setSaved(String(new FormData(event.currentTarget).get('target'))); }}>
      <label htmlFor="target">Target</label>
      <fieldset disabled={disabled} style={{ border: 0 }}>
        <Select id="target" name="target" value={value} onChange={event => setValue(event.currentTarget.value)} style={{ width: 240 }}>
          <option value="fabric">Fabric</option>
          <option value="unavailable" disabled>Unavailable target</option>
          <option value="neoforge">NeoForge</option>
          <optgroup label="Unavailable group" disabled><option value="legacy">Legacy</option></optgroup>
          <optgroup label="Additional targets">
            {Array.from({ length: 24 }, (_, i) => <option key={i} value={`target-${i}`}>Target {String(i).padStart(2, '0')} — a long translated label that must remain readable</option>)}
          </optgroup>
        </Select>
      </fieldset>
      <button type="submit">Save target</button><output data-testid="saved">{saved}</output>
    </form>
    <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}><input type="checkbox" checked={disabled} onChange={event => setDisabled(event.target.checked)} />Disable choices</label>
    <section data-testid="checkbox-alignment" aria-label="Checkbox alignment" style={{ display: 'flex', gap: 32, width: 'max-content', marginTop: 24, padding: 24, background: 'var(--bg-panel)' }}>
      {['checked', 'unchecked', 'disabled', 'mixed'].map(kind => <label key={kind} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
        <input type="checkbox" data-testid={`alignment-${kind}`} defaultChecked={kind === 'checked' || kind === 'disabled'} disabled={kind === 'disabled'}
          ref={input => { if (input) input.indeterminate = kind === 'mixed'; }} />
        {kind}
      </label>)}
      <label style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
        <input type="radio" data-testid="alignment-radio" defaultChecked />radio
      </label>
    </section>
    <div style={{ position: 'fixed', bottom: 12, right: 12 }}>
      <Select aria-label="Edge choice" defaultValue="1" style={{ width: 120 }}>
        <option value="1">First option</option><option value="2">Second option with a longer label</option>
      </Select>
    </div>
  </main>;
}
createRoot(document.getElementById('fixture')!).render(<Fixture />);
