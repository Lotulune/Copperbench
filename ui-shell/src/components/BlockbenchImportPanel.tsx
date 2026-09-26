import { safeRandomUUID } from '../bridge/JcefCoreBridge';
import React, { useEffect, useState } from 'react';
import { coreBridge } from '../bridge';
import { useWorkbench } from '../context/WorkbenchContext';
import type { CommandOperation } from '../types/contract';
import { t } from '../i18n';

type Mapping = { sourceRelativePath: string; targetRelativePath: string };
type Preview = { planToken: string; canApply: boolean; outputs: Mapping[]; requiresReplacementConfirmation: boolean; items: Array<{ targetRelativePath: string; conflict: string; sourceSha256: string }> };

export const BlockbenchImportPanel: React.FC<{
  taskId: string; taskState: string; writable: boolean; busy: boolean;
  files?: Array<{ targetRelativePath: string }>;
  targetElementId?: string;
  binding?: { state: string; modelResource?: string };
  invalidCandidate?: boolean;
  run: (operation: CommandOperation, payload: Record<string, unknown>) => Promise<boolean>;
}> = ({ taskId, taskState, writable, busy, files, run, targetElementId, binding, invalidCandidate }) => {
  const { state, selectedElementId, buildWorkspace, runClient } = useWorkbench();
  const [outputs, setOutputs] = useState<Mapping[]>([{ sourceRelativePath: '', targetRelativePath: '' }]);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [elementId, setElementId] = useState(targetElementId ?? selectedElementId ?? '');
  const [resource, setResource] = useState('');
  const [manualMapping, setManualMapping] = useState(false);
  const [bindingConfirmed, setBindingConfirmed] = useState(false);
  const [starting, setStarting] = useState(false);
  const [startedTaskId, setStartedTaskId] = useState<string | null>(null);
  const target = state.elements.find(element => element.id === elementId);
  const restricted = binding?.state === 'manual' || binding?.state === 'element_missing' || target?.ownership === 'manual';
  const models = (files ?? []).flatMap(file => {
    const match = file.targetRelativePath.match(/^src\/main\/resources\/assets\/([^/]+)\/models\/(.+)\.json$/);
    return match ? [`${match[1]}:${match[2]}`] : [];
  });
  const modelKey = models.join('|');
  useEffect(() => {
    setResource(current => models.includes(current) ? current : models.length === 1 ? models[0] : '');
  }, [modelKey]);
  useEffect(() => {
    setPreview(null); setConfirm(false);
  }, [state.workbench?.workspace.revision, invalidCandidate]);
  useEffect(() => { setMessage(''); }, [taskState]);
  const bound = !restricted && (targetElementId ? binding?.state === 'bound' && binding.modelResource === resource : bindingConfirmed);
  const startVerification = async (client: boolean) => {
    setStarting(true);
    try {
      const result = await (client ? runClient() : buildWorkspace());
      if (result.status !== 'accepted' && result.status !== 'completed' && result.status !== 'committed')
        throw new Error(t(result.diagnostics[0]?.message) || '任务未启动，已导入文件和关联仍保留。');
      setStartedTaskId(result.task?.id ?? null);
      setMessage(client ? '客户端任务已提交；请在游戏内核验形状、贴图，并正常退出。' : '构建任务已提交；请等待任务终态，提交不代表构建成功。');
    } catch (error) { setMessage(error instanceof Error ? error.message : '任务启动失败。'); }
    finally { setStarting(false); }
  };
  const verificationTask = startedTaskId ? state.tasks[startedTaskId] : null;
  const edit = (index: number, field: keyof Mapping, value: string) => {
    setManualMapping(true);
    setOutputs(outputs.map((row, position) => position === index ? { ...row, [field]: value } : row));
    setPreview(null); setConfirm(false);
  };
  const inspect = async () => {
    if (!state.workbench) return;
    setChecking(true); setPreview(null); setMessage('正在验证导出文件及资源引用…');
    try {
      const result = await coreBridge.sendQuery<Preview>({ messageType: 'query', schemaVersion: '1.0',
        requestId: safeRandomUUID(), workspaceId: state.workbench.workspace.id,
        operation: 'preview_blockbench_import', payload: !manualMapping
          ? { taskId, elementId } : { taskId, outputs } });
      if (result.status !== 'succeeded' || !result.data) throw new Error(`${t(result.diagnostics[0]?.message) || '回导预览失败'}（${result.diagnostics[0]?.code ?? '未知错误'}）`);
      setPreview(result.data); if (result.data.outputs) setOutputs(result.data.outputs);
      setConfirm(false); setMessage('请核对下方文件，再应用回导。');
    } catch (error) { setMessage(error instanceof Error ? error.message : '回导预览失败。'); }
    finally { setChecking(false); }
  };

  if (taskState === 'importing') return <div>
    <p role="alert">上一次回导未完成。恢复只处理本任务记录的文件；遇到其他修改会停止并保留备份。</p>
    <button className="btn-secondary" type="button" disabled={!writable || busy}
      onClick={() => void run('recover_blockbench_import', { taskId })}>恢复中断的回导</button>
  </div>;
  if (taskState === 'imported') return <div>
    <p>{bound ? '文件已回导，元素定义已关联。下一步：构建并在游戏内验证。' : '文件已回导，关联尚未确认。下一步：关联所选模型；失败时可直接重试，已导入文件保留。'}</p>
    <p role="status">{message}</p>
    {restricted && <p role="alert">目标元素已删除或由手写源码管理，无法自动关联；已导入文件保留，请在源码中显式注册模型。</p>}
    {verificationTask && <p>本次验证任务：{verificationTask.kind} · {t(verificationTask.stage)}。游戏视觉效果仍需人工确认。</p>}
    <div className="blockbench-setup-actions">
      <label>关联元素<select value={elementId} onChange={event => { setElementId(event.target.value); setBindingConfirmed(false); }} disabled={busy || Boolean(targetElementId)}>
        <option value="">请选择方块或物品</option>
        {state.elements.filter(element => (element.type === 'block' || element.type === 'item') && element.ownership !== 'manual').map(element => <option key={element.id} value={element.id}>{element.name}</option>)}
      </select></label>
      <label>导入的游戏模型<select value={resource} onChange={event => { setResource(event.target.value); setBindingConfirmed(false); }} disabled={busy}>
        <option value="">请选择模型</option>{models.map(model => <option key={model} value={model}>{model}</option>)}
      </select></label>
      <button className="btn-secondary" type="button" disabled={!writable || busy || restricted || !elementId || !resource}
        onClick={() => void run('bind_blockbench_model', { taskId, elementId, modelResource: resource }).then(ok => setBindingConfirmed(ok))}>关联所选模型</button>
      <button className="btn-secondary" type="button" disabled={!writable || busy || starting || !bound} onClick={() => void startVerification(false)}>构建工作区</button>
      <button className="btn-secondary" type="button" disabled={!writable || busy || starting || !bound} onClick={() => void startVerification(true)}>启动客户端验证</button>
    </div>
  </div>;
  if (taskState !== 'ready_to_import') return null;
  return <details className="modeling-import-review">
    <summary>识别游戏导出并预览回导</summary>
    <label>目标元素<select value={elementId} disabled={busy || checking || Boolean(targetElementId)} onChange={event => {
      setElementId(event.target.value); setPreview(null); setConfirm(false);
      if (!manualMapping) setOutputs([{ sourceRelativePath: '', targetRelativePath: '' }]);
    }}>
      <option value="">请选择方块或物品</option>
      {state.elements.filter(element => (element.type === 'block' || element.type === 'item') && element.ownership !== 'manual')
        .map(element => <option key={element.id} value={element.id}>{element.name}</option>)}
    </select></label>
    {restricted && <p role="alert">目标元素由手写源码管理或已删除，不能自动关联。请从资产页单独处理资源，并在源码中显式注册。</p>}
    <p>在本任务编辑目录导出一个游戏 JSON 和引用的 PNG。点击识别后自动建议路径，无需手输；修改映射后按填写内容验证，缺失或多候选会停止选择。</p>
    <p>模型建议存入命名空间下的 models/custom 目录，避免与生成器的方块／物品模型同名。</p>
    <p role="status">{message}</p>
    {outputs.map((row, index) => <div className="blockbench-setup-actions" key={index}>
      <label>编辑目录内的导出文件<input value={row.sourceRelativePath} placeholder="例如 export/lamp.json" disabled={checking || busy}
        onChange={event => edit(index, 'sourceRelativePath', event.target.value)} /></label>
      <label>工作区目标路径<input value={row.targetRelativePath} placeholder="例如 src/main/resources/assets/模组标识/models/custom/lamp.json" disabled={checking || busy}
        onChange={event => edit(index, 'targetRelativePath', event.target.value)} /></label>
      <button className="btn-secondary" type="button" disabled={outputs.length === 1 || checking || busy}
        onClick={() => { setOutputs(outputs.filter((_, position) => position !== index)); setManualMapping(true); setPreview(null); }}>移除此映射</button>
    </div>)}
    <div className="blockbench-setup-actions">
      <button className="btn-secondary" type="button" disabled={checking || busy || outputs.length >= 63}
        onClick={() => { setOutputs([...outputs, { sourceRelativePath: '', targetRelativePath: '' }]); setManualMapping(true); setPreview(null); }}>添加导出文件</button>
      <button className="btn-secondary" type="button" disabled={checking || busy} onClick={() => {
        setManualMapping(false); setOutputs([{ sourceRelativePath: '', targetRelativePath: '' }]); setPreview(null); setConfirm(false);
      }}>恢复自动识别</button>
      <button className="btn-secondary" type="button" disabled={checking || busy || restricted || invalidCandidate || (!manualMapping
        ? !elementId : outputs.some(row => !row.sourceRelativePath || !row.targetRelativePath))} onClick={() => void inspect()}>识别并预览回导</button>
    </div>
    {preview && <div>
      <ul>{preview.items.map(item => <li key={item.targetRelativePath}>{item.targetRelativePath} · {item.conflict === 'CREATE' ? '新增' : item.conflict === 'REPLACE' ? '替换' : '内容相同'}</li>)}</ul>
      {preview.requiresReplacementConfirmation && <label><input type="checkbox" checked={confirm} onChange={event => setConfirm(event.target.checked)} />我确认替换上述文件，保留回导前恢复点</label>}
      <div className="blockbench-setup-actions"><button className="btn-primary" type="button" disabled={!writable || busy || invalidCandidate || restricted || !preview.canApply || (preview.requiresReplacementConfirmation && !confirm)}
        onClick={() => void run('import_blockbench_task', { taskId, planToken: preview.planToken, confirmReplace: confirm }).then(ok => { if (ok) setPreview(null); })}>应用回导</button></div>
    </div>}
  </details>;
};
