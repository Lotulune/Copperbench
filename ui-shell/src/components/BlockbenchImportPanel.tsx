import { tr } from '../i18n/locale';
import { safeRandomUUID } from '../bridge/JcefCoreBridge';
import React, { useState } from 'react';
import { coreBridge } from '../bridge';
import { useWorkbench } from '../context/WorkbenchContext';
import type { CommandOperation } from '../types/contract';
import { t } from '../i18n';

type Mapping = { sourceRelativePath: string; targetRelativePath: string };
type Preview = { planToken: string; canApply: boolean; requiresReplacementConfirmation: boolean; items: Array<{ targetRelativePath: string; conflict: string; sourceSha256: string }> };

export const BlockbenchImportPanel: React.FC<{
  taskId: string; taskState: string; writable: boolean; busy: boolean;
  files?: Array<{ targetRelativePath: string }>;
  run: (operation: CommandOperation, payload: Record<string, unknown>) => Promise<boolean>;
}> = ({ taskId, taskState, writable, busy, files, run }) => {
  const { state } = useWorkbench();
  const [outputs, setOutputs] = useState<Mapping[]>([{ sourceRelativePath: '', targetRelativePath: '' }]);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [elementId, setElementId] = useState('');
  const [resource, setResource] = useState('');
  const models = (files ?? []).flatMap(file => {
    const match = file.targetRelativePath.match(/^src\/main\/resources\/assets\/([^/]+)\/models\/(.+)\.json$/);
    return match ? [`${match[1]}:${match[2]}`] : [];
  });
  const edit = (index: number, field: keyof Mapping, value: string) => {
    setOutputs(outputs.map((row, position) => position === index ? { ...row, [field]: value } : row));
    setPreview(null); setConfirm(false);
  };
  const inspect = async () => {
    if (!state.workbench) return;
    setChecking(true); setPreview(null); setMessage(tr("正在验证导出文件及资源引用…"));
    try {
      const result = await coreBridge.sendQuery<Preview>({ messageType: 'query', schemaVersion: '1.0',
        requestId: safeRandomUUID(), workspaceId: state.workbench.workspace.id,
        operation: 'preview_blockbench_import', payload: { taskId, outputs } });
      if (result.status !== 'succeeded' || !result.data) throw new Error(tr("{0}（{1}）", [t(result.diagnostics[0]?.message) || tr("回导预览失败"), result.diagnostics[0]?.code ?? tr("未知错误")]));
      setPreview(result.data); setConfirm(false); setMessage(tr("请核对下方文件，再应用回导。"));
    } catch (error) { setMessage(error instanceof Error ? error.message : tr("回导预览失败。")); }
    finally { setChecking(false); }
  };

  if (taskState === 'importing') return <div>
    <p role="alert">{tr("上一次回导未完成。恢复只处理本任务记录的文件；遇到其他修改会停止并保留备份。")}</p>
    <button className="btn-secondary" type="button" disabled={!writable || busy}
      onClick={() => void run('recover_blockbench_import', { taskId })}>{tr("恢复中断的回导")}</button>
  </div>;
  if (taskState === 'imported') return <div>
    <p>{tr("文件已回导。选择元素并绑定模型后，再构建和测试游戏效果。")}</p>
    <div className="blockbench-setup-actions">
      <label>{tr("关联元素")}<select value={elementId} onChange={event => setElementId(event.target.value)} disabled={busy}>
        <option value="">{tr("请选择方块或物品")}</option>
        {state.elements.filter(element => element.type === 'block' || element.type === 'item').map(element => <option key={element.id} value={element.id}>{element.name}</option>)}
      </select></label>
      <label>{tr("导入的游戏模型")}<select value={resource} onChange={event => setResource(event.target.value)} disabled={busy}>
        <option value="">{tr("请选择模型")}</option>{models.map(model => <option key={model} value={model}>{model}</option>)}
      </select></label>
      <button className="btn-secondary" type="button" disabled={!writable || busy || !elementId || !resource}
        onClick={() => void run('bind_blockbench_model', { taskId, elementId, modelResource: resource })}>{tr("关联所选模型")}</button>
    </div>
  </div>;
  if (taskState !== 'ready_to_import') return null;
  return <details className="modeling-import-review">
    <summary>{tr("回导导出的模型与贴图")}</summary>
    <p>{tr("先在 Blockbench 导出游戏模型 JSON 和 PNG 到任务编辑目录。填写导出文件与工作区目标的对应关系；编辑源候选会自动加入。")}</p>
    <p>{tr("模型建议存入命名空间下的 models/custom 目录，避免与生成器的方块／物品模型同名。")}</p>
    <p role="status">{message}</p>
    {outputs.map((row, index) => <div className="blockbench-setup-actions" key={index}>
      <label>{tr("编辑目录内的导出文件")}<input value={row.sourceRelativePath} placeholder={tr("例如 export/lamp.json")} disabled={checking || busy}
        onChange={event => edit(index, 'sourceRelativePath', event.target.value)} /></label>
      <label>{tr("工作区目标路径")}<input value={row.targetRelativePath} placeholder={tr("例如 src/main/resources/assets/模组标识/models/custom/lamp.json")} disabled={checking || busy}
        onChange={event => edit(index, 'targetRelativePath', event.target.value)} /></label>
      <button className="btn-secondary" type="button" disabled={outputs.length === 1 || checking || busy}
        onClick={() => { setOutputs(outputs.filter((_, position) => position !== index)); setPreview(null); }}>{tr("移除此映射")}</button>
    </div>)}
    <div className="blockbench-setup-actions">
      <button className="btn-secondary" type="button" disabled={checking || busy || outputs.length >= 63}
        onClick={() => { setOutputs([...outputs, { sourceRelativePath: '', targetRelativePath: '' }]); setPreview(null); }}>{tr("添加导出文件")}</button>
      <button className="btn-secondary" type="button" disabled={checking || busy || outputs.some(row => !row.sourceRelativePath || !row.targetRelativePath)} onClick={() => void inspect()}>{tr("预览回导")}</button>
    </div>
    {preview && <div>
      <ul>{preview.items.map(item => <li key={item.targetRelativePath}>{item.targetRelativePath} · {item.conflict === 'CREATE' ? tr("新增") : item.conflict === 'REPLACE' ? tr("替换") : tr("内容相同")}</li>)}</ul>
      {preview.requiresReplacementConfirmation && <label><input type="checkbox" checked={confirm} onChange={event => setConfirm(event.target.checked)} />{tr("我确认替换上述文件，保留回导前恢复点")}</label>}
      <div className="blockbench-setup-actions"><button className="btn-primary" type="button" disabled={!writable || busy || !preview.canApply || (preview.requiresReplacementConfirmation && !confirm)}
        onClick={() => void run('import_blockbench_task', { taskId, planToken: preview.planToken, confirmReplace: confirm }).then(ok => { if (ok) setPreview(null); })}>{tr("应用回导")}</button></div>
    </div>}
  </details>;
};
