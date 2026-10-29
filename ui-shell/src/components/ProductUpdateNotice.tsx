import { useSyncExternalStore, useState } from 'react';
import { ArrowDownToLine } from 'lucide-react';
import { uiText } from '../i18n';

function subscribe(listener: () => void) {
  window.addEventListener('copperbench:update-available', listener);
  return () => window.removeEventListener('copperbench:update-available', listener);
}
function snapshot() { return window.__COPPERBENCH_WINDOW_HOST__?.availableUpdateVersion ?? null; }

export function ProductUpdateNotice() {
  const version = useSyncExternalStore(subscribe, snapshot);
  const [error, setError] = useState(false);
  if (!version || !window.__COPPERBENCH_WINDOW_HOST__?.updatesAvailable) return null;
  const label = uiText('更新至 ', 'Update to ') + version;
  return <div className="product-update-notice" data-testid="product-update-notice">
    <button type="button" className="product-update-button" title={label} onClick={() => {
      setError(false);
      void window.__COPPERBENCH_WINDOW_HOST__!.invoke('check_updates').catch(() => setError(true));
    }}>
      <ArrowDownToLine size={16} aria-hidden="true" /><span>{label}</span>
    </button>
    {error && <p role="alert">{uiText('暂时无法打开更新，请重试。', 'Could not open updates. Please try again.')}</p>}
  </div>;
}
