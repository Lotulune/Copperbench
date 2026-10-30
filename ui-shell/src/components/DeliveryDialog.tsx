import { useEffect, useRef, useState } from 'react';
import { FileArchive, FolderOpen, X } from 'lucide-react';
import { isNativeHostPresent, safeRandomUUID } from '../bridge';
import { workspaceOpenBridge } from '../bridge/workspaceOpenBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { hasUnsavedDrafts } from '../hooks/unsavedDraftGuard';
import { ExportCompatibility } from './ExportCompatibility';
import { renderUiMessage, t, uiMessage, uiText, type UiMessage } from '../i18n';
import { useFirstCreation } from '../onboarding/FirstCreationContext';
import { defaultDeliveryPath, firstCreationConfirmationKey, firstCreationElement, firstCreationTask, firstCreationInputsConfirmed,
  isRelativeDeliveryPath } from '../onboarding/firstCreationModel';

export function DeliveryDialog() {
  const { state, exportWorkspace, setActiveTaskId, setIsTaskDrawerOpen } = useWorkbench();
  const { deliveryOpen, closeDelivery, deliveryReference, workspaceRoot, referencesReady, recordTask, currentBinding, observations,
    inputObservation, inputObservationPending, refreshReferences } = useFirstCreation();
  const workspace = state.workbench?.workspace;
  const native = isNativeHostPresent();
  const [output, setOutput] = useState('');
  const [busy, setBusy] = useState(false);
  const [openingFolder, setOpeningFolder] = useState(false);
  const [openingVerifiedFolder, setOpeningVerifiedFolder] = useState<string | null>(null);
  const [messages, setMessages] = useState<UiMessage[]>([]);
  const [uncertain, setUncertain] = useState(false);
  const submitting = useRef(false);
  const currentWorkspaceId = useRef(workspace?.id);
  currentWorkspaceId.current = workspace?.id;
  const dialogRef = useDialogA11y(deliveryOpen, closeDelivery);
  useEffect(() => {
    if (!workspace) return;
    setOutput(defaultDeliveryPath(workspace.name, workspace.kind, safeRandomUUID(), workspace.generator.id));
    setMessages([]); setUncertain(false);
  }, [workspace?.id]);
  if (!deliveryOpen) return null;
  const exported = firstCreationTask(deliveryReference, state.tasks, workspace?.revision ?? 0, inputObservation);
  const active = exported.status === 'queued' || exported.status === 'running';
  const acceptanceTasks = Object.values(state.tasks).filter(task => task.kind === 'run_gametest' && task.state === 'succeeded'
    && !task.resultObservation && task.verification?.status === 'passed' && task.verification.mode === 'packaged_jar');
  const exactExports = Object.values(state.tasks).filter(task => task.kind === 'export' && task.state === 'succeeded' && task.verifiedExport && !task.resultObservation);
  const elementFact = currentBinding ? firstCreationElement(currentBinding, state.elements) : null;
  const confirmationKey = workspace && currentBinding && elementFact?.status === 'ready'
    ? firstCreationConfirmationKey(workspace, elementFact.element, currentBinding, inputObservation) : '';
  const userConfirmed = !inputObservationPending && !!workspace && !!currentBinding
    && firstCreationInputsConfirmed(currentBinding, state.tasks, workspace.revision, inputObservation)
    && !!confirmationKey && observations.key === confirmationKey && observations.placed && observations.persisted;
  const showTask = (id: string) => { closeDelivery(); setActiveTaskId(id); setIsTaskDrawerOpen(true); };
  const submit = async () => {
    if (!native || !referencesReady || !workspace || submitting.current || uncertain || !isRelativeDeliveryPath(output)) return;
    const workspaceId = workspace.id, revision = workspace.revision, requestedOutput = output.trim().replaceAll('\\', '/');
    submitting.current = true; setBusy(true); setMessages([]);
    try {
      const result = await exportWorkspace(requestedOutput);
      if (result.workspaceId !== workspaceId) throw new Error('The response belongs to another workspace.');
      recordTask(result, 'export', workspaceId, revision, requestedOutput);
      if (currentWorkspaceId.current === workspaceId && !result.task?.id) setMessages(result.diagnostics.length
        ? result.diagnostics.map(diagnostic => diagnostic.message)
        : [uiMessage('导出任务未确认启动。请检查任务状态。', 'Export task startup was not confirmed. Check the task state.')]);
    } catch (error) {
      if (currentWorkspaceId.current === workspaceId) {
        setUncertain(true);
        setMessages([uiMessage('导出结果待确认。请先查看原任务，避免重复写入同一位置。', 'The export result needs confirmation. Inspect the original task before writing to the same location again.'),
          error instanceof Error ? error.message : uiMessage('与 Core 的连接中断。', 'The connection to Core was interrupted.')]);
      }
    } finally { submitting.current = false; setBusy(false); }
  };
  const openFolder = async () => {
    if (openingFolder) return;
    setOpeningFolder(true);
    try { await workspaceOpenBridge.openBuildFolder(); }
    catch (error) { setMessages([error instanceof Error ? error.message : uiMessage('无法打开输出文件夹。请在文件管理器中查看下列路径。', 'Could not open the output folder. Use the file manager to inspect the path below.')]); }
    finally { setOpeningFolder(false); }
  };

  return <div className="modal-overlay"><div ref={dialogRef} className="modal-card first-creation-guide delivery-dialog" role="dialog" aria-modal="true" aria-labelledby="delivery-title" data-testid="delivery-dialog">
    <header className="modal-header"><h2 id="delivery-title"><FileArchive size={18} aria-hidden="true" />{uiText('交付作品', 'Deliver creation')}</h2>
      <button type="button" aria-label={uiText('关闭交付窗口', 'Close delivery dialog')} onClick={closeDelivery}><X size={18} aria-hidden="true" /></button></header>
    <div className="modal-body first-creation-content">
      {!native && <p role="status" className="first-creation-notice">{uiText('请在桌面应用中导出本机工作区。', 'Use the desktop app to export a local workspace.')}</p>}
      {workspace && <dl className="delivery-facts" data-testid="delivery-compatibility"><div><dt>{uiText('当前工程', 'Current project')}</dt><dd>{workspace.name}</dd></div>
        <div><dt>{uiText('加载器', 'Loader')}</dt><dd>{workspace.generator.loader}</dd></div>
        <div><dt>Minecraft</dt><dd>{workspace.generator.minecraftVersion}</dd></div>
        <div><dt>{uiText('游戏观察', 'Gameplay observations')}</dt><dd data-testid="delivery-user-observation">{userConfirmed ? uiText('本次会话由你确认放置与重进世界；自动验收另见报告。', 'You confirmed placement and world reopening in this session; automated acceptance has a separate report.') : uiText('本次会话尚无完整用户确认。构建与启动任务不证明游戏行为。', 'No complete user confirmation in this session. Build and launch tasks do not establish gameplay behavior.')}</dd></div></dl>}
      {messages.length > 0 && <div role="alert" className="first-creation-notice">{messages.map((message, index) => <p key={index}>{renderUiMessage(message)}</p>)}</div>}
      <section aria-labelledby="ordinary-export-title"><h3 id="ordinary-export-title">{uiText('普通导出', 'Ordinary export')}</h3>
        {hasUnsavedDrafts(['simple-chat']) && <p className="first-creation-notice" data-testid="delivery-unsaved">{uiText('有未保存输入。请先应用更改，或明确使用已保存内容导出。', 'There is unsaved input. Apply changes first, or export the saved content deliberately.')}</p>}
        <label className="delivery-output-label" htmlFor="delivery-output">{uiText('工作区内的输出路径', 'Output path inside the workspace')}</label>
        <input id="delivery-output" data-testid="delivery-output" value={output} onChange={event => setOutput(event.target.value)} aria-describedby="delivery-output-help" aria-invalid={output !== '' && !isRelativeDeliveryPath(output)} />
        <p id="delivery-output-help" className={output !== '' && !isRelativeDeliveryPath(output) ? 'delivery-output-error' : 'sr-only'}>{uiText('使用工作区内的 .jar 或 .zip 相对路径，避开 src、elements、gradle、lib、libs、plugins 目录。仅可替换 Copperbench 之前导出且内容未变的文件；其他已有文件请改用新路径。', 'Use a workspace-relative .jar or .zip path outside src, elements, gradle, lib, libs and plugins. An existing file can be replaced only if it is an unchanged previous Copperbench export; otherwise, use a new path.')}</p>
        <div className="first-creation-buttons delivery-export-actions"><button type="button" className="btn-primary" data-testid="delivery-export" disabled={!native || !referencesReady || !workspace || busy || active || uncertain || !isRelativeDeliveryPath(output)} aria-busy={busy} onClick={() => void submit()}>{busy ? uiText('正在提交…', 'Submitting…') : uiText('重新构建并导出', 'Rebuild and export')}</button>
          <div className="delivery-secondary-actions"><button type="button" className="btn-secondary" data-testid="delivery-new-path" disabled={!workspace || busy || active} onClick={() => { if (workspace) setOutput(defaultDeliveryPath(workspace.name, workspace.kind, safeRandomUUID(), workspace.generator.id)); }}>{uiText('使用新的交付路径', 'Use a new delivery path')}</button>
            <button type="button" className="btn-secondary" data-testid="delivery-open-folder" disabled={!workspaceOpenBridge.buildFolderAvailable || openingFolder} onClick={() => void openFolder()}
              title={uiText('默认交付位于 build/libs 下的 copperbench-delivery 目录；自定义位置请按上方完整路径查找。', 'Default deliveries are in a copperbench-delivery directory under build/libs; use the full path above for a custom location.')}><FolderOpen size={14} aria-hidden="true" />{uiText('打开构建输出文件夹', 'Open build output folder')}</button></div>
        </div>
        {uncertain && <button type="button" className="btn-secondary" onClick={() => { setUncertain(false); setMessages([]); }}>{uiText('已检查原任务，返回导出表单', 'Original task inspected; return to export form')}</button>}
        {deliveryReference && <div className="delivery-last-export" data-testid="delivery-export-fact" role="status">
          <strong>{exported.status === 'succeeded' ? uiText('普通导出任务成功', 'Ordinary export task succeeded')
            : exported.status === 'failed' ? uiText('导出任务失败', 'Export task failed')
              : exported.status === 'cancelled' ? uiText('导出任务已取消', 'Export task cancelled')
                : active ? t(exported.task?.stage) : uiText('导出状态待确认', 'Export state needs confirmation')}</strong>
          {workspaceRoot && deliveryReference.output && <code data-testid="delivery-requested-path">{workspaceRoot.replaceAll('\\', '/').replace(/\/+$/, '')}/{deliveryReference.output}</code>}
          {!workspaceRoot && deliveryReference.output && <code>{deliveryReference.output}</code>}
          {(exported.freshness === 'older_revision' || exported.freshness === 'changed_inputs') && <p data-testid="delivery-stale-input">{uiText('此任务对应较早的已保存输入。请重新构建并导出最新修改。', 'This task used earlier saved inputs. Rebuild and export the latest changes.')}</p>}
          {exported.freshness === 'changed_during_task' && <p data-testid="delivery-stale-input">{uiText('导出期间输入发生变化。请核对修改后重新构建并导出。', 'Inputs changed during export. Review the changes, then rebuild and export.')}</p>}
          {exported.status === 'succeeded' && exported.freshness === 'unknown' && <p data-testid="delivery-unknown-input">{uiText('此任务的输入状态待确认。重读输入；旧任务没有输入记录时，请重新导出。', 'This task’s input state needs confirmation. Refresh inputs; export again if an older task has no input record.')}</p>}
          {inputObservation?.tasks[deliveryReference.id]?.detail && <p data-testid="delivery-input-unavailable"><code>{inputObservation.tasks[deliveryReference.id].detail}</code></p>}
          <button type="button" className="btn-secondary" data-testid="delivery-refresh-inputs" disabled={inputObservationPending} onClick={refreshReferences}>{uiText('重读输入与原任务', 'Refresh inputs and original tasks')}</button>
          {inputObservation && <time dateTime={inputObservation.observedAt}>{inputObservation.observedAt}</time>}
          <button type="button" className="btn-secondary" onClick={() => showTask(deliveryReference.id)}>{uiText('查看原导出任务与日志', 'View original export task and logs')}</button>
        </div>}
      </section>
      <section aria-labelledby="exact-export-title"><h3 id="exact-export-title">{uiText('已有验收的精确导出', 'Exact export with recorded acceptance')}</h3>
        <details className="delivery-explainer"><summary>{uiText('什么是精确导出', 'About exact export')}</summary><p>{uiText('打开已有的打包 JAR 验收任务，使用原任务中的导出入口。它核对被测 JAR、报告与当前输入；历史输入需要明确选择，不会重建另一个 JAR 沿用通过标识。', 'Open an existing packaged-JAR acceptance task and use its export action. It checks the tested JAR, report and current input. Historical input requires an explicit choice; it does not rebuild another JAR and reuse a passed result.')}</p></details>
        {acceptanceTasks.length ? <ul className="delivery-task-list">{acceptanceTasks.map(task => <li key={task.id}><code>{task.id}</code><span>{task.completedAt}</span>
          <button type="button" className="btn-secondary" disabled={!native} onClick={() => showTask(task.id)}>{uiText('打开原验收任务', 'Open original acceptance task')}</button></li>)}</ul>
          : <p data-testid="delivery-no-acceptance">{uiText('当前任务记录中没有已通过的打包 JAR 验收；普通导出仍可使用。', 'Current task records contain no passed packaged-JAR acceptance; ordinary export remains available.')}</p>}
        {exactExports.map(task => <div key={task.id} className="delivery-last-export" data-testid="delivery-verified-export"><strong>{task.verifiedExport!.status === 'passed_historical_input'
          ? uiText('历史已验收产物导出回执', 'Receipt for a previously accepted artifact') : uiText('导出时输入匹配验收的产物回执', 'Receipt for an artifact whose input matched acceptance at export time')}</strong>
          <code>{task.verifiedExport!.exportDirectory}</code><code>{`SHA-256 ${task.verifiedExport!.artifactSha256}`}</code>
          <ExportCompatibility environment={task.verifiedExport!.environment} />
          <button type="button" className="btn-secondary" data-testid="delivery-open-verified-folder" disabled={!workspaceOpenBridge.verifiedExportFolderAvailable || openingVerifiedFolder !== null} onClick={() => {
            setOpeningVerifiedFolder(task.id);
            void workspaceOpenBridge.openVerifiedExportFolder(task.id).catch(error => setMessages([error instanceof Error ? error.message : uiMessage('无法打开此导出目录，请查看原任务回执。', 'Could not open this export directory. Inspect the original task receipt.')])).finally(() => setOpeningVerifiedFolder(null));
          }}><FolderOpen size={14} aria-hidden="true" />{uiText('打开此次精确导出目录', 'Open this exact export directory')}</button>
          <button type="button" className="btn-secondary" onClick={() => showTask(task.id)}>{uiText('查看原回执', 'View original receipt')}</button></div>)}
      </section>
    </div>
  </div></div>;
}
