import { tr } from '../i18n/locale';
import React, { useId, useRef, useState } from 'react';
import { coreBridge } from '../bridge';
import { safeRandomUUID } from '../bridge/JcefCoreBridge';
import { mcpRuntimeBridge } from '../bridge/mcpRuntimeBridge';
import { blockbenchBridge } from '../bridge/blockbenchBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import { t, uiText } from '../i18n';
import './blockbenchSetup.css';

interface Environment {
  editor: { state: string; available: boolean; version?: string; diagnosticCode?: string };
  mcp: { state: string; endpoint: string; toolCount?: number; hasMoreTools?: boolean; checkedAt?: string;
    diagnosticCode?: string; failurePhase?: string; cleanupDiagnosticCode?: string };
  inspectionState?: string;
  application?: { sourceState?: string; version?: string; javaVersion?: string; javaVendor?: string;
    mcpSdkVersion?: string; applicationSha256?: string };
  managedModelingTasksAvailable: boolean;
}

const editorLabels: Record<string, string> = {
  ready: '已检测到编辑器', ready_unverified: '已找到编辑器，版本未确认',
  unavailable: '尚未检测到编辑器', unknown_version: '编辑器版本无法确认', incompatible: '编辑器版本不兼容',
  unverified: '本次安装检测未完成，请重试', not_checked: '尚未检测'
};
const downloadUrl = 'https://www.blockbench.net/';
const pluginUrl = 'https://github.com/jasonjgardner/blockbench-mcp-plugin';
const mcpLabels: Record<string, string> = {
  not_checked: '尚未测试连接', tools_available: '握手成功，工具可发现', no_tools: '服务可连接，但未发现工具',
  unreachable: '无法连接，请启动 Blockbench 并启用社区插件', timeout: '连接超时，请检查插件服务后重试',
  protocol_error: 'MCP 协议检测失败，请核对地址、插件状态及认证要求',
  authentication_required: '服务要求认证；请在 Agent 中配置，本页不会转发 Copperbench 凭据',
  busy: '已有连接检测正在运行，请稍后重试',
  initialization_error: '连接组件初始化失败，请查看运行详情及本机日志',
  cancelled: '连接检测已中断，可以重新测试',
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
    setMessage(probeMcp ? tr("正在检测编辑器并测试本机 MCP 连接…") : tr("正在重新检测本机编辑器…"));
    try {
      const workspaceId = state.workbench?.workspace.id;
      if (!workspaceId) throw new Error(tr("请先打开工作区。"));
      const result = await coreBridge.sendQuery<Environment>({
        messageType: 'query', schemaVersion: '1.0', requestId: safeRandomUUID(), workspaceId,
        operation: 'get_blockbench_environment', payload: { endpoint, probeMcp }
      });
      if (sequence !== requestSequence.current) return;
      if (result.status !== 'succeeded' || !result.data?.editor || !result.data?.mcp)
        throw new Error(t(result.diagnostics[0]?.message) || tr("检测不可用，请确认桌面产品已更新。"));
      setEnvironment(result.data);
      setMessage(result.data.inspectionState && result.data.inspectionState !== 'completed'
        ? '本次检测未完成；已获取的信息保留，可稍后重试。'
        : probeMcp ? '连接检测完成。' : '编辑器检测完成；尚未测试 MCP 连接。');
    } catch (error) {
      if (sequence === requestSequence.current)
        setMessage(error instanceof Error ? error.message : tr("检测失败，请重试。"));
    } finally {
      if (sequence === requestSequence.current) setBusy(false);
    }
  };

  const copy = async (text: string, label: string) => {
    try { await mcpRuntimeBridge.copyText(text); setMessage(tr("已复制{0}，请粘贴到浏览器或 Agent 的 MCP 设置。", [label])); }
    catch { setMessage(tr("复制失败，请手动选择并复制显示的地址。")); }
  };

  return <details name="blockbench-tools" className="blockbench-setup" data-testid="blockbench-setup">
    <summary>{uiText('Blockbench 设置', 'Blockbench setup')}</summary>
    <div className="blockbench-setup-content">
      <div className="blockbench-setup-status" aria-live="polite" aria-atomic="true">
        <span>{tr("编辑器：")}{environment ? tr(editorLabels[environment.editor.state] ?? environment.editor.state) || tr("状态未知") : tr("尚未检测")}
          {environment?.editor.version ? `（${environment.editor.version}）` : ''}</span>
        <span>{tr("AI 建模连接：")}{environment ? tr(mcpLabels[environment.mcp.state] ?? environment.mcp.state) || tr("状态未知") : tr("尚未测试连接")}
          {environment?.mcp.toolCount != null ? tr("（本页 {0} 个工具）", [environment.mcp.toolCount]) : ''}</span>
      </div>
      {environment && <details className="blockbench-setup-note">
        <summary>检测与运行详情</summary>
        {environment.mcp.diagnosticCode && <p>连接诊断：<code>{environment.mcp.diagnosticCode}</code>
          {environment.mcp.failurePhase && <> · <code>{environment.mcp.failurePhase}</code></>}</p>}
        {environment.mcp.cleanupDiagnosticCode && <p>关闭诊断：<code>{environment.mcp.cleanupDiagnosticCode}</code></p>}
        {environment.editor.diagnosticCode && <p>安装诊断：<code>{environment.editor.diagnosticCode}</code></p>}
        <p>构建来源：{environment.application?.sourceState === 'packaged_binary' ? '已打包程序' : '开发构建或来源未确认'}
          {environment.application?.version && <> · {environment.application.version}</>}</p>
        {environment.application?.applicationSha256 && <p>程序 SHA-256：<code style={{ overflowWrap: 'anywhere' }}>{environment.application.applicationSha256}</code></p>}
        {environment.application?.javaVersion && <p>{t({ key: 'blockbench.runtime.java', fallback: 'Java: ' })}{environment.application.javaVersion} · {environment.application.javaVendor}</p>}
        {environment.application?.mcpSdkVersion && <p>{t({ key: 'blockbench.runtime.sdk', fallback: 'MCP SDK: ' })}{environment.application.mcpSdkVersion}</p>}
      </details>}
      <div className="blockbench-setup-actions">
        <button className="btn-secondary" type="button" disabled={busy} onClick={() => {
          setBusy(true);
          void blockbenchBridge.selectExecutable().then(async result => {
            if (!result.cancelled) await inspect(false);
            else setMessage(tr("已取消选择，原安装设置保持不变。"));
          }).catch(error => setMessage(error instanceof Error ? error.message : tr("安装位置选择失败。"))).finally(() => setBusy(false));
        }}>{tr("选择安装位置")}</button>
        <button className="btn-secondary" type="button" disabled={busy} onClick={() => void inspect(false)}>{tr("重新检测安装")}</button>
      </div>
      <details className="blockbench-setup-note"><summary>{uiText('安装步骤', 'Installation steps')}</summary><ol>
        <li>{tr("从官网下载桌面版 Blockbench。已有安装会优先复用；安装完成后重新检测。")}<div className="blockbench-setup-link"><code>{downloadUrl}</code>
            <button className="btn-secondary" type="button" onClick={() => void copy('https://www.blockbench.net/', tr("官方下载地址"))}>{tr("复制官方下载地址")}</button></div>
        </li>
        <li>{tr("需要 AI 建模时，在 Blockbench 的插件菜单中按社区项目说明安装 MCP 插件，并保持编辑器运行。")}<div className="blockbench-setup-link"><code>{pluginUrl}</code>
            <button className="btn-secondary" type="button" onClick={() => void copy('https://github.com/jasonjgardner/blockbench-mcp-plugin', tr("社区插件说明地址"))}>{tr("复制插件说明地址")}</button></div>
        </li>
        <li>{tr("填写插件显示的本机服务地址，测试后将该地址添加到 Agent；Copperbench MCP 仍负责模组工作区。")}</li>
      </ol></details>
      <label htmlFor={endpointId}>{tr("Blockbench MCP 本机地址")}</label>
      <div className="blockbench-setup-actions">
        <input id={endpointId} value={endpoint} maxLength={512} spellCheck={false} disabled={busy}
          onChange={event => { setEndpoint(event.target.value); setEnvironment(null); setMessage(tr("地址已修改，请重新测试连接。")); }} />
        <button className="btn-secondary" type="button" disabled={busy} onClick={() => void inspect(true)}>{busy ? tr("检测中…") : tr("测试 MCP 连接")}</button>
        <button className="btn-secondary" type="button" disabled={busy} onClick={() => void copy(endpoint, tr("连接地址"))}>{tr("复制连接地址")}</button>
      </div>
      <details className="blockbench-setup-note"><summary>{uiText('连接与回导说明', 'Connection and import help')}</summary>
      <p>{uiText('连接测试只检查服务。模型和贴图需在建模任务中回导。', 'The connection test checks the service. Import models and textures from a modeling task.')}</p>
      <p className="blockbench-setup-note">{tr("可在资产中心创建建模副本，保存候选后预览并回导导出的模型和贴图，再关联元素。原有手工编辑可使用“在 Blockbench 打开”。安装位置选择会保存在本机，启动参数如有指定则优先。")}</p>
      <p className="blockbench-setup-note">{tr("Blockbench 与社区 MCP 插件均为独立 GPLv3 项目；社区插件并非 Blockbench 官方 MCP。Copperbench 当前不捆绑或自动安装它们。")}</p>
      </details>
      <p role="status" className="blockbench-setup-message">{message}</p>
    </div>
  </details>;
};
