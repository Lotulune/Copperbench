import React, { useEffect, useState } from 'react';
import { coreBridge, isNativeHostPresent } from '../bridge';
import { safeRandomUUID } from '../bridge/JcefCoreBridge';
import { blockbenchBridge } from '../bridge/blockbenchBridge';
import { useWorkbench } from '../context/WorkbenchContext';

export const BlockbenchOnboarding: React.FC = () => {
  const { state, setActiveView } = useWorkbench();
  const [show, setShow] = useState(false);
  const [error, setError] = useState('');
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
    <strong>可选建模工具：Blockbench</strong>
    <p>需要自定义模型时，可连接独立的 Blockbench 编辑器及社区 MCP。现在可以跳过，之后从资产中心进入设置。</p>
    <div className="blockbench-setup-actions">
      <button className="btn-secondary" type="button" onClick={() => setActiveView('assets')}>查看建模工具设置</button>
      <button className="btn-secondary" type="button" disabled={busy} onClick={() => {
        setBusy(true); void blockbenchBridge.dismissSetup().then(() => setShow(false)).catch(() => setError('偏好保存失败，请稍后重试。')).finally(() => setBusy(false));
      }}>稍后设置，继续制作模组</button>
    </div>
    {error && <p role="alert">{error}</p>}
  </aside>;
};
