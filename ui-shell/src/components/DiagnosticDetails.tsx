import type { Diagnostic } from '../types/contract';
import { uiText } from '../i18n';

export function DiagnosticDetails({ diagnostics }: { diagnostics: Diagnostic[] }) {
  if (!diagnostics.length) return null;
  return <details className="recovery-diagnostic-details">
    <summary>{uiText('原始诊断', 'Original diagnostics')}</summary>
    <pre tabIndex={0} style={{ margin: '8px 0', maxHeight: 180, overflow: 'auto', whiteSpace: 'pre-wrap',
      overflowWrap: 'anywhere', fontFamily: 'var(--font-mono)', fontSize: 12, userSelect: 'text' }}>{JSON.stringify(diagnostics, null, 2)}</pre>
  </details>;
}
