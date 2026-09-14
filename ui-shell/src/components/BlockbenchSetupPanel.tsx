import React, { useId, useRef, useState } from 'react';
import { coreBridge } from '../bridge';
import { safeRandomUUID } from '../bridge/JcefCoreBridge';
import { mcpRuntimeBridge } from '../bridge/mcpRuntimeBridge';
import { blockbenchBridge } from '../bridge/blockbenchBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import { t } from '../i18n';
import './blockbenchSetup.css';

interface Environment {
  editor: { state: string; available: boolean; version?: string; diagnosticCode?: string };
  mcp: { state: string; endpoint: string; toolCount?: number; hasMoreTools?: boolean; checkedAt?: string };
  managedModelingTasksAvailable: boolean;
}

const editorLabels: Record<string, string> = {
  ready: '已检测到编辑器', ready_unverified: '已找到编辑器，版本未确认',
  unavailable: '尚未检测到编辑器', unknown_version: '编辑器版本无法确认', incompatible: '编辑器版本不兼容'
};
const downloadUrl = 'https://www.blockbench.net/';
const pluginUrl = 'https://github.com/jasonjgardner/blockbench-mcp-plugin';
const mcpLabels: Record<string, string> = {
  not_checked: '尚未测试连接', tools_available: '握手成功，工具可发现', no_tools: '服务可连接，但未发现工具',
  unreachable: '无法连接，请启动 Blockbench 并启用社区插件', timeout: '连接超时，请检查插件服务后重试',
  protocol_error: 'MCP 协议检测失败，请核对地址、插件状态及认证要求',
  authentication_required: '服务要求认证；请在 Agent 中配置，本页不会转发 Copperbench 凭据',
  busy: '已有连接检测正在运行，请稍后重试',
  preview_unavailable: '预览模式无法检测本机服务'
};

/** Optional setup: disclosure itself performs no network request or installation. */
export const BlockbenchSetupPanel: React.FC = () => {
  const { state } = useWorkbench();
  const endpointId = useId();
  const [endpoint, setEndpoint] = useState('http://127.0.0.1:3000/bb-mcp');
  const [environment, setEnvironment] = useState<Environment | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const requestSequence = useRef(0);

  const inspect = async (probeMcp: boolean) => {
    const sequence = ++requestSequence.current;
    setBusy(true);
    setEnvironment(null);
    setMessage(probeMcp ? '正在检测编辑器并测试本机 MCP 连接…' : '正在重新检测本机编辑器…');
    try {
      const workspaceId = state.workbench?.workspace.id;
      if (!workspaceId) throw new Error('请先打开工作区。');
      const result = await coreBridge.sendQuery<Environment>({
        messageType: 'query', schemaVersion: '1.0', requestId: safeRandomUUID(), workspaceId,
        operation: 'get_blockbench_environment', payload: { endpoint, probeMcp }
      });
      if (sequence !== requestSequence.current) return;
      if (result.status !== 'succeeded' || !result.data?.editor || !result.data?.mcp)
        throw new Error(t(result.diagnostics[0]?.message) || '检测不可用，请确认桌面产品已更新。');
      setEnvironment(result.data);
      setMessage(probeMcp ? '连接检测完成。' : '编辑器检测完成；尚未测试 MCP 连接。');
    } catch (error) {
      if (sequence === requestSequence.current)
        setMessage(error instanceof Error ? error.message : '检测失败，请重试。');
    } finally {
      if (sequence === requestSequence.current) setBusy(false);
    }
  };

  const copy = async (text: string, label: string) => {
    try { await mcpRuntimeBridge.copyText(text); setMessage(`已复制${label}，请粘贴到浏览器或 Agent 的 MCP 设置。`); }
    catch { setMessage('复制失败，请手动选择并复制显示的地址。'); }
  };

  return <details name="blockbench-tools" className="blockbench-setup" data-testid="blockbench-setup">
    <summary>连接 Blockbench · 可选建模工具</summary>
    <div className="blockbench-setup-content">
      <p>使用独立开源工具 Blockbench 编辑模型；AI 建模还需要社区 MCP 插件。可稍后设置，继续制作模组。</p>
      <div className="blockbench-setup-status" aria-live="polite" aria-atomic="true">
        <span>编辑器：{environment ? editorLabels[environment.editor.state] ?? '状态未知' : '尚未检测'}
          {environment?.editor.version ? `（${environment.editor.version}）` : ''}</span>
        <span>AI 建模连接：{environment ? mcpLabels[environment.mcp.state] ?? '状态未知' : '尚未测试连接'}
          {environment?.mcp.toolCount != null ? `（本页 ${environment.mcp.toolCount} 个工具）` : ''}</span>
      </div>
      <div className="blockbench-setup-actions">
        <button className="btn-secondary" type="button" disabled={busy} onClick={() => {
          setBusy(true);
          void blockbenchBridge.selectExecutable().then(async result => {
            if (!result.cancelled) await inspect(false);
            else setMessage('已取消选择，原安装设置保持不变。');
          }).catch(error => setMessage(error instanceof Error ? error.message : '安装位置选择失败。')).finally(() => setBusy(false));
        }}>选择安装位置</button>
        <button className="btn-secondary" type="button" disabled={busy} onClick={() => void inspect(false)}>重新检测安装</button>
      </div>
      <ol>
        <li>从官网下载桌面版 Blockbench。已有安装会优先复用；安装完成后重新检测。
          <div className="blockbench-setup-link"><code>{downloadUrl}</code>
            <button className="btn-secondary" type="button" onClick={() => void copy('https://www.blockbench.net/', '官方下载地址')}>复制官方下载地址</button></div>
        </li>
        <li>需要 AI 建模时，在 Blockbench 的插件菜单中按社区项目说明安装 MCP 插件，并保持编辑器运行。
          <div className="blockbench-setup-link"><code>{pluginUrl}</code>
            <button className="btn-secondary" type="button" onClick={() => void copy('https://github.com/jasonjgardner/blockbench-mcp-plugin', '社区插件说明地址')}>复制插件说明地址</button></div>
        </li>
        <li>填写插件显示的本机服务地址，测试后将该地址添加到 Agent；Copperbench MCP 仍负责模组工作区。</li>
      </ol>
      <label htmlFor={endpointId}>Blockbench MCP 本机地址</label>
      <div className="blockbench-setup-actions">
        <input id={endpointId} value={endpoint} maxLength={512} spellCheck={false} disabled={busy}
          onChange={event => { setEndpoint(event.target.value); setEnvironment(null); setMessage('地址已修改，请重新测试连接。'); }} />
        <button className="btn-secondary" type="button" disabled={busy} onClick={() => void inspect(true)}>{busy ? '检测中…' : '测试 MCP 连接'}</button>
        <button className="btn-secondary" type="button" disabled={busy} onClick={() => void copy(endpoint, '连接地址')}>复制连接地址</button>
      </div>
      <p className="blockbench-setup-note">连接测试仅发送握手与工具查询，不上传工作区、不执行建模。工具可发现不代表 Agent 已连接或模型已回导。</p>
      <p className="blockbench-setup-note">可在资产中心创建建模副本，保存候选后预览并回导导出的模型和贴图，再关联元素。原有手工编辑可使用“在 Blockbench 打开”。安装位置选择会保存在本机，启动参数如有指定则优先。</p>
      <p className="blockbench-setup-note">Blockbench 与社区 MCP 插件均为独立 GPLv3 项目；社区插件并非 Blockbench 官方 MCP。Copperbench 当前不捆绑或自动安装它们。</p>
      <p role="status" className="blockbench-setup-message">{message}</p>
    </div>
  </details>;
};
