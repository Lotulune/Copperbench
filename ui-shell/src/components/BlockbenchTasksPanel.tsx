import { tr } from '../i18n/locale';
import { safeRandomUUID } from '../bridge/JcefCoreBridge';
import React, { useEffect, useRef, useState } from 'react';
import { coreBridge, isNativeHostPresent } from '../bridge';
import { mcpRuntimeBridge } from '../bridge/mcpRuntimeBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import type { AssetProjection, CommandOperation, ModElementSummary } from '../types/contract';
import { blockbenchBridge } from '../bridge/blockbenchBridge';
import { t, uiText } from '../i18n';
import './blockbenchSetup.css';
import { BlockbenchImportPanel } from './BlockbenchImportPanel';

interface ModelingTask {
  taskId: string; state: 'editing' | 'ready_to_import' | 'cancelled' | 'importing' | 'imported'; targetRelativePath: string;
  editPath: string; editSha256: string | null; sourceChanged: boolean; candidatePath: string | null;
  hasSavedChanges: boolean; recoveryPointId: string;
  candidateChanged?: boolean;
  createdAt?: string;
  importFiles?: Array<{ targetRelativePath: string }>;
  elementContext?: { elementId: string; name: string; type: string; namespace: string; modelResource: string; textureDirectory: string };
  binding?: { state: 'bound' | 'unbound' | 'manual' | 'element_missing'; modelResource?: string };
}

