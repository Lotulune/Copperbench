import { Select } from './Select';
import React, { useEffect, useState } from 'react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { t, uiText } from '../i18n';
import type { CommandResult, RequiredCaseManifest } from '../types/contract';
import { WorkspacePreviewChoice } from './WorkspacePreviewChoice';

export const AcceptanceRunDialog: React.FC<{
  loadManifests: () => Promise<RequiredCaseManifest[]>;
  onRun: (manifest: RequiredCaseManifest) => Promise<CommandResult | null>;
  onClose: () => void;
}> = ({ loadManifests, onRun, onClose }) => {
  const { workspacePreviewTarget: target, state } = useWorkbench();
  const [manifests, setManifests] = useState<RequiredCaseManifest[]>([]);
  const [manifest, setManifest] = useState<RequiredCaseManifest | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const dialogRef = useDialogA11y(true, busy ? null : onClose);
  const available = target?.selected && target.destination === '' && state.workbench?.permission.profile !== 'read_only'
    && target.capabilities.operations?.some(operation => operation.operation === 'run_gametest' && operation.available);

  useEffect(() => {
    let active = true;
    void loadManifests().then(values => { if (active) setManifests(values); })
      .catch(failure => { if (active) setError(String(failure.message ?? failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [loadManifests]);

  const importManifest = async (file?: File) => {
    setError(''); setManifest(null);
    if (!file) return;
    try {
      if (file.size > 1024 * 1024) throw new Error(uiText('清单文件不能超过 1 MiB。', 'The manifest file must be at most 1 MiB.'));
      const value = JSON.parse(await file.text());
      if (!value || value.schemaVersion !== '1' || typeof value.id !== 'string'
        || !['junit_tuple', 'gametest_resource'].includes(value.identityKind)
        || !Number.isInteger(value.minimumTests) || value.minimumTests < 1 || value.minimumTests > 10000
        || typeof value.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(value.sha256)
        || !Array.isArray(value.cases) || value.cases.length < 1 || value.cases.length > value.minimumTests
        || value.cases.some((item: unknown) => !item || typeof item !== 'object'
          || typeof (item as { className?: unknown }).className !== 'string' || typeof (item as { name?: unknown }).name !== 'string'))
        throw new Error(uiText('请选择完整的验收清单 JSON，包含用例和 SHA-256。', 'Choose a complete acceptance manifest JSON with cases and SHA-256.'));
      setManifest(value);
    } catch (failure) { setError(failure instanceof Error ? failure.message : String(failure)); }
  };

  return <div className="modal-overlay" data-testid="acceptance-run-dialog">
    <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="acceptance-run-title" className="modal-card create-element-card">
      <div className="modal-header"><h2 id="acceptance-run-title">{uiText('选择验收清单', 'Choose acceptance cases')}</h2></div>
      <form onSubmit={async event => {
        event.preventDefault();
        if (!manifest || !available || busy) return;
        setBusy(true); setError('');
        try {
          const result = await onRun(manifest);
          if (result?.status === 'accepted' || result?.status === 'committed') onClose();
          else setError(result?.diagnostics.map(diagnostic => t(diagnostic.message, diagnostic.code)).join('; ')
            || uiText('无法启动验收。', 'Could not start acceptance.'));
        } catch (failure) { setError(failure instanceof Error ? failure.message : String(failure)); }
        finally { setBusy(false); }
      }}>
        <div className="modal-body">
          <WorkspacePreviewChoice />
          <label htmlFor="acceptance-manifest">{uiText('已发布清单', 'Published manifest')}</label>
          <Select id="acceptance-manifest" value={manifests.includes(manifest!) ? manifest!.id : ''} disabled={loading || busy}
            onChange={event => { setManifest(manifests.find(value => value.id === event.target.value) ?? null); setError(''); }}>
            <option value="">{loading ? uiText('加载中…', 'Loading…') : uiText('选择与工作区匹配的清单', 'Choose a manifest matching this workspace')}</option>
            {manifests.map(value => <option key={value.id} value={value.id}>{value.id}</option>)}
          </Select>
          <label htmlFor="acceptance-manifest-file">{uiText('或载入自己的清单', 'Or load your own manifest')}</label>
          <input id="acceptance-manifest-file" type="file" accept=".json,application/json" disabled={busy}
            onChange={event => { void importManifest(event.target.files?.[0]); event.target.value = ''; }} />
          {manifest && <div>
            <p>{manifest.id} · {uiText(`至少执行 ${manifest.minimumTests} 项测试`, `Run at least ${manifest.minimumTests} tests`)}</p>
            <details><summary>{uiText(`必需用例（${manifest.cases.length}）`, `Required cases (${manifest.cases.length})`)}</summary>
              <ul style={{ maxHeight: 180, overflow: 'auto', overflowWrap: 'anywhere' }}>{manifest.cases.map((item, index) =>
                <li key={index}>{item.className ? `${item.className} · ` : ''}{item.name}</li>)}</ul>
            </details>
          </div>}
          {error && <p role="alert">{error}</p>}
        </div>
        <div className="modal-footer">
          <button type="button" className="btn-secondary" disabled={busy} onClick={onClose}>{uiText('取消', 'Cancel')}</button>
          <button type="submit" className="btn-primary" disabled={!manifest || !available || busy} aria-busy={busy}>
            {busy ? uiText('启动中…', 'Starting…') : uiText('运行测试', 'Run tests')}</button>
        </div>
      </form>
    </div>
  </div>;
};
