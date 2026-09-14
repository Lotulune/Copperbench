import { safeRandomUUID } from '../bridge/JcefCoreBridge';
import React, { useState } from 'react';
import { coreBridge, isNativeHostPresent } from '../bridge';
import { mcpRuntimeBridge } from '../bridge/mcpRuntimeBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import type { CommandOperation } from '../types/contract';
import { t } from '../i18n';
import './blockbenchSetup.css';
import { BlockbenchImportPanel } from './BlockbenchImportPanel';

interface ModelingTask {
  taskId: string; state: 'editing' | 'ready_to_import' | 'cancelled' | 'importing' | 'imported'; targetRelativePath: string;
  editPath: string; editSha256: string | null; sourceChanged: boolean; candidatePath: string | null;
  hasSavedChanges: boolean; recoveryPointId: string;
  candidateChanged?: boolean;
  importFiles?: Array<{ targetRelativePath: string }>;
}
const labels = { editing: '编辑中', ready_to_import: '候选已保存，待回导', cancelled: '已取消，文件保留', importing: '回导中断，需要恢复', imported: '文件已回导' };

export const BlockbenchTasksPanel: React.FC<{ source?: { id: string; name: string; path: string } | null }> = ({ source }) => {
  const { state } = useWorkbench();
  const [tasks, setTasks] = useState<ModelingTask[]>([]);
  const [target, setTarget] = useState('models/blockbench/new_model.bbmodel');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [loaded, setLoaded] = useState(false);
  const [retry, setRetry] = useState<{ taskId: string; key: string } | null>(null);
  const writable = isNativeHostPresent() && state.workbench?.permission.profile !== 'read_only';

  const refresh = async () => {
    const workspaceId = state.workbench?.workspace.id;
    if (!workspaceId) throw new Error('请先打开工作区。');
    const result = await coreBridge.sendQuery<{ tasks: ModelingTask[] }>({ messageType: 'query', schemaVersion: '1.0',
      requestId: safeRandomUUID(), workspaceId, operation: 'list_blockbench_tasks', payload: {} });
    if (result.status !== 'succeeded') throw new Error(t(result.diagnostics[0]?.message) || '任务列表读取失败。');
    setTasks(result.data?.tasks ?? []);
    setLoaded(true);
  };
  const run = async (operation: CommandOperation, payload: Record<string, unknown>) => {
    const workspace = state.workbench?.workspace;
    if (!workspace) return false;
    setBusy(true);
    try {
      const result = await coreBridge.sendCommand({ messageType: 'command', schemaVersion: '1.0', requestId: safeRandomUUID(),
        workspaceId: workspace.id, expectedRevision: workspace.revision, operation,
        payload: { ...payload, clientMutationId: safeRandomUUID() } });
      if (result.status !== 'completed' && result.status !== 'committed') {
        const diagnostic = result.diagnostics[0];
        throw new Error(`${t(diagnostic?.message) || '任务未完成。'}${diagnostic ? `（${diagnostic.code}）` : ''}`);
      }
      setRetry(null);
      setMessage(operation === 'finish_blockbench_task' ? '候选已保存；源资产未被覆盖，尚未导出或回导到游戏。'
        : operation === 'cancel_blockbench_task' ? '任务已取消，副本和候选文件均已保留。'
        : operation === 'import_blockbench_task' ? '编辑源、游戏模型和贴图已回导，资产索引已刷新；请继续关联元素并构建测试。'
        : operation === 'recover_blockbench_import' ? '中断的回导已恢复，候选仍可重新预览。'
        : operation === 'bind_blockbench_model' ? '模型已关联到元素；请构建并在游戏中检查。'
        : '编辑副本已准备。复制路径，在 Blockbench 中打开该副本并保存。');
      await refresh();
      return true;
    } catch (error) { setMessage(error instanceof Error ? error.message : '任务操作失败。'); return false; }
    finally { setBusy(false); }
  };
  const begin = (assetId?: string) => {
    const key = assetId ?? target;
    const taskId = retry?.key === key ? retry.taskId : safeRandomUUID();
    setRetry({ taskId, key });
    void run('begin_blockbench_task', { taskId, ...(assetId ? { assetId } : { targetRelativePath: target }) });
  };

  return <details name="blockbench-tools" className="blockbench-setup" data-testid="blockbench-tasks">
    <summary>建模任务 · 编辑副本与保存候选</summary>
    <div className="blockbench-setup-content">
      <p>当前支持 Java 方块／物品模型。先编辑副本并保存候选，再预览导出的游戏模型与贴图、回导并关联元素；无需关闭编辑器。</p>
      {!isNativeHostPresent() && <p>预览模式仅展示任务入口，请在桌面产品中创建任务。</p>}
      <p role="status">{message}</p>
      <div className="blockbench-setup-actions">
        <label>新模型目标路径<input aria-label="新模型目标路径" value={target} disabled={busy} onChange={event => setTarget(event.target.value)} /></label>
        <button className="btn-secondary" type="button" disabled={busy || !writable} onClick={() => begin()}>新建建模副本</button>
        {source?.path.endsWith('.bbmodel') && <button className="btn-secondary" type="button" disabled={busy || !writable}
          onClick={() => begin(source.id)}>从所选模型创建副本</button>}
        <button className="btn-secondary" type="button" disabled={busy} onClick={() => {
          setBusy(true); void refresh().then(() => setMessage('已刷新磁盘保存状态。')).catch(error => setMessage(error.message)).finally(() => setBusy(false));
        }}>刷新任务与保存状态</button>
      </div>
      {loaded && tasks.length === 0 && <p>当前没有建模任务。</p>}
      {tasks.map(task => <article className="blockbench-task" key={task.taskId}>
        <strong>{task.targetRelativePath} · {labels[task.state]}</strong>
        {task.sourceChanged && <p role="alert">源文件或目标已发生变化，请处理冲突后重新创建任务；当前副本仍被保留。</p>}
        {task.candidateChanged && <p role="alert">候选或编辑副本在完成后发生变化，请重新建模并核验，勿将此记录当作有效回导结果。</p>}
        <p>{task.hasSavedChanges ? '磁盘副本已有保存变化' : '未检测到副本内容变化'} · 恢复点：{task.recoveryPointId}</p>
        <code>{task.candidatePath ?? task.editPath}</code>
        <div className="blockbench-setup-actions">
          <button className="btn-secondary" type="button" onClick={() => {
            void mcpRuntimeBridge.copyText(task.candidatePath ?? task.editPath).then(() => setMessage('已复制文件路径。')).catch(() => setMessage('复制失败，请手动复制路径。'));
          }}>复制文件路径</button>
          <button className="btn-secondary" type="button" disabled={!writable || busy || task.state !== 'editing' || task.sourceChanged || !task.editSha256}
            onClick={() => void run('finish_blockbench_task', { taskId: task.taskId, savedSha256: task.editSha256 })}>确认磁盘保存并生成候选</button>
          <button className="btn-secondary" type="button" disabled={!writable || busy || !['editing', 'ready_to_import'].includes(task.state)}
            onClick={() => void run('cancel_blockbench_task', { taskId: task.taskId })}>取消任务并保留文件</button>
        </div>
        <BlockbenchImportPanel taskId={task.taskId} taskState={task.state} writable={writable} busy={busy} files={task.importFiles} run={run} />
      </article>)}
    </div>
  </details>;
};
