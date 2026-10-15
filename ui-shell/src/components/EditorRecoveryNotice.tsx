import type { Diagnostic } from '../types/contract';
import { t, uiText } from '../i18n';
import { DiagnosticDetails } from './DiagnosticDetails';

/** Recovery belongs to the object session, independently of dismissible feedback. */
export function EditorRecoveryNotice({ saveUnknown, diagnostics, busy, onRead, editor, needsRead = false }: {
  saveUnknown: boolean; diagnostics: Diagnostic[]; busy: boolean; onRead: () => void; editor: string; needsRead?: boolean;
}) {
  if (!saveUnknown && !diagnostics.length && !needsRead) return null;
  return <section data-testid={`${editor}-recovery`} role={saveUnknown ? 'alert' : 'status'}
    style={{ padding: '10px 18px', borderBottom: '1px solid var(--border-subtle)', flexShrink: 0, overflowWrap: 'anywhere' }}>
    {saveUnknown && <p data-testid={`${editor}-save-unknown`} style={{ margin: '0 0 8px' }}>{uiText(
      '保存结果待确认。草稿已保留；请读取最新版本并核对后再保存。',
      'The save result is unknown. Your draft is preserved; read the latest version and compare before saving again.')}</p>}
    {diagnostics.map((diagnostic, index) => <p key={`${diagnostic.code}:${index}`} style={{ margin: '4px 0' }}>
      {diagnostic.path && <code>{diagnostic.path} · </code>}{t(diagnostic.message)}
    </p>)}
    <DiagnosticDetails diagnostics={diagnostics} />
    <button type="button" className="btn-secondary" disabled={busy} onClick={onRead} data-testid={`${editor}-read-latest`}>
      {uiText('读取最新版本并保留草稿', 'Read latest version and keep draft')}
    </button>
  </section>;
}
