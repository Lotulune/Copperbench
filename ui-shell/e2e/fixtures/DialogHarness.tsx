import React, { StrictMode, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { useDialogA11y } from '../../src/hooks/useDialogA11y';

function Child({ close }: { close: () => void }) {
  const ref = useDialogA11y(true, close);
  return <div className="modal-overlay"><div className="modal-card" role="dialog" aria-modal="true" aria-label="Child fixture" ref={ref}>
    <input autoFocus aria-label="Child input" /><button onClick={close}>Close child</button>
  </div></div>;
}
function Harness({ mode }: { mode: string }) {
  const [open, setOpen] = useState(false);
  const [child, setChild] = useState(false);
  const [removed, setRemoved] = useState(false);
  const ref = useDialogA11y(open, mode === 'blocking' ? null : () => setOpen(false), {
    initialFocus: mode === 'container' ? () => document.getElementById('fixture-dialog') : undefined
  });
  return <main data-dialog-fallback tabIndex={-1} data-testid="dialog-fixture">
    {!removed && <button data-testid="fixture-launch" onClick={() => {
      setOpen(true); if (mode === 'simultaneous') setChild(true); if (mode === 'removed') setRemoved(true);
    }}>Open fixture</button>}
    <button data-testid="fixture-background">Background action</button>
    {open && <div className="modal-overlay"><div id="fixture-dialog" ref={ref} className="modal-card" role="dialog" aria-modal="true" aria-label="Parent fixture">
      <input aria-label="Fixture input" autoFocus={mode === 'autofocus'} />
      <button onClick={() => setChild(true)}>Open child</button><button onClick={() => setOpen(false)}>Close parent</button>
      <details><summary>Fixture details</summary><p>Native summary remains keyboard reachable.</p></details>
      {child && <Child close={() => setChild(false)} />}
    </div></div>}
  </main>;
}
export function mountDialogHarness(mode: string) {
  const root = document.createElement('div'); document.body.append(root);
  createRoot(root).render(<StrictMode><Harness mode={mode} /></StrictMode>);
}