export const BlockbenchTasksPanel: React.FC<{ source?: { id: string; name: string; path: string } | null; element?: ModElementSummary }> = ({ source, element }) => {
  const { state } = useWorkbench();
  const labels = { editing: tr("编辑中"), ready_to_import: tr("候选已保存，待回导"), cancelled: tr("已取消，文件保留"), importing: tr("回导中断，需要恢复"), imported: tr("文件已回导") };
  const [tasks, setTasks] = useState<ModelingTask[]>([]);
  const [target, setTarget] = useState('models/blockbench/new_model.bbmodel');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [loaded, setLoaded] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [creationExpanded, setCreationExpanded] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const pendingTaskFocus = useRef<string | null>(null);
  const taskCards = useRef(new Map<string, HTMLElement>());
  const [modelSources, setModelSources] = useState<AssetProjection['assets']>([]);
  const [sourceId, setSourceId] = useState('');
  const refreshRef = useRef<() => Promise<void>>(async () => {});
  const [retry, setRetry] = useState<{ taskId: string; key: string } | null>(null);
  const writable = isNativeHostPresent() && state.workbench?.permission.profile !== 'read_only';
  const manual = element?.ownership === 'manual';

  useEffect(() => {
    const id = pendingTaskFocus.current;
    const card = id ? taskCards.current.get(id) : undefined;
    if (!card) return;
    pendingTaskFocus.current = null;
    card.scrollIntoView({ block: 'nearest' });
    card.focus({ preventScroll: true });
  }, [tasks, selectedTaskId]);

  const refresh = async () => {
    const workspaceId = state.workbench?.workspace.id;
    if (!workspaceId) throw new Error(tr("请先打开工作区。"));
    const result = await coreBridge.sendQuery<{ tasks: ModelingTask[] }>({ messageType: 'query', schemaVersion: '1.0',
      requestId: safeRandomUUID(), workspaceId, operation: 'list_blockbench_tasks', payload: {} });
    if (result.status !== 'succeeded') throw new Error(t(result.diagnostics[0]?.message, result.diagnostics[0]?.code) || tr("任务列表读取失败。"));
    setTasks(result.data?.tasks ?? []);
    setLoaded(true);
    if (element) {
      const assets = await coreBridge.sendQuery<AssetProjection>({ messageType: 'query', schemaVersion: '1.0',
        requestId: safeRandomUUID(), workspaceId, operation: 'list_assets', payload: {} });
      if (assets.status === 'succeeded') setModelSources((assets.data?.assets ?? []).filter(asset => asset.relativePath.endsWith('.bbmodel')));
    }
  };
  refreshRef.current = refresh;
  useEffect(() => {
    if (!expanded || !isNativeHostPresent()) return;
    const reload = () => { void refreshRef.current().catch(error => setMessage(error.message)); };
    reload();
    window.addEventListener('focus', reload);
    return () => window.removeEventListener('focus', reload);
  }, [expanded, state.workbench?.workspace.id, element?.id]);
  const run = async (operation: CommandOperation, payload: Record<string, unknown>) => {
    const workspace = state.workbench?.workspace;
    if (!workspace) return false;
    setBusy(true);
    try {
      if (operation === 'finish_blockbench_task') {
        // Native window focus events are not a reliable notification that Blockbench saved.
        // Read the current disk observation for this explicit action; Core still rejects a save racing the command.
        const current = await coreBridge.sendQuery<ModelingTask>({ messageType: 'query', schemaVersion: '1.0',
          requestId: safeRandomUUID(), workspaceId: workspace.id, operation: 'get_blockbench_task', payload: { taskId: payload.taskId } });
        if (current.status !== 'succeeded' || !current.data) {
          const diagnostic = current.diagnostics[0];
          throw new Error(`${t(diagnostic?.message, diagnostic?.code) || '无法读取磁盘保存状态。'}${diagnostic ? `（${diagnostic.code}）` : ''}`);
        }
        const saved = current.data;
        setTasks(previous => previous.map(task => task.taskId === saved.taskId ? saved : task));
        if (saved.state !== 'editing') throw new Error('建模任务状态已改变，请查看当前阶段后继续。');
        if (saved.sourceChanged) throw new Error('源资产已改变，请先处理冲突；编辑副本仍被保留。');
        if (!saved.editSha256) throw new Error('尚未找到磁盘编辑副本，请先在 Blockbench 保存项目。');
        payload = { ...payload, savedSha256: saved.editSha256 };
      }
      const result = await coreBridge.sendCommand({ messageType: 'command', schemaVersion: '1.0', requestId: safeRandomUUID(),
        workspaceId: workspace.id, expectedRevision: workspace.revision, operation,
        payload: { ...payload, clientMutationId: safeRandomUUID() } });
      if (result.status !== 'completed' && result.status !== 'committed') {
        const diagnostic = result.diagnostics[0];
        throw new Error(tr("{0}{1}", [t(diagnostic?.message, diagnostic?.code) || tr("任务未完成。"), diagnostic ? `（${diagnostic.code}）` : '']));
      }
      setRetry(null);
      setMessage(operation === 'finish_blockbench_task' ? uiText('候选已保存，待回导。', 'Candidate saved; ready to import.')
        : operation === 'cancel_blockbench_task' ? uiText('任务已取消，文件已保留。', 'Task cancelled; files retained.')
        : operation === 'import_blockbench_task' ? uiText('模型与贴图已回导。', 'Model and textures imported.')
        : operation === 'recover_blockbench_import' ? uiText('回导已恢复。', 'Import recovered.')
        : operation === 'bind_blockbench_model' ? uiText('模型已关联。', 'Model linked.')
        : uiText('编辑副本已准备。', 'Editing copy is ready.'));
      await refresh();
      return true;
    } catch (error) { setMessage(error instanceof Error ? error.message : tr("任务操作失败。")); return false; }
    finally { setBusy(false); }
  };
  const begin = (assetId?: string) => {
    const key = `${element?.id ?? ''}:${assetId ?? target}`;
    const taskId = retry?.key === key ? retry.taskId : safeRandomUUID();
    setRetry({ taskId, key });
    void run('begin_blockbench_task', { taskId, ...(element ? { elementId: element.id } : {}),
      ...(assetId ? { assetId } : element ? {} : { targetRelativePath: target }) }).then(completed => {
      if (!completed) return;
      pendingTaskFocus.current = taskId;
      setCreationExpanded(false);
      setSelectedTaskId(taskId);
    });
  };
  const active = (task: ModelingTask) => ['editing', 'ready_to_import', 'importing'].includes(task.state);
  const visibleTasks = [...(element ? tasks.filter(task => task.elementContext?.elementId === element.id) : tasks)]
    .sort((a, b) => Number(b.taskId === selectedTaskId) - Number(a.taskId === selectedTaskId)
      || Number(active(b)) - Number(active(a)) || (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));

  const creationControls = (
      <div className="blockbench-setup-actions">
        {element && !manual && modelSources.length > 0 && <>
          <label>现有源模型<select value={sourceId} disabled={busy} onChange={event => setSourceId(event.target.value)}>
            <option value="">选择工作区中的模型副本来源</option>
            {modelSources.map(asset => <option key={asset.id} value={asset.id}>{asset.relativePath}</option>)}
          </select></label>
          <button className="btn-secondary" type="button" disabled={!writable || busy || !sourceId}
            onClick={() => begin(sourceId)}>从现有模型创建副本</button>
        </>}
        {!element && <label>新模型目标路径<input aria-label="新模型目标路径" value={target} disabled={busy} onChange={event => setTarget(event.target.value)} /></label>}
        <button className="btn-secondary" type="button" disabled={busy || !writable || manual} onClick={() => begin()}>新建建模副本</button>
        {source?.path.endsWith('.bbmodel') && <button className="btn-secondary" type="button" disabled={busy || !writable || manual}
          onClick={() => begin(source.id)}>从所选模型创建副本</button>}

      </div>
  );

  return <details name="blockbench-tools" className="blockbench-setup" data-testid="blockbench-tasks"
    onToggle={event => setExpanded(event.currentTarget.open)}>
    <summary>{element ? uiText('Blockbench 模型', 'Blockbench model') : uiText('建模任务', 'Modeling tasks')}</summary>
    <div className="blockbench-setup-content">
      {visibleTasks.length === 0 && <p>{uiText('Java 方块 / 物品模型', 'Java block / item models')}</p>}
      {!isNativeHostPresent() && <p>{uiText('请在桌面应用中创建任务。', 'Create tasks in the desktop app.')}</p>}
      {manual && <p role="alert">{uiText('此元素由源码管理，需在源码中关联模型。', 'This element is source-managed. Link its model in source.')}</p>}
      <p className="modeling-task-message" role="status">{message}</p>
      {visibleTasks.length === 0 ? creationControls : <details className="modeling-extra-task" open={creationExpanded}
        onToggle={event => setCreationExpanded(event.currentTarget.open)}>
        <summary>创建另一个建模副本</summary>{creationControls}
      </details>}
      <div className="blockbench-setup-actions">
        <button className="btn-secondary" type="button" disabled={busy} onClick={() => {
          setBusy(true); void refresh().then(() => setMessage(tr("已刷新磁盘保存状态。"))).catch(error => setMessage(error.message)).finally(() => setBusy(false));
        }}>{tr("刷新任务与保存状态")}</button>
      </div>
      {loaded && visibleTasks.length === 0 && <p>{element ? "当前元素没有关联的建模任务。" : "当前没有建模任务。"}</p>}
      {visibleTasks.map(task => <article className="blockbench-task" key={task.taskId} tabIndex={-1}
        data-modeling-task-id={task.taskId} ref={node => {
          if (node) taskCards.current.set(task.taskId, node); else taskCards.current.delete(task.taskId);
        }}>
        <strong>{task.elementContext?.name ?? task.targetRelativePath.split(/[\\/]/).pop()} · {labels[task.state]}</strong>
        {task.state === 'cancelled' ? <p>{uiText('编辑副本与候选文件已保留。', 'Editing copies and candidate files are retained.')}</p> : <details>
        <summary>{uiText('操作步骤', 'Workflow steps')}</summary>
        <ol aria-label="建模步骤" className="modeling-steps">
          <li>创建副本：已完成</li>
          <li aria-current={task.state === 'editing' ? 'step' : undefined}>打开编辑器、保存磁盘副本：{['ready_to_import', 'imported'].includes(task.state) && !task.candidateChanged ? '已确认保存' : '待确认'}</li>
          <li aria-current={task.state === 'ready_to_import' ? 'step' : undefined}>识别游戏导出、预览并回导：{task.state === 'imported' ? '已回导' : '尚未回导'}</li>
          <li aria-current={task.state === 'imported' ? 'step' : undefined}>关联、构建与游戏验证：{task.binding?.state === 'bound' ? '定义已关联，继续构建与验证' : '尚未确认完成'}</li>
        </ol>

        </details>}
        {task.sourceChanged && <p role="alert">源文件或目标已发生变化，请处理冲突后重新创建任务；当前副本仍被保留。</p>}
        {task.candidateChanged && <p role="alert">{uiText('候选或编辑副本在完成后发生变化，请重新核验。', 'The candidate or editing copy changed after completion. Verify it again.')}</p>}
        <div className="blockbench-setup-actions">
          <button className="btn-secondary" type="button" disabled={!writable || busy || task.state !== 'editing' || task.sourceChanged}
            onClick={() => {
              setBusy(true);
              void blockbenchBridge.openTask(task.taskId).then(result => setMessage(result.state === 'running'
                ? uiText('已打开副本。请在编辑目录保存并导出 JSON 和 PNG。', 'Copy opened. Save and export JSON and PNG in the editing folder.')
                : `尚未打开编辑器（${result.diagnosticCode ?? result.state}）。可在安装设置中选择 Blockbench，或复制路径后手工打开。`))
                .catch(error => setMessage(error.message)).finally(() => setBusy(false));
            }}>在 Blockbench 打开副本</button>
          <button className="btn-secondary" type="button" onClick={() => {
            void mcpRuntimeBridge.copyText(task.candidatePath ?? task.editPath).then(() => setMessage('已复制文件路径。')).catch(() => setMessage('复制失败，请手动复制路径。'));
          }}>复制文件路径</button>
          <button className="btn-secondary" type="button" onClick={() => {
            void mcpRuntimeBridge.copyText(task.editPath.replace(/[\\/][^\\/]+$/, '')).then(() => setMessage('已复制编辑目录。请在此目录导出游戏 JSON 和 PNG。'))
              .catch(() => setMessage('复制失败，请手动复制上方编辑目录。'));
          }}>复制编辑目录</button>
          <button className="btn-secondary" type="button" disabled={!writable || busy || task.state !== 'editing' || task.sourceChanged || !task.editSha256}
            onClick={() => void run('finish_blockbench_task', { taskId: task.taskId, savedSha256: task.editSha256 })}>{tr("确认磁盘保存并生成候选")}</button>
          <button className="btn-secondary" type="button" disabled={!writable || busy || !['editing', 'ready_to_import'].includes(task.state)}
            onClick={() => void run('cancel_blockbench_task', { taskId: task.taskId })}>{tr("取消任务并保留文件")}</button>
        </div>
        <BlockbenchImportPanel taskId={task.taskId} taskState={task.state} writable={writable} busy={busy} files={task.importFiles} run={run}
          targetElementId={task.elementContext?.elementId} binding={task.binding} invalidCandidate={task.candidateChanged || task.sourceChanged} />
        <details className="modeling-task-details">
          <summary>查看资源路径与保存详情</summary>
        {task.elementContext && <div className="modeling-context">
          <p>目标元素：{task.elementContext.name} · 命名空间：<code>{task.elementContext.namespace}</code></p>
          <p>建议游戏模型：<code>{task.elementContext.modelResource}</code></p>
          <p>建议贴图目录：<code>{task.elementContext.textureDirectory}</code></p>
        </div>}
        <p>{task.hasSavedChanges ? '磁盘副本已有保存变化' : '未检测到副本内容变化'} · 恢复点：{task.recoveryPointId}</p>
        <p>游戏 JSON 和 PNG 导出到编辑目录：<code>{task.editPath.replace(/[\\/][^\\/]+$/, '')}</code></p>
        <code>{task.candidatePath ?? task.editPath}</code>
        </details>
      </article>)}
    </div>
  </details>;
};
