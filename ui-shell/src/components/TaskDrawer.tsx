import { valueLabel } from '../i18n/labels';
import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import {
  Terminal,
  X,
  Ban,
  FileDiff,
  Upload,
  LoaderCircle,
  CheckCircle2,
  AlertTriangle
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { coreBridge } from '../bridge';
import { safeRandomUUID, taskLogWatermark } from '../bridge/JcefCoreBridge';
import { OperationFailure, operationDiagnostics, operationFailureText } from '../bridge/operationFailure';
import { DiagnosticDetails } from './DiagnosticDetails';
import { ExportCompatibility } from './ExportCompatibility';
import { t, uiText, uiMessage, renderUiMessage, englishCount, type UiMessage } from '../i18n';
import type { ActionHint, DatagenPreview, Diagnostic, TaskSourcePreview, WorkspacePlan, WorkspacePlanStep } from '../types/contract';
import './taskAuthorization.css';
import { useAgent } from '../context/AgentContext';
import { agentAttachment } from './agentEvidence';
import { AcceptanceSummary } from './AcceptanceSummary';

export const TaskDrawer: React.FC = () => {
  const agent = useAgent();
  const {
    isTaskDrawerOpen,
    setIsTaskDrawerOpen,
    activeTaskId,
    setActiveTaskId,
    state,
    cancelTask,
    prepareGameTests,
    runGameTest,
    exportVerifiedArtifact,
    previewDatagenOutput,
    previewTaskSource,
    openSource,
    publishDatagenOutput,
    runDiagnosticAction,
    planWorkspaceChanges,
    applyWorkspacePlan
  } = useWorkbench();

  const logContainerRef = useRef<HTMLDivElement>(null);
  const [datagenPreview, setDatagenPreview] = useState<DatagenPreview | null>(null);
  const [datagenBusy, setDatagenBusy] = useState(false);
  const [datagenError, setDatagenError] = useState<UiMessage | null>(null);
  const [allowHistoricalExport, setAllowHistoricalExport] = useState(false);
  const [sourcePreview, setSourcePreview] = useState<TaskSourcePreview | null>(null);
  const [sourceBusy, setSourceBusy] = useState(false);
  const [sourceError, setSourceError] = useState<unknown>(null);
  const [repairPlan, setRepairPlan] = useState<WorkspacePlan | null>(null);
  const [repairBusy, setRepairBusy] = useState(false);
  const [repairError, setRepairError] = useState<UiMessage | null>(null);
  const [repairApplied, setRepairApplied] = useState(false);
  const [confirmPublish, setConfirmPublish] = useState(false);
  const publishDialogRef = useDialogA11y(confirmPublish, () => setConfirmPublish(false));
  const [cancelRequests, setCancelRequests] = useState<Record<string, { status: 'requesting' | 'pending' | 'unknown' | 'rejected'; diagnostics: Diagnostic[] }>>({});
  const cancellationRequests = useRef(new Set<string>());

  // Active task and logs
  const activeTask = activeTaskId
    ? state.tasks[activeTaskId]
    : Object.values(state.tasks)[0] || null;

  const logs = activeTask ? state.taskLogs[activeTask.id] || [] : [];
  const diagnostics = activeTask ? state.taskDiagnostics[activeTask.id] || [] : [];
  const scopeKey = JSON.stringify([state.workbench?.workspace.id, activeTask?.id]);
  const logScopeRef = useRef(scopeKey);
  const seenLogSequencesRef = useRef(new Set<number>());
  const followingLogsRef = useRef(true);
  const [followingLogs, setFollowingLogs] = useState(true);
  const [unreadLogCount, setUnreadLogCount] = useState(0);
  const scope = useRef(scopeKey);
  scope.current = scopeKey;
  const alive = useRef(true);
  const requestSequences = useRef(new Map<string, number>());
  const nextRequestSequence = useRef(0);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  const requestToken = (kind: string) => {
    const sequence = ++nextRequestSequence.current;
    requestSequences.current.set(kind, sequence);
    return { scope: scopeKey, kind, sequence };
  };
  const isCurrent = (request: ReturnType<typeof requestToken>) => alive.current && scope.current === request.scope
    && requestSequences.current.get(request.kind) === request.sequence;
  const cancelRequest = cancelRequests[scopeKey];
  const taskRunning = activeTask?.state === 'queued' || activeTask?.state === 'running';
  const readError = activeTask ? state.taskReadErrors?.[activeTask.id] : undefined;

  useLayoutEffect(() => {
    const newScope = logScopeRef.current !== scopeKey;
    const sequences = new Set(logs.map(entry => entry.sequence));
    // Native reads can replace the array without adding output. Count new
    // sequences only, including lines recovered from a gap in the stream.
    const added = newScope ? 0 : [...sequences].filter(sequence => !seenLogSequencesRef.current.has(sequence)).length;
    logScopeRef.current = scopeKey;
    seenLogSequencesRef.current = sequences;
    if (!isTaskDrawerOpen || newScope) {
      followingLogsRef.current = true;
      setFollowingLogs(true);
      setUnreadLogCount(0);
    }
    const container = logContainerRef.current;
    if (!container) return;
    if (followingLogsRef.current) {
      container.scrollTop = container.scrollHeight;
      setUnreadLogCount(0);
    } else if (added > 0) {
      setUnreadLogCount(count => count + added);
    }
  }, [logs, logs.length, scopeKey, isTaskDrawerOpen, followingLogs]);

  const handleLogScroll = (event: React.UIEvent<HTMLDivElement>) => {
    const container = event.currentTarget;
    const atLatest = container.scrollHeight - container.clientHeight - container.scrollTop <= 24;
    followingLogsRef.current = atLatest;
    setFollowingLogs(atLatest);
    if (atLatest) setUnreadLogCount(0);
  };

  const resumeLogFollowing = () => {
    followingLogsRef.current = true;
    setFollowingLogs(true);
    setUnreadLogCount(0);
  };

  useEffect(() => {
    setDatagenPreview(null);
    setAllowHistoricalExport(false);
    setDatagenError(null);
    setSourcePreview(null);
    setSourceError(null);
    setRepairPlan(null);
    setRepairError(null);
    setRepairApplied(false);
    setConfirmPublish(false);
    setDatagenBusy(false); setSourceBusy(false); setRepairBusy(false);
  }, [scopeKey]);

  const handleDiagnosticAction = async (action: ActionHint, diagnostic: Diagnostic) => {
    if (action.kind === 'preview_repair') {
      const operations = action.payload?.operations;
      const expectedRevision = action.payload?.expectedRevision;
      if (!Array.isArray(operations) || operations.length === 0
        || !Number.isSafeInteger(expectedRevision) || Number(expectedRevision) < 0) {
        setRepairPlan(null);
        setRepairError({ key: 'diagnostic.repair_invalid', fallback: 'The diagnostic repair payload is invalid.' });
        return;
      }
      if (state.workbench?.workspace.revision !== expectedRevision) {
        setRepairPlan(null);
        setRepairError({ key: 'diagnostic.repair_stale', fallback: 'This repair was produced for an older workspace revision. Validate again before applying a fix.' });
        return;
      }
      setRepairBusy(true);
      setRepairError(null);
      setRepairApplied(false);
      const request = requestToken('repair');
      try {
        const plan = await planWorkspaceChanges(
          operations as Array<Omit<WorkspacePlanStep, 'plannedId'>>,
          true,
          Number(expectedRevision)
        );
        if (!isCurrent(request)) return;
        if (!plan) {
          setRepairPlan(null);
          setRepairError({ key: 'diagnostic.repair_preview_unavailable', fallback: 'Safe repair preview is unavailable.' });
          return;
        }
        setRepairPlan(plan);
      } catch {
        if (!isCurrent(request)) return;
        setRepairPlan(null);
        setRepairError({ key: 'diagnostic.repair_preview_unavailable', fallback: 'Safe repair preview is unavailable.' });
      } finally {
        if (isCurrent(request)) setRepairBusy(false);
      }
      return;
    }
    if (action.kind !== 'open_source') {
      runDiagnosticAction(action, diagnostic);
      return;
    }
    if (!activeTask || !action.target) return;
    setSourceBusy(true);
    setSourceError(null);
    const request = requestToken('source');
    try {
      const preview = await previewTaskSource(activeTask.id, action.target);
      if (!isCurrent(request)) return;
      if (!preview) {
        setSourcePreview(null);
        setSourceError(new OperationFailure([], uiMessage('源码预览不可用。', 'Source preview is unavailable.')));
        return;
      }
      // The captured bytes are shared by path; the chosen diagnostic owns its own line.
      const diagnosticLine = action.payload?.line;
      const line = typeof diagnosticLine === 'number' && Number.isSafeInteger(diagnosticLine) && diagnosticLine > 0
        ? diagnosticLine : preview.line;
      setSourcePreview({ ...preview, line });
    } catch (error) {
      if (!isCurrent(request)) return;
      setSourcePreview(null);
      setSourceError(error);
    } finally {
      if (isCurrent(request)) setSourceBusy(false);
    }
  };
  const applyDiagnosticRepair = async () => {
    if (!repairPlan || !repairPlan.safety.ready || repairPlan.workspaceId !== state.workbench?.workspace.id) return;
    setRepairBusy(true);
    setRepairError(null);
    const request = requestToken('repair');
    try {
      const result = await applyWorkspacePlan(repairPlan);
      if (!isCurrent(request)) return;
      if (result.status !== 'committed') {
        setRepairError(result.diagnostics[0]
          ? result.diagnostics[0].message
          : { key: 'diagnostic.repair_apply_failed', fallback: 'The safe repair was not applied.' });
        return;
      }
      setRepairPlan(null);
      setRepairApplied(true);
    } catch {
      if (isCurrent(request)) setRepairPlan(null);
      if (isCurrent(request)) setRepairError(uiMessage('修复结果待确认。请读取最新工作区状态并核对，勿重复应用。', 'The repair result is unknown. Read the latest workspace state and compare before applying again.'));
    } finally {
      if (isCurrent(request)) setRepairBusy(false);
    }
  };

  const loadDatagenPreview = async () => {
    if (!activeTask) return;
    setDatagenBusy(true);
    setDatagenError(null);
    const request = requestToken('datagen');
    try {
      const preview = await previewDatagenOutput(activeTask.id);
      if (!isCurrent(request)) return;
      if (!preview) setDatagenError(uiMessage("无法读取暂存结果，请确认任务已成功完成。", "Could not read staged results. Check that the task completed successfully."));
      setDatagenPreview(preview);
    } catch {
      if (!isCurrent(request)) return;
      setDatagenError(uiMessage("无法读取暂存结果，请查看任务日志。", "Could not read staged results. Check the task logs."));
    } finally {
      if (isCurrent(request)) setDatagenBusy(false);
    }
  };

  const publishDatagen = async () => {
    if (!activeTask || !datagenPreview?.canPublish || datagenPreview.taskId !== activeTask.id) return;
    setConfirmPublish(false);
    setDatagenBusy(true);
    setDatagenError(null);
    const request = requestToken('datagen');
    try {
      const result = await publishDatagenOutput(activeTask.id, datagenPreview.manifestHash);
      if (!isCurrent(request)) return;
      if (result.status === 'committed' && result.data) {
        setDatagenPreview(result.data as DatagenPreview);
      } else {
        setDatagenError(result.diagnostics[0] ? result.diagnostics[0].message : uiMessage("发布失败，工作区未变更。", "Publish failed. The workspace was not changed."));
      }
    } catch {
      if (isCurrent(request)) setDatagenError(uiMessage('发布结果待确认。请读取最新工作区状态并核对，勿重复发布。', 'The publish result is unknown. Read the latest workspace state and compare before publishing again.'));
    } finally {
      if (isCurrent(request)) setDatagenBusy(false);
    }
  };

  const requestCancellation = async () => {
    if (!activeTask?.cancellable || !taskRunning || cancellationRequests.current.has(scopeKey)) return;
    const key = scopeKey;
    cancellationRequests.current.add(key);
    setCancelRequests(previous => ({ ...previous, [key]: { status: 'requesting', diagnostics: [] } }));
    try {
      const result = await cancelTask(activeTask.id);
      if (result.status === 'rejected') cancellationRequests.current.delete(key);
      if (!alive.current) return;
      setCancelRequests(previous => ({ ...previous, [key]: { status: result.status === 'rejected' ? 'rejected' : 'pending', diagnostics: result.diagnostics } }));
    } catch {
      if (alive.current) setCancelRequests(previous => ({ ...previous, [key]: { status: 'unknown', diagnostics: [] } }));
    }
  };
  const refreshActiveTask = async () => {
    if (!activeTask || !state.workbench) return;
    const afterLogSequence = taskLogWatermark(logs);
    try {
      await coreBridge.sendQuery({ messageType: 'query', schemaVersion: '1.0', requestId: safeRandomUUID(), workspaceId: state.workbench.workspace.id,
        operation: 'get_task', payload: { taskId: activeTask.id, afterLogSequence } });
    } catch { /* The read error remains associated with the original task. */ }
  };

  if (!isTaskDrawerOpen) return null;

  return (
    <div
      className="task-drawer animate-fade-in"
      data-testid="task-drawer"
      style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        height: sourcePreview || sourceError || repairPlan || repairError || repairApplied
          ? '480px'
          : datagenPreview || datagenError || diagnostics.length > 0 || activeTask?.verification || activeTask?.gameTestSetup ? '360px' : '240px',
        background: 'var(--drawer-bg)',
        borderTop: '1px solid var(--border-subtle)',
        display: 'flex',
        flexDirection: 'column',
        boxShadow: '0 -4px 20px rgba(0, 0, 0, 0.4)',
        zIndex: 40
      }}
    >
      {/* Drawer Header */}
      <div
        style={{
          padding: '8px 16px',
          background: 'var(--bg-surface)',
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 600, fontSize: '12px', color: 'var(--accent-copper)' }}>
            <Terminal size={14} />
            <span>{uiText('任务控制台与日志流', 'Task console and logs')}</span>
          </div>

          {Object.keys(state.tasks).length > 1 && <select
            aria-label={t({ key: 'task.recent_selector', fallback: 'Recent tasks' })}
            value={activeTask?.id ?? ''} onChange={(event) => setActiveTaskId(event.target.value)}
            style={{ maxWidth: 220, background: 'var(--bg-surface)', color: 'var(--text-primary)' }}>
            {Object.values(state.tasks).map((task) => <option key={task.id} value={task.id}>
              {valueLabel(task.kind)} · {valueLabel(task.state)} · {new Date(task.startedAt).toLocaleString()}
            </option>)}
          </select>}

          {activeTask && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span className="badge badge-copper" style={{ fontSize: '12px' }}>
                {valueLabel(activeTask.kind)}
              </span>
              <span
                data-testid="task-current-state"
                className={`badge badge-${activeTask.state === 'succeeded' ? 'green' : activeTask.state === 'running' ? 'amber' : 'red'}`}
                style={{ fontSize: '12px' }}
              >
                {valueLabel(activeTask.state)}
              </span>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                {t(activeTask.stage)}
              </span>
            </div>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {activeTask && <button type="button" className="btn-secondary" data-testid="task-analyze-agent" onClick={() => {
            agent.openPanel(agentAttachment(`task:${activeTask.id}`, 'task-log', JSON.stringify({ task: activeTask, logs, diagnostics }, null, 2)),
              uiText('请分析这次任务的结果并修复问题。', 'Analyze this task result and fix the problem.'));
            setIsTaskDrawerOpen(false);
          }}>{uiText('让 Agent 分析', 'Analyze with Agent')}</button>}
          {activeTask?.kind === 'run_gametest' && activeTask.state === 'failed' &&
            <button type="button" className="btn-secondary" onClick={() => void prepareGameTests()}>{uiText("准备测试模板", "Prepare test templates")}</button>}
          {activeTask?.kind === 'prepare_game_tests' && activeTask.state === 'succeeded' &&
            <button type="button" className="btn-primary" onClick={() => void runGameTest()}>{uiText("运行测试", "Run tests")}</button>}
          {activeTask?.kind === 'run_datagen' && activeTask.state === 'succeeded' && !datagenPreview && (
            <button
              type="button"
              className="btn-secondary"
              style={{ fontSize: '12px', minHeight: '28px', padding: '3px 9px' }}
              onClick={loadDatagenPreview}
              disabled={datagenBusy}
              data-testid="datagen-preview-btn"
            >
              {datagenBusy ? <LoaderCircle className="spin" size={13} /> : <FileDiff size={13} />}
              <span>{uiText("查看暂存差异", "View staged changes")}</span>
            </button>
          )}
          {activeTask?.cancellable && (
            <button
              className="btn-danger"
              style={{ fontSize: '12px', padding: '2px 8px' }}
              type="button"
              disabled={Boolean(cancelRequest && cancelRequest.status !== 'rejected' && taskRunning)}
              onClick={() => void requestCancellation()}
              data-testid="task-cancel-btn"
            >
              <Ban size={12} />
              <span>{cancelRequest && taskRunning && cancelRequest.status !== 'rejected' ? uiText('正在请求取消', 'Requesting cancellation') : uiText('取消', 'Cancel')}</span>
            </button>
          )}

          <button
            type="button"
            aria-label={uiText('关闭日志抽屉', 'Close task console')}
            onClick={() => setIsTaskDrawerOpen(false)}
            style={{ padding: '2px 6px', color: 'var(--text-muted)' }}
            title={uiText('关闭日志抽屉', 'Close task console')}
            data-testid="task-drawer-close"
          >
            <X size={14} />
          </button>
        </div>
      </div>

      {readError && <div role="status" data-testid="task-status-unavailable" style={{ padding: '8px 16px', fontSize: 12 }}>
        <p>{uiText('暂时无法取得任务状态；保留最近一次观察。请读取原任务，确认是否仍在运行。', 'Task status is temporarily unavailable; the last observation is preserved. Read the original task to confirm whether it is still running.')}</p>
        <button type="button" className="btn-secondary" onClick={() => void refreshActiveTask()}>{uiText('读取原任务状态', 'Read original task status')}</button>
        <details><summary>{uiText('读取错误详情', 'Read error details')}</summary><pre tabIndex={0} style={{ whiteSpace: 'pre-wrap', userSelect: 'text' }}>{readError}</pre></details>
      </div>}
      {cancelRequest && taskRunning && <div role="status" data-testid="task-cancel-status" style={{ padding: '8px 16px', fontSize: 12 }}>
        <p>{cancelRequest.status === 'unknown' ? uiText('取消请求结果待确认。请读取原任务；任务尚未确认取消。', 'The cancellation result is unknown. Read the original task; cancellation has not been confirmed.')
          : cancelRequest.status === 'rejected' ? uiText('取消请求被拒绝。请查看原因；任务状态以最新回执为准。', 'The cancellation request was rejected. Review the reason; the latest receipt determines the task state.')
          : uiText('正在请求取消；等待任务返回终态。', 'Requesting cancellation; waiting for the task to reach a final state.')}</p>
        {cancelRequest.diagnostics.map((diagnostic, index) => <p key={index}>{t(diagnostic.message)}</p>)}
        <DiagnosticDetails diagnostics={cancelRequest.diagnostics} />
        {cancelRequest.status === 'unknown' && <button type="button" className="btn-secondary" onClick={() => void refreshActiveTask()}>{uiText('读取原任务状态', 'Read original task status')}</button>}
      </div>}

      {activeTask?.verification && <section className="gametest-verification" aria-label={uiText("GameTest 验收报告", "GameTest verification report")}>
        <AcceptanceSummary task={activeTask} fallback={<strong>{activeTask.verification.status === 'pending' ? uiText("测试正在执行", "Tests are running") : activeTask.verification.status === 'passed' ? uiText("配置的测试已通过", "Configured tests passed") : uiText("测试尚未通过验收", "Tests have not passed verification")}</strong>} />
        {activeTask.verification.status === 'passed' && activeTask.verification.artifactSha256 && <div>
          <p>{uiText("这是该次输入的验收结论。导出时重新核对当前输入、被测 JAR 和报告。", "This result applies to the tested input. Export checks the current input, tested JAR and report again.")}</p>
          <label><input type="checkbox" checked={allowHistoricalExport} onChange={event => setAllowHistoricalExport(event.target.checked)} />{uiText("允许导出旧输入对应的历史已验证产物", "Allow exporting a previously verified artifact for older input")}</label>
          <button type="button" className="btn-secondary" onClick={() => void exportVerifiedArtifact(activeTask.id, allowHistoricalExport)}>{uiText("导出本次已验证 JAR", "Export this verified JAR")}</button>
        </div>}
        <dl>{([
          [uiText("发现", "Discovered"), activeTask.verification.discovered], [uiText("执行", "Executed"), activeTask.verification.executed],
          [uiText("通过", "Passed"), activeTask.verification.passed], [uiText("失败", "Failed"), activeTask.verification.failed], [uiText("跳过", "Skipped"), activeTask.verification.skipped]
        ] as const).map(([name, count]) => <div key={name}><dt>{name}</dt><dd>{count}</dd></div>)}</dl>
        {!!activeTask.verification.frameworkTests && <p>{uiText(`其中框架自检 ${activeTask.verification.frameworkTests} 个；验收执行 ${activeTask.verification.acceptanceExecuted ?? 0} 个。框架自检不计入最低验收数量。`, `Framework self-tests: ${activeTask.verification.frameworkTests}; acceptance tests executed: ${activeTask.verification.acceptanceExecuted ?? 0}. Framework self-tests do not count toward the minimum acceptance total.`)}</p>}
        <details><summary>{uiText("用例与被测内容", "Test cases and tested content")}</summary>
          <p>{uiText("结果代码：", "Result code: ")}<code>{activeTask.verification.reasonCode}</code></p>
          {activeTask.verification.sourceCurrentAtCompletion === false && <p className="test-failure">{uiText("测试期间工作区已变化，请针对当前内容重新验收。", "The workspace changed during testing. Verify the current content again.")}</p>}
          {activeTask.sourceSnapshot && <p>{uiText("源码 SHA-256：", "Source SHA-256: ")}<code>{activeTask.sourceSnapshot.sha256}</code></p>}
          {activeTask.verification.artifactSha256 && <p>{uiText("被测 JAR SHA-256：", "Tested JAR SHA-256: ")}<code>{activeTask.verification.artifactSha256}</code></p>}
          {activeTask.verification.verificationPath && <p>{uiText("报告：", "Report: ")}<code>{activeTask.verification.verificationPath}</code></p>}
          <ul>{activeTask.verification.cases.slice(0, 100).map((test, index) => <li key={`${test.className}.${test.name}.${index}`}>
            {test.status === 'passed' ? uiText("通过", "Passed") : test.status === 'failed' ? uiText("失败", "Failed") : uiText("跳过", "Skipped")} · {test.className}.{test.name}
            {test.scope === 'framework' && uiText("（框架自检）", " (framework self-test)")}
            {test.message && <p className="test-failure">{test.message}</p>}
          </li>)}</ul>
          {activeTask.verification.cases.length > 100 && <p>{uiText("此处显示前 100 个用例，完整结果见报告文件。", "The first 100 cases are shown here. See the report for the full results.")}</p>}
        </details>
      </section>}
      {activeTask?.verifiedExport && <section className="gametest-verification" aria-label={uiText("已验证产物导出", "Verified artifact export")}>
        <AcceptanceSummary task={activeTask} fallback={<strong>{activeTask.verifiedExport.status === 'passed_historical_input' ? uiText("已导出历史已验证产物", "Previously verified artifact exported") : uiText('已导出验收产物 · 导出时输入匹配', 'Accepted artifact exported · input matched at export time')}</strong>} />
        <p>{activeTask.verifiedExport.exportDirectory}</p>
        <ExportCompatibility environment={activeTask.verifiedExport.environment} />
        <p>{uiText("导出内容：JAR、GameTest 报告和文件清单。", "Exported: JAR, GameTest report and file manifest.")}</p>
      </section>}
      {activeTask?.gameTestSetup && <section className="gametest-verification" aria-label={uiText("GameTest 模板", "GameTest templates")}>
        <strong>{uiText("测试入口已准备", "Test entry point prepared")}</strong><p>{uiText("初始用例只检查模组加载。请补充玩法断言后，再将结果作为玩法验收证据。", "The initial test only checks mod loading. Add gameplay assertions before using the results as gameplay acceptance evidence.")}</p>
        <code>{activeTask.gameTestSetup.configurationPath}</code>
      </section>}

      {(datagenPreview || datagenError) && (
        <section
          data-testid="datagen-preview"
          style={{
            padding: '10px 16px',
            borderBottom: '1px solid var(--border-subtle)',
            background: 'var(--bg-panel)',
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 1fr) auto',
            gap: '10px 16px',
            alignItems: 'center'
          }}
        >
          <div style={{ minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', fontWeight: 600 }}>
              {datagenPreview?.published ? <CheckCircle2 size={14} color="var(--badge-green)" /> : <FileDiff size={14} />}
              <span>{datagenPreview?.published ? uiText("生成结果已发布", "Generated results published") : uiText(`暂存差异 ${datagenPreview?.changeCount ?? 0} 项`, `Staged changes: ${datagenPreview?.changeCount ?? 0}`)}</span>
              {datagenPreview?.stale && <span className="badge badge-red">{uiText("修订已过期", "Revision is stale")}</span>}
            </div>
            {datagenPreview && (
              <div style={{ marginTop: '6px', maxHeight: '48px', overflowY: 'auto', fontFamily: 'var(--font-mono)', fontSize: '12px', color: 'var(--text-muted)' }}>
                {datagenPreview.files.map((file) => (
                  <div key={file.path} title={file.sha256} style={{ display: 'flex', gap: '8px' }}>
                    <span style={{ color: file.status === 'add' ? 'var(--badge-green)' : file.status === 'modify' ? 'var(--badge-amber)' : 'var(--text-sub)', width: '50px', flexShrink: 0 }}>
                      {valueLabel(file.status)}
                    </span>
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{file.path}</span>
                  </div>
                ))}
              </div>
            )}
            {datagenError && <div style={{ marginTop: '4px', color: 'var(--badge-red)', fontSize: '12px' }}>{renderUiMessage(datagenError)}</div>}
          </div>
          {datagenPreview?.canPublish && (
            <button
              type="button"
              className="btn-primary"
              onClick={() => setConfirmPublish(true)}
              disabled={datagenBusy || datagenPreview.stale}
              data-testid="datagen-publish-btn"
              title={uiText("写入工作区", "Write to workspace")}
            >
              {datagenBusy ? <LoaderCircle className="spin" size={14} /> : <Upload size={14} />}
              <span>{uiText("发布到工作区", "Publish to workspace")}</span>
            </button>
          )}
        </section>
      )}

      {(repairPlan || repairError || repairApplied) && (
        <section
          data-testid="task-repair-preview"
          aria-label={t({ key: 'repair.preview.title', fallback: 'Safe repair preview' })}
          style={{ padding: '10px 16px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-panel)', display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) auto', gap: '10px 16px' }}
        >
          <div style={{ minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', fontWeight: 600 }}>
              {repairApplied ? <CheckCircle2 size={14} color="var(--badge-green)" /> : <FileDiff size={14} />}
              <span>{repairApplied
                ? t({ key: 'repair.preview.applied', fallback: 'Safe repair applied' })
                : t({ key: 'repair.preview.title', fallback: 'Safe repair preview' })}</span>
              {repairPlan?.requireRecoveryPoint && <span className="badge badge-green">{t({ key: 'repair.preview.recovery', fallback: 'Recovery point required' })}</span>}
            </div>
            {repairPlan && (
              <>
                <div data-testid="task-repair-summary" style={{ marginTop: '5px', fontSize: '12px', color: 'var(--text-muted)' }}>
                  {t({ key: 'repair.preview.summary', fallback: '{objects} affected objects · {operations} operations · {paths} changed paths', args: { objects: repairPlan.review.summary.affectedObjectCount, operations: repairPlan.operationCount, paths: repairPlan.changedPaths.length } })}
                </div>
                <code style={{ display: 'block', marginTop: '5px', fontSize: '12px', color: 'var(--text-sub)', overflowWrap: 'anywhere' }}>{repairPlan.changedPaths.join(', ')}</code>
                <details style={{ marginTop: '6px', fontSize: '12px' }}>
                  <summary>{t({ key: 'repair.preview.semantic_diff', fallback: 'Semantic diff' })}</summary>
                  <pre data-testid="task-repair-semantic-diff" style={{ margin: '6px 0 0', maxHeight: '80px', overflow: 'auto', whiteSpace: 'pre-wrap' }}>{JSON.stringify(repairPlan.semanticDiff, null, 2)}</pre>
                </details>
                {!repairPlan.safety.ready && <div data-testid="task-repair-safety-blocked" style={{ marginTop: '5px', color: 'var(--badge-red)', fontSize: '12px' }}>{t({ key: 'repair.preview.safety_blocked', fallback: 'This repair cannot be applied because a required recovery point is unavailable.' })}</div>}
              </>
            )}
            {repairError && <div data-testid="task-repair-error" style={{ marginTop: '5px', color: 'var(--badge-red)', fontSize: '12px' }}>{renderUiMessage(repairError)}</div>}
          </div>
          {repairPlan && (
            <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              <button type="button" className="btn-secondary" onClick={() => { setRepairPlan(null); setRepairError(null); }} disabled={repairBusy} data-testid="task-repair-dismiss" style={{ minHeight: '32px', padding: '4px 9px' }}>
                {t({ key: 'repair.preview.dismiss', fallback: 'Dismiss' })}
              </button>
              <button type="button" className="btn-primary" onClick={() => void applyDiagnosticRepair()} disabled={repairBusy || !repairPlan.safety.ready} data-testid="task-repair-apply" style={{ minHeight: '32px', padding: '4px 9px' }}>
                {repairBusy ? <LoaderCircle className="spin" size={13} /> : <CheckCircle2 size={13} />}
                <span>{t({ key: 'repair.preview.apply', fallback: 'Apply reviewed repair' })}</span>
              </button>
            </div>
          )}
        </section>
      )}

      {diagnostics.length > 0 && (
        <section
          data-testid="task-diagnostics"
          aria-label={uiText('任务诊断', 'Task diagnostics')}
          style={{
            padding: '10px 16px',
            borderBottom: '1px solid var(--border-subtle)',
            background: 'var(--bg-panel)',
            display: 'flex',
            flexDirection: 'column',
            gap: '8px',
            maxHeight: '132px',
            overflowY: 'auto'
          }}
        >
          {diagnostics.map((diagnostic, index) => (
            <div
              key={`${diagnostic.code}:${diagnostic.path ?? ''}:${index}`}
              data-testid={`task-diagnostic-${diagnostic.code}`}
              style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) auto', gap: '8px 12px', alignItems: 'center' }}
            >
              <div style={{ minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--badge-red)', fontSize: '12px', fontWeight: 600 }}>
                  <AlertTriangle size={13} />
                  <code>{diagnostic.code}</code>
                </div>
                <div style={{ marginTop: '3px', fontSize: '12px', color: 'var(--text-main)' }}>{t(diagnostic.message, diagnostic.code)}</div>
                {diagnostic.path && (
                  <code style={{ display: 'block', marginTop: '3px', fontSize: '12px', color: 'var(--text-sub)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {diagnostic.path}
                  </code>
                )}
              </div>
              {diagnostic.actions.length > 0 && (
                <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                  {diagnostic.actions.map((action) => (
                    <button
                      key={action.id}
                      type="button"
                      className="btn-secondary"
                      style={{ fontSize: '12px', minHeight: '32px', padding: '4px 9px' }}
                      onClick={() => void handleDiagnosticAction(action, diagnostic)}
                      disabled={(action.kind === 'open_source' && sourceBusy)
                        || (action.kind === 'preview_repair' && repairBusy)}
                      data-testid={`task-diag-action-${action.id}`}
                    >
                      {t(action.label)}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}
          <DiagnosticDetails diagnostics={diagnostics} />
        </section>
      )}

      {(sourcePreview || Boolean(sourceError)) && (
        <section
          data-testid="task-source-preview"
          aria-label={uiText("源码预览", "Source preview")}
          style={{
            borderBottom: '1px solid var(--border-subtle)',
            background: 'var(--bg-input)',
            minHeight: 0,
            maxHeight: '220px',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px', padding: '7px 12px', borderBottom: '1px solid var(--border-subtle)' }}>
            <div style={{ minWidth: 0, fontSize: '12px' }}>
              <strong>{uiText("源码", "Source")}</strong>
              {sourcePreview && (
                <code style={{ marginLeft: '8px', color: 'var(--text-sub)' }}>
                  {sourcePreview.path}{sourcePreview.line > 0 ? `:${sourcePreview.line}` : ''}
                </code>
              )}
            </div>
            {sourcePreview && <button type="button" className="btn-secondary" data-testid="task-source-open-editor"
              onClick={() => {
                const workspacePath = sourcePreview.path.startsWith('/src/') ? sourcePreview.path.slice(1) : sourcePreview.path;
                openSource(workspacePath, sourcePreview.line);
                setIsTaskDrawerOpen(false);
              }}>
              {uiText('在源码编辑器中打开', 'Open in source editor')}
            </button>}
            <button
              type="button"
              className="btn-secondary"
              onClick={() => { setSourcePreview(null); setSourceError(null); }}
              data-testid="task-source-preview-close"
              style={{ minHeight: '32px', padding: '4px 9px' }}
            >
              {uiText("关闭", "Close")}</button>
          </div>
          {Boolean(sourceError) ? (
            <div data-testid="task-source-error" role="alert" style={{ padding: '12px', color: 'var(--badge-red)', fontSize: '12px', overflow: 'auto' }}>
              <p>{operationFailureText(sourceError)}</p>
              <DiagnosticDetails diagnostics={operationDiagnostics(sourceError)} />
            </div>
          ) : (
            <pre
              data-testid="task-source-content"
              style={{ margin: 0, padding: '10px 12px', overflow: 'auto', fontFamily: 'var(--font-mono)', fontSize: '12px', lineHeight: 1.5, whiteSpace: 'pre' }}
            >
              {sourcePreview?.content}
            </pre>
          )}
        </section>
      )}
      {/* Log Stream Output */}
      {!followingLogs && <div data-testid="task-log-follow-controls" style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12,
        flexShrink: 0, padding: '4px 16px', background: 'var(--bg-panel)', fontSize: 12
      }}>
        <span role="status" data-testid="task-new-log-count" style={{ color: 'var(--text-muted)' }}>
          {unreadLogCount > 0
            ? uiText(`${unreadLogCount} 条新日志`, englishCount(unreadLogCount, 'new log entry', 'new log entries'))
            : uiText('已暂停自动滚动', 'Auto-scroll paused')}
        </span>
        <button type="button" className="btn-secondary" data-testid="task-log-jump-latest"
          onClick={resumeLogFollowing} style={{ minHeight: 32, padding: '2px 8px' }}>
          {uiText('回到最新', 'Jump to latest')}
        </button>
      </div>}
      <div
        ref={logContainerRef}
        onScroll={handleLogScroll}
        data-testid="task-log-stream"
        role="log"
        aria-live="polite"
        aria-label={t({ key: 'aria.task_log_stream', fallback: 'Task log stream' })}
        style={{
          flex: 1,
          padding: '12px 16px',
          overflowY: 'auto',
          background: 'var(--bg-input)',
          fontFamily: 'var(--font-mono)',
          fontSize: '12px',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px',
          userSelect: 'text'
        }}
      >
        {logs.length === 0 ? (
          <div style={{ color: 'var(--text-sub)', fontStyle: 'italic' }}>
            {uiText('暂无任务输出日志。', 'No task output yet.')}
          </div>
        ) : (
          logs.map((entry) => (
            <div
              key={entry.sequence}
              style={{
                display: 'flex',
                gap: '8px',
                lineHeight: '1.4',
                color:
                  entry.level === 'error'
                    ? 'var(--badge-red)'
                    : entry.level === 'warning'
                    ? 'var(--badge-amber)'
                    : 'var(--text-main)'
              }}
            >
              <span style={{ color: 'var(--text-sub)', flexShrink: 0 }}>
                [{entry.timestamp.slice(11, 19)}]
              </span>
              <span
                style={{
                  fontWeight: 600,
                  color:
                    entry.level === 'error'
                      ? 'var(--badge-red)'
                      : entry.level === 'warning'
                      ? 'var(--badge-amber)'
                      : 'var(--badge-blue)',
                  flexShrink: 0
                }}
              >
                [{entry.level.toUpperCase()}]
              </span>
              <span style={{ wordBreak: 'break-word' }}>{entry.text}</span>
            </div>
          ))
        )}
      </div>

      {confirmPublish && datagenPreview && (
        <div
          role="presentation"
          onMouseDown={() => setConfirmPublish(false)}
          style={{
            position: 'fixed', inset: 0, zIndex: 100,
            background: 'rgba(0, 0, 0, 0.58)',
            display: 'grid', placeItems: 'center', padding: '24px'
          }}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="datagen-publish-title"
            data-testid="datagen-publish-dialog"
            ref={publishDialogRef}
            onMouseDown={(event) => event.stopPropagation()}
            style={{
              width: 'min(440px, 100%)',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-active)',
              borderRadius: 'var(--radius-md)',
              boxShadow: '0 16px 48px rgba(0, 0, 0, 0.55)',
              padding: '18px'
            }}
          >
            <h2 id="datagen-publish-title" style={{ margin: 0, fontSize: '16px' }}>{uiText("发布数据生成结果", "Publish generated data")}</h2>
            <p style={{ margin: '10px 0 16px', color: 'var(--text-muted)', fontSize: '12px', lineHeight: 1.6 }}>
              {uiText(`将 ${datagenPreview.changeCount} 个暂存文件写入工作区，并创建恢复点。`, `Write ${englishCount(datagenPreview.changeCount, 'staged file')} to the workspace and create a recovery point.`)}
            </p>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
              <button type="button" className="btn-secondary" onClick={() => setConfirmPublish(false)}>{uiText("取消", "Cancel")}</button>
              <button
                type="button"
                className="btn-primary"
                onClick={publishDatagen}
                data-testid="datagen-confirm-publish"
              >
                <Upload size={14} />
                <span>{uiText("确认发布", "Confirm publish")}</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
