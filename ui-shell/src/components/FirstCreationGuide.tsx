import React, { useEffect, useRef, useState } from 'react';
import { ArrowRight, Box, CheckCircle2, Flag, FolderOpen, Hammer, Play, X } from 'lucide-react';
import { isNativeHostPresent } from '../bridge';
import { useWorkbench } from '../context/WorkbenchContext';
import { useWorkbenchMode } from '../context/WorkbenchModeContext';
import { useWorkbenchTour } from '../onboarding/WorkbenchTour';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { elementEditorDraftKey, getElementEditorDraft } from '../hooks/elementEditorDrafts';
import { hasUnsavedDrafts } from '../hooks/unsavedDraftGuard';
import { renderUiMessage, t, uiMessage, uiText, type UiMessage } from '../i18n';
import { useFirstCreation } from '../onboarding/FirstCreationContext';
import { FIRST_CREATION_ELEMENT, FIRST_CREATION_GENERATOR, firstCreationConfirmationKey,
  firstCreationElement, firstCreationTask, firstCreationInputsConfirmed, hasSavedFirstCreationFields } from '../onboarding/firstCreationModel';
import type { CommandResult, ModElementEditorProjection } from '../types/contract';
import './firstCreation.css';

export function FirstCreationGuide() {
  const { mode } = useWorkbenchMode();
  const { openTour } = useWorkbenchTour();
  const { state, createModElement, buildWorkspace, runClient, getModElementEditor,
    setSelectedElementId, setActiveView, setActiveTaskId, setIsTaskDrawerOpen } = useWorkbench();
  const { guideOpen, closeGuide, pauseGuide, openGuide, tourActive, binding, referenceError, referencesReady, refreshReferences,
    bindElement, recordTask, openDelivery, inputObservation, inputObservationPending,
    observations: confirmation, setObservations: setConfirmation } = useFirstCreation();
  const native = isNativeHostPresent();
  const workspace = state.workbench?.workspace;
  const targetIsOpen = referencesReady && !!binding && binding.workspaceId === workspace?.id;
  const resolution = targetIsOpen && binding ? firstCreationElement(binding, state.elements) : null;
  const element = resolution?.status === 'ready' ? resolution.element : null;
  const [editor, setEditor] = useState<ModElementEditorProjection | null>(null);
  const [busy, setBusy] = useState(false);
  const [messages, setMessages] = useState<UiMessage[]>([]);
  const [uncertain, setUncertain] = useState(false);
  const [selectedStep, setSelectedStep] = useState<string | null>(null);
  const submitting = useRef(false);
  const currentWorkspaceId = useRef(workspace?.id);
  currentWorkspaceId.current = workspace?.id;
  const readEditor = useRef(getModElementEditor);
  readEditor.current = getModElementEditor;
  const dialogRef = useDialogA11y(guideOpen && targetIsOpen && mode === 'professional', closeGuide);

  useEffect(() => {
    if (mode !== 'simple' || !guideOpen || !targetIsOpen) return;
    closeGuide();
    openTour();
  }, [mode, guideOpen, targetIsOpen, closeGuide, openTour]);

  useEffect(() => {
    let current = true;
    setEditor(null);
    if (!guideOpen || !element || !native) return;
    void readEditor.current(element.id).then(result => {
      if (current) {
        setEditor(result);
        if (!result) setMessages([uiMessage('已保存的元素内容暂时无法读取。请重读状态或打开元素检查。', 'Saved element content is temporarily unavailable. Refresh the state or open the element to inspect it.')]);
      }
    }).catch(() => { if (current) setMessages([uiMessage('元素读取失败。原作品和草稿仍可从编辑器检查。', 'Could not read the element. Inspect the original creation and draft in the editor.')]); });
    return () => { current = false; };
  }, [guideOpen, element?.id, element?.updatedAt, workspace?.revision, native]);

  if (!binding || !targetIsOpen || mode === 'simple') return null;
  if (!guideOpen) return tourActive ? <div className="first-creation-resume" role="region" aria-label={uiText('创作引导', 'Creation tour')}>
    <button type="button" data-testid="continue-first-creation" onClick={() => openGuide()}>{uiText('继续引导', 'Continue tour')}<ArrowRight size={14} aria-hidden="true" /></button>
    <button type="button" onClick={closeGuide} aria-label={uiText('结束引导', 'End tour')}><X size={16} aria-hidden="true" /></button>
  </div> : null;

  const build = firstCreationTask(binding?.build, state.tasks, workspace?.revision ?? 0, inputObservation);
  const client = firstCreationTask(binding?.clients.at(-1), state.tasks, workspace?.revision ?? 0, inputObservation);
  const exported = firstCreationTask(binding?.export, state.tasks, workspace?.revision ?? 0, inputObservation);
  const saved = hasSavedFirstCreationFields(editor, element);
  const draft = !!(workspace && element && getElementEditorDraft(elementEditorDraftKey(workspace.id, element.id)));
  const pendingInput = draft || hasUnsavedDrafts(['simple-chat']);
  const clientRunning = Object.values(state.tasks).some(task => task.kind === 'run_client' && ['queued', 'running'].includes(task.state));
  const buildRunning = Object.values(state.tasks).some(task => task.kind === 'build' && ['queued', 'running'].includes(task.state));
  const confirmationKey = workspace && element && binding ? firstCreationConfirmationKey(workspace, element, binding, inputObservation) : '';
  const confirmed = !inputObservationPending && confirmation.key === confirmationKey ? confirmation : { placed: false, persisted: false };
  const canConfirm = native && saved && !!binding && !!workspace && !inputObservationPending
    && firstCreationInputsConfirmed(binding, state.tasks, workspace.revision, inputObservation) && !clientRunning;
  const inputDetails = [...new Set([binding?.build?.id, binding?.clients.at(-1)?.id, binding?.export?.id]
    .map(id => id ? inputObservation?.tasks[id]?.detail : undefined).filter((detail): detail is string => !!detail))];
  const showTask = (taskId: string) => { pauseGuide(); setActiveTaskId(taskId); setIsTaskDrawerOpen(true); };
  const openElement = (id: string) => { pauseGuide(); setSelectedElementId(id); setActiveView('elements'); };
  const action = async (kind: 'create' | 'build' | 'client', operation: () => Promise<CommandResult>) => {
    if (submitting.current || uncertain || !native || !workspace || !binding || !targetIsOpen) return;
    const workspaceId = workspace.id, revision = workspace.revision, file = binding.workspaceFile;
    submitting.current = true; setBusy(true); setMessages([]);
    try {
      const result = await operation();
      if (result.workspaceId !== workspaceId) throw new Error('The response belongs to another workspace.');
      if (kind === 'create' && result.status === 'committed') {
        const id = result.data?.element?.id ?? result.data?.elementId;
        if (id) bindElement(file, id);
        if (currentWorkspaceId.current === workspaceId) pauseGuide();
      } else if (result.task?.id) {
        recordTask(result, kind === 'client' ? 'client' : 'build', workspaceId, revision);
        if (currentWorkspaceId.current === workspaceId) pauseGuide();
      } else if (currentWorkspaceId.current === workspaceId) {
        setMessages(result.diagnostics.length ? result.diagnostics.map(diagnostic => diagnostic.message)
          : [uiMessage('操作未确认完成。请检查原工作区和任务状态。', 'Completion was not confirmed. Check the original workspace and task state.')]);
      }
    } catch (error) {
      if (currentWorkspaceId.current === workspaceId) { setUncertain(true); setMessages([
        uiMessage('操作结果待确认。请重读原对象或任务状态后继续，避免重复创建或启动。', 'The result needs confirmation. Refresh the original object or task state before continuing to avoid creating or starting it twice.'),
        error instanceof Error ? error.message : uiMessage('与 Core 的连接中断。', 'The connection to Core was interrupted.')
      ]); }
    } finally { submitting.current = false; setBusy(false); }
  };
  const taskLabel = (fact: ReturnType<typeof firstCreationTask>) => fact.status === 'succeeded'
    ? fact.freshness === 'older_revision' || fact.freshness === 'changed_inputs' ? uiText('任务成功 · 输入随后发生变化', 'Task succeeded · inputs changed afterwards')
      : fact.freshness === 'changed_during_task' ? uiText('任务成功 · 运行期间输入发生变化', 'Task succeeded · inputs changed during the task')
        : fact.freshness === 'current' ? uiText('任务成功 · 输入已核对', 'Task succeeded · inputs checked')
          : uiText('任务成功 · 输入状态待确认', 'Task succeeded · input state needs confirmation')
    : fact.status === 'failed' ? uiText('任务失败 · 查看日志', 'Task failed · view logs')
      : fact.status === 'cancelled' ? uiText('任务已取消', 'Task cancelled')
        : fact.status === 'queued' || fact.status === 'running' ? t(fact.task?.stage)
          : fact.status === 'unavailable' || fact.status === 'unconfirmed' ? uiText('任务状态待确认', 'Task state needs confirmation')
            : uiText('尚未运行', 'Not run yet');
  const currentStep = selectedStep ?? (binding.build ? 'build' : 'element');
  const steps = [
    ['workspace', uiText('工作区', 'Workspace')], ['element', uiText('方块', 'Block')],
    ['build', uiText('构建', 'Build')], ['client', uiText('运行', 'Run')],
    ['behavior', uiText('检查', 'Inspect')], ['delivery', uiText('导出', 'Export')]
  ];
  const step = (id: string, title: string, status: string, content: React.ReactNode) => currentStep !== id ? null : <li key={id} data-testid={`first-creation-step-${id}`}>
    <div className="first-creation-step-heading"><h3>{title}</h3><span>{status}</span></div>{content}</li>;

  return <div className="modal-overlay" data-testid="first-creation-overlay">
    <div className="modal-card first-creation-guide" ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="first-creation-title">
      <header className="modal-header"><h2 id="first-creation-title"><Flag size={18} aria-hidden="true" />{uiText('第一件作品', 'First creation')}</h2>
        <button type="button" data-testid="first-creation-close" aria-label={uiText('关闭首作指引', 'Close first-creation guide')} onClick={closeGuide}><X size={18} aria-hidden="true" /></button></header>
      <div className="modal-body first-creation-content">
        <nav className="first-creation-progress" aria-label={uiText('引导步骤', 'Tour steps')}>{steps.map(([id, label], index) =>
          <button type="button" key={id} data-testid={`first-creation-nav-${id}`} aria-current={currentStep === id ? 'step' : undefined}
            onClick={() => setSelectedStep(id)}><span aria-hidden="true">{index + 1}</span>{label}</button>)}</nav>
        {!native && <p className="first-creation-notice" role="status">{uiText('请在桌面应用中创建、运行和导出本机工作区。', 'Use the desktop app to create, run and export a local workspace.')}</p>}
        {referenceError && <p role="alert" className="first-creation-notice">{renderUiMessage(referenceError)} <button type="button" onClick={refreshReferences}>{uiText('重读状态', 'Refresh state')}</button></p>}
        {messages.length > 0 && <div role="alert" className="first-creation-notice">{messages.map((message, index) => <p key={index}>{renderUiMessage(message)}</p>)}
          <button type="button" onClick={refreshReferences}>{uiText('重读原状态', 'Refresh original state')}</button>
          {uncertain && <button type="button" onClick={() => { setUncertain(false); setMessages([]); }}>{uiText('已检查原对象和任务，继续操作', 'Original object and tasks inspected; continue')}</button>}</div>}
        <>
          <details className="first-creation-details" data-testid="first-creation-details"><summary>{uiText('项目与任务详情', 'Project and task details')}</summary>
          <p className="first-creation-binding"><FolderOpen size={15} aria-hidden="true" /><code>{binding.workspaceFile}</code></p>
          {targetIsOpen && <div className="first-creation-notice" role="status" data-testid="first-creation-input-observation">
            <p>{inputObservationPending ? uiText('正在检查已保存内容…', 'Checking saved content…')
              : inputDetails.length ? uiText('内容核对未完成，请检查任务状态。', 'Content checks are incomplete. Check the task status.')
                : inputObservation ? uiText('已读取内容状态', 'Content status loaded') : uiText('内容状态待确认', 'Content status needs confirmation')}</p>
            {inputDetails.map(detail => <p key={detail}><code>{detail}</code></p>)}
            <details><summary>{uiText('内容与任务详情', 'Content and task details')}</summary>
              <p>{uiText('外部修改后请重新检查；没有输入记录的旧任务需要重新构建或运行。', 'Check again after external edits. Build or run again when an older task has no input record.')}</p>
              {inputObservation && <time dateTime={inputObservation.observedAt} title={inputObservation.observedAt}>{inputObservation.observedAt}</time>}
            </details>
            <button type="button" className="btn-secondary" data-testid="first-creation-refresh-inputs" disabled={inputObservationPending} onClick={refreshReferences}>{uiText('检查任务状态', 'Check task status')}</button>
          </div>}
          </details>
          {workspace?.generator.id !== FIRST_CREATION_GENERATOR ? <div className="first-creation-notice" role="status">
              {uiText('此首作路径需要 Fabric 1.21.1。当前生成器已变化；可退出指引继续编辑，或在版本与迁移中核对。', 'This first-creation path requires Fabric 1.21.1. The generator changed; leave the guide to keep editing, or review Versions & migration.')}
              <button type="button" className="btn-secondary" onClick={() => { closeGuide(); setActiveView('tracks'); }}>{uiText('核对生成器', 'Review generator')}</button></div>
              : <ol className="first-creation-steps">
                {step('workspace', uiText('1. 工作区', '1. Workspace'), uiText('工作区已就绪', 'Workspace ready'), <p>{workspace.name} · {workspace.generator.displayName}</p>)}
                {step('element', uiText('2. 创建并编辑方块', '2. Create and edit the block'), element ? uiText('方块已创建', 'Block created') : uiText('等待定位元素', 'Locate the element'), <>
                  {resolution?.status === 'missing' || resolution?.status === 'changed' ? <p role="status">{uiText('原元素已删除或身份已变化。指引不会重建；可退出后继续正常编辑。', 'The original element was deleted or its identity changed. Leave the guide to continue editing; it will not be recreated.')}</p>
                    : resolution?.status === 'unbound' && resolution.conflict ? <div><p>{uiText('已存在同名元素。请检查它，然后决定是否使用此方块继续。', 'An element with this name already exists. Inspect it, then decide whether to continue with this block.')}</p>
                      <button type="button" className="btn-secondary" onClick={() => openElement(resolution.conflict!.id)}>{uiText('检查已有元素', 'Inspect existing element')}</button>
                      {resolution.conflict.type === 'block' && <button type="button" className="btn-secondary" data-testid="first-creation-use-existing" disabled={!native} onClick={() => bindElement(binding.workspaceFile, resolution.conflict!.id)}>{uiText('使用这个方块', 'Use this block')}</button>}</div>
                      : !element ? <button type="button" className="btn-primary" data-testid="first-creation-create-element" disabled={!native || busy || uncertain} onClick={() => void action('create', () => createModElement('block', FIRST_CREATION_ELEMENT))}><Box size={14} aria-hidden="true" />{uiText('创建 Trail Block', 'Create Trail Block')}</button>
                        : <><p>{uiText('在编辑器中设置：显示名称 Trail Block、硬度 3、爆炸抗性 6；保留默认石头纹理与方块物品，然后应用更改。', 'In the editor, set display name to Trail Block, hardness to 3 and blast resistance to 6. Keep the default stone texture and block item, then apply changes.')}</p>
                          <p data-testid="first-creation-save-fact">{saved ? uiText('已从 Core 读取到建议字段的保存值。', 'Core returned the saved values for the recommended fields.') : uiText('请在编辑器应用修改，再返回核对保存值。', 'Apply changes in the editor, then return to check the saved values.')}</p>
                          <button type="button" className="btn-secondary" data-testid="first-creation-open-element" onClick={() => openElement(element.id)}>{uiText('打开原元素', 'Open original element')}</button></>}
                </>)}
                {step('build', uiText('3. 构建', '3. Build'), taskLabel(build), <>
                  {pendingInput && <p className="first-creation-notice" data-testid="first-creation-unsaved">{uiText('仍有未保存输入。构建与导出只使用已保存文件，请先在编辑器应用更改。', 'There is unsaved input. Build and export use saved files; apply changes in the editor first.')}</p>}
                  <p>{uiText('这里显示任务完成时的结果。修改工程后请重新构建。', 'This shows the result when the task completed. Rebuild after changing the project.')}</p>
                  {build.task?.completedAt && <p><time dateTime={build.task.completedAt}>{build.task.completedAt}</time></p>}
                  <button type="button" className="btn-secondary" data-testid="first-creation-build" disabled={!native || !element || busy || uncertain || buildRunning} onClick={() => void action('build', buildWorkspace)}><Hammer size={14} aria-hidden="true" />{uiText('构建已保存内容', 'Build saved content')}</button>
                  {binding.build && <button type="button" className="btn-secondary" onClick={() => showTask(binding.build!.id)}>{uiText('查看原构建任务', 'View original build task')}</button>}
                </>)}
                {step('client', uiText('4. 启动与正常退出', '4. Launch and normal exit'), taskLabel(client), <>
                  <p>{uiText('启动客户端并创建独立创造世界。放置后保存并正常退出 Minecraft，再启动新的客户端，打开同一个世界。每次启动与退出分别由任务记录。', 'Start the client and create a separate creative world. Place the block, save and exit Minecraft normally, then start a new client and reopen the same world. Tasks record each launch and exit.')}</p>
                  <code>{`/give @s ${binding.modId}:trail_block 16`}</code>
                  <button type="button" className="btn-secondary" data-testid="first-creation-run-client" disabled={!native || !element || busy || uncertain || clientRunning} onClick={() => void action('client', runClient)}><Play size={14} aria-hidden="true" />{binding.clients.length ? uiText('再次启动客户端', 'Start client again') : uiText('启动客户端', 'Start client')}</button>
                  {binding.clients.at(-1) && <button type="button" className="btn-secondary" onClick={() => showTask(binding.clients.at(-1)!.id)}>{uiText('查看原客户端任务', 'View original client task')}</button>}
                </>)}
                {step('behavior', uiText('5. 你在游戏中的确认', '5. Your observations in game'), confirmed.placed && confirmed.persisted ? uiText('本次会话由你确认', 'Confirmed by you in this session') : uiText('尚未确认', 'Not confirmed'), <>
                  <p>{uiText('两次客户端任务正常退出后，可以记录自己的观察。保存修改或再次启动会使这些确认需要重新核对。', 'After two client tasks exit normally, record your observations. Saved changes or another launch require checking these confirmations again.')}</p>
                  <label><input type="checkbox" data-testid="first-creation-confirm-placed" disabled={!canConfirm} checked={confirmed.placed} onChange={event => setConfirmation({ key: confirmationKey, placed: event.target.checked, persisted: confirmed.persisted })} />{uiText('我取得并放置了 Trail Block。', 'I obtained and placed Trail Block.')}</label>
                  <label><input type="checkbox" data-testid="first-creation-confirm-persisted" disabled={!canConfirm} checked={confirmed.persisted} onChange={event => setConfirmation({ key: confirmationKey, placed: confirmed.placed, persisted: event.target.checked })} />{uiText('我保存并退出，在新客户端重开同一世界后方块仍在。', 'I saved and exited, then reopened the same world in a new client and the block remained.')}</label>
                  <p>{uiText('这是你的观察记录；自动验收范围以已有报告为准。', 'These are your observations. Automated acceptance scope comes from its recorded report.')}</p>
                </>)}
                {step('delivery', uiText('6. 导出与继续开发', '6. Export and keep developing'), taskLabel(exported), <>
                  <p>{uiText('普通导出会重新构建，不要求配置自动验收。已有验收的精确导出使用被测 JAR 和报告。', 'Ordinary export rebuilds and needs no automated acceptance setup. Exact export uses the tested JAR and its recorded report.')}</p>
                  {binding.export?.output && <code>{binding.export.output}</code>}
                  <button type="button" className="btn-secondary" data-testid="first-creation-delivery" disabled={!native} onClick={openDelivery}>{uiText('查看交付与兼容范围', 'View delivery and compatibility')}</button>
                  {element && <button type="button" className="btn-secondary" onClick={() => openElement(element.id)}><CheckCircle2 size={14} aria-hidden="true" />{uiText('继续编辑这件作品', 'Keep editing this creation')}</button>}
                </>)}
              </ol>}
        </>
      </div>
      <footer className="modal-footer"><button type="button" className="btn-secondary" data-testid="first-creation-skip" onClick={closeGuide}>{uiText('结束引导', 'End tour')}</button>
        {steps.findIndex(([id]) => id === currentStep) < steps.length - 1 && <button type="button" className="btn-secondary" data-testid="first-creation-next"
          onClick={() => setSelectedStep(steps[steps.findIndex(([id]) => id === currentStep) + 1][0])}>{uiText('下一步', 'Next')}<ArrowRight size={14} aria-hidden="true" /></button>}</footer>
    </div>
  </div>;
}
