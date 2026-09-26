import React, { useEffect, useState } from 'react';
import { coreBridge, isNativeHostPresent } from '../bridge';
import { safeRandomUUID } from '../bridge/JcefCoreBridge';
import { blockbenchBridge } from '../bridge/blockbenchBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import { uiText } from '../i18n';

export const BlockbenchOnboarding: React.FC = () => {
  const { state, setActiveView } = useWorkbench();
  const [show, setShow] = useState(false);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  const workspaceId = state.workbench?.workspace.id;
  useEffect(() => {
    let active = true;
    if (workspaceId && isNativeHostPresent()) void coreBridge.sendQuery<{ onboardingDismissed?: boolean }>({
      messageType: 'query', schemaVersion: '1.0', requestId: safeRandomUUID(), workspaceId,
      operation: 'get_blockbench_environment', payload: {}
    }).then(result => { if (active && result.status === 'succeeded') setShow(result.data?.onboardingDismissed === false); }).catch(() => {});
    return () => { active = false; };
  }, [workspaceId]);
  if (!show) return null;
  return <aside className="blockbench-setup" data-testid="blockbench-onboarding" style={{ padding: 14, margin: 0 }}>
    <strong>{uiText('可选建模工具：Blockbench', 'Optional modeling tool: Blockbench')}</strong>
    <p>{uiText('需要自定义模型时，可连接独立的 Blockbench 编辑器及社区 MCP。现在可以跳过，之后从资产中心进入设置。', 'For custom models, connect the standalone Blockbench editor and community MCP. You can skip this now and set it up later in Assets & models.')}</p>
    <div className="blockbench-setup-actions">
      <button className="btn-secondary" type="button" onClick={() => setActiveView('assets')}>{uiText('查看建模工具设置', 'View modeling tool setup')}</button>
      <button className="btn-secondary" type="button" disabled={busy} onClick={() => {
        setBusy(true); void blockbenchBridge.dismissSetup().then(() => setShow(false)).catch(() => setError(true)).finally(() => setBusy(false));
      }}>{uiText('稍后设置，继续制作模组', 'Set up later and continue')}</button>
    </div>
    {error && <p role="alert">{uiText('偏好保存失败，请稍后重试。', 'Could not save this preference. Please try again later.')}</p>}
  </aside>;
};
