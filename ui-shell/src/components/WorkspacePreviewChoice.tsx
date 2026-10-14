import React, { useState } from 'react';
import { useWorkbench } from '../context/WorkbenchContext';
import { uiText } from '../i18n/locale';
import { t } from '../i18n';
import { elementLabel } from '../i18n/labels';
import type { ModElementType } from '../types/contract';

export const WorkspacePreviewChoice: React.FC = () => {
  const { workspacePreviewTarget: target, previewTargets, state } = useWorkbench();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!target) return null;
  const selected = target.selected && target.destination === '';
  const types = target.capabilities.elementTypes?.filter(type => type.supported) ?? [];
  return <div className="workspace-preview-choice" data-testid="workspace-preview-choice">
    <label>
      <input type="checkbox" checked={selected} disabled={pending || state.workbench?.permission.profile === 'read_only'}
        onChange={async event => {
          const enable = event.target.checked;
          setPending(true); setError(null);
          try {
            const result = await previewTargets({ operation: enable ? 'opt_in' : 'revoke', generatorId: target.generatorId });
            if (result.status !== 'succeeded') setError(result.diagnostics.map(d => t(d.message, d.code)).join('; '));
          } catch (failure) { setError(failure instanceof Error ? failure.message : String(failure)); }
          finally { setPending(false); }
        }} />
      <span>{uiText('在本次会话使用工作区预览', 'Use workspace preview in this session')} · {target.generatorId}</span>
    </label>
    <p>{types.length ? <>{uiText('可创建类型：', 'Available types: ')}{types.map(type => elementLabel(type.elementType as ModElementType)).join(', ')}</>
      : uiText('此目标尚无可创建的结构化类型。', 'This target has no available structured types yet.')}</p>
    {error && <p role="alert">{error}</p>}
  </div>;
};
