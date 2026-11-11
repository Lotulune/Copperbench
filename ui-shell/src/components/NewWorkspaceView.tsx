import { tr } from '../i18n/locale';
import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  Loader2,
  FolderOpen,
  Plus,
  RefreshCw
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { workspaceOpenBridge } from '../bridge/workspaceOpenBridge';
import { isNativeHostPresent } from '../bridge';
import { diagnosticsBridge } from '../bridge/diagnosticsBridge';
import {
  NewWorkspaceGeneratorCatalog,
  PreviewTarget,
  PreviewResult,
  CommandResult,
  Diagnostic
} from '../types/contract';
import { renderUiMessage, t, uiMessage, uiText, useUiLocale, type UiMessage } from '../i18n';
import { useFirstCreation } from '../onboarding/FirstCreationContext';
import { FIRST_CREATION_GENERATOR } from '../onboarding/firstCreationModel';
import { useWorkbenchMode } from '../context/WorkbenchModeContext';
import { creationModId } from '../onboarding/simpleCreationDefaults';
import './newWorkspace.css';

type WorkspaceFormField = 'generatorId' | 'modName' | 'modId' | 'packageName' | 'workspaceFolderPath';

const FIELD_BY_DIAGNOSTIC: Record<string, WorkspaceFormField> = {
  UNSUPPORTED_GENERATOR: 'generatorId',
  GENERATOR_NOT_INSTALLED: 'generatorId',
  MOD_NAME_INVALID: 'modName',
  MOD_ID_INVALID: 'modId',
  PACKAGE_NAME_INVALID: 'packageName',
  WORKSPACE_FOLDER_REQUIRED: 'workspaceFolderPath',
  WORKSPACE_FOLDER_OUTSIDE_ROOT: 'workspaceFolderPath',
  WORKSPACE_FOLDER_NOT_EMPTY: 'workspaceFolderPath',
  WORKSPACE_CREATE_FAILED: 'workspaceFolderPath'
};

const FIELD_ELEMENT_ID: Record<WorkspaceFormField, string> = {
  generatorId: 'new-workspace-generator-group',
  modName: 'new-workspace-mod-name',
  modId: 'new-workspace-mod-id',
  packageName: 'new-workspace-package-name',
  workspaceFolderPath: 'new-workspace-folder'
};

const diagnosticField = (diagnostic: Diagnostic): WorkspaceFormField | null => {
  const path = diagnostic.path?.replace(/^\//, '');
  if (path && path in FIELD_ELEMENT_ID) return path as WorkspaceFormField;
  return FIELD_BY_DIAGNOSTIC[diagnostic.code] ?? null;
};

/**
 * 产品外壳原生「新建工作区」视图（替代跳回旧版 Swing NewWorkspaceDialog）。
 *
 * 合同约束：
 * - 生成器清单来自 list_new_workspace_generators 查询（四轨 × Fabric/NeoForge + 独立资源包），
 *   UI 不根据加载器名称自行推导可用能力。
 * - 提交走 create_workspace 命令（需要 userApproved 确认门）。
 * - 诊断展示 Core 返回的稳定代码，不在 UI 重新实现域校验。
 */
const operationLabel = (operation: string): string => ({
  create_workspace: tr("创建工作区"), update_workspace_file: tr("编辑源码"), validate_workspace: tr("校验工作区"),
  generate_workspace: tr("生成工程"), build_workspace: tr("构建模组"), run_client: tr("启动客户端"),
  run_server: tr("启动服务器"), run_datagen: tr("生成数据"), run_gametest: tr("运行验收"),
  prepare_game_tests: tr("准备验收"), export_workspace: tr("导出模组")
}[operation] ?? operation);

export const NewWorkspaceView: React.FC<{ active?: boolean }> = ({ active = true }) => {
  const [formId, setFormId] = useState(0);
  const completed = useRef(false);
  const wasActive = useRef(active);
  const { leaveCreation } = useFirstCreation();
  const startAnother = useCallback(() => {
    completed.current = false;
    leaveCreation();
    setFormId(value => value + 1);
  }, [leaveCreation]);
  useEffect(() => {
    if (active && !wasActive.current && completed.current) startAnother();
    wasActive.current = active;
  }, [active, startAnother]);
  return <NewWorkspaceForm key={formId} onCreated={() => { completed.current = true; }} onStartAnother={startAnother} />;
};

const NewWorkspaceForm: React.FC<{ onCreated: () => void; onStartAnother: () => void }> = ({ onCreated, onStartAnother }) => {
  const { listNewWorkspaceGenerators, createWorkspace, previewTargets } = useWorkbench();
  const locale = useUiLocale();
  const { mode } = useWorkbenchMode();
  const simpleMode = mode === 'simple';
  const modeRef = useRef(mode);
  modeRef.current = mode;
  const { creationIntent, leaveCreation, bindCreatedWorkspace, openGuide, openWorkspaceOpen } = useFirstCreation();
  const nativeUnavailable = creationIntent && !isNativeHostPresent();

  const [catalog, setCatalog] = useState<NewWorkspaceGeneratorCatalog | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [catalogReloadToken, setCatalogReloadToken] = useState(0);

  const [previewResult, setPreviewResult] = useState<PreviewResult | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewPending, setPreviewPending] = useState(false);
  const [choiceScope, setChoiceScope] = useState<{ generatorId: string; folder: string } | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const [generatorId, setGeneratorId] = useState<string>('');
  const [modName, setModName] = useState('');
  const [modId, setModId] = useState('');
  const [modIdTouched, setModIdTouched] = useState(false);
  const defaultNamespace = useRef(`work_${Date.now().toString(36)}`);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [folderPicking, setFolderPicking] = useState(false);
  const [folderPickerError, setFolderPickerError] = useState<string | null>(null);
  const [packageName, setPackageName] = useState('');
  const [packageTouched, setPackageTouched] = useState(false);
  const [workspaceFolder, setWorkspaceFolder] = useState('');
  const [version, setVersion] = useState('1.0.0');
  const [userApproved, setUserApproved] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<CommandResult | null>(null);
  const [openError, setOpenError] = useState<string | null>(null);
  const [diagnosticActionError, setDiagnosticActionError] = useState<string | null>(null);
  const [unknownSubmission, setUnknownSubmission] = useState<UiMessage | null>(null);
  const errorSummaryRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const submittingRef = useRef(false);
  const catalogQueryRef = useRef(listNewWorkspaceGenerators);
  catalogQueryRef.current = listNewWorkspaceGenerators;

  useEffect(() => {
    if (!creationIntent || modName || modId || workspaceFolder) return;
    setModName('First Creation'); setModId('first_creation');
    setPackageName('com.example.firstcreation'); setPackageTouched(true);
  }, [creationIntent]);

  useEffect(() => {
    if (simpleMode && !creationIntent && !modIdTouched) setModId(creationModId(modName, defaultNamespace.current));
  }, [simpleMode, creationIntent, modName, modIdTouched]);

  // 读取生成器目录（挂载时）
  useEffect(() => {
    let cancelled = false;
    setCatalog(null);
    setCatalogError(null);
    void catalogQueryRef.current()
      .then((data) => {
        if (cancelled) return;
        if (!data) {
          setCatalogError(tr("生成器目录返回了空结果。"));
          return;
        }
        setCatalog(data);
        if (data && data.generators.length > 0) {
          const recommended = data.generators.find(g => g.generatorId === FIRST_CREATION_GENERATOR && g.available);
          const preferred = creationIntent ? recommended
            : (modeRef.current === 'simple' ? recommended : undefined) ?? data.generators.find((g) => g.available) ?? data.generators[0];
          setGeneratorId(preferred?.generatorId ?? '');
        }
      })
      .catch(() => {
        if (!cancelled) setCatalogError(tr("生成器目录无法加载。"));
      });
    return () => {
      cancelled = true;
    };
  }, [catalogReloadToken, creationIntent]);

  useEffect(() => {
    let cancelled = false;
    void previewTargets({ operation: 'catalog' }).then(result => {
      if (cancelled) return;
      if (result.status !== 'succeeded') throw new Error(t(result.diagnostics[0]?.message));
      setPreviewResult(result); setPreviewError(null);
    }).catch(error => { if (!cancelled) setPreviewError(String(error.message ?? error)); });
    return () => { cancelled = true; };
  }, [previewTargets, catalogReloadToken]);

  const generators = useMemo(() => {
    if (!catalog) return [];
    const exact = previewResult?.targets;
    if (!exact) return catalog.generators;
    return [...exact.map(target => ({ generatorId: target.generatorId, loader: target.loader,
      minecraftVersion: target.minecraftVersion, trackId: target.trackId, displayName: target.generatorId,
      dynamic: target.trackId === 'latest_stable' || target.trackId === 'previous_stable',
      available: target.available || target.previewRequired, workspaceGeneratorName: target.workspaceGeneratorName })),
      ...catalog.generators.filter(item => !exact.some(target => target.generatorId === item.generatorId))];
  }, [catalog, previewResult]);

  // 包名自动补全：未手动修改时跟随 modId（net.mcreator.<modid>，与 Swing 对话框一致）
  useEffect(() => {
    if (!packageTouched && modId) {
      setPackageName(`net.mcreator.${modId.replaceAll(/[^a-z0-9_]/g, '')}`);
    }
  }, [modId, packageTouched]);

  const selectedGenerator = useMemo(
    () => generators.find((g) => g.generatorId === generatorId) ?? null,
    [generators, generatorId]
  );
  const isResourcePack = selectedGenerator?.loader === 'resource_pack';

  // 按 track 分组渲染（目录按 track 顺序返回）
  const groupedByTrack = useMemo(() => {
    return [...new Set(generators.map((generator) => generator.trackId))].filter(id => id !== 'advanced' || showAdvanced).map((id) => {
      const group = generators.filter((generator) => generator.trackId === id);
      const minecraftVersion = group[0]?.minecraftVersion;
      const label = id === 'resource_pack'
        ? tr("独立资源包 · Minecraft {0}", [minecraftVersion])
        : `Minecraft ${minecraftVersion}`;
      return { id, label: id === 'advanced' ? tr("历史精确版本") : label, generators: group };
    });
  }, [generators, showAdvanced, locale]);

  // 建议的完整工作区文件夹路径（跟随 modId）
  const suggestedFolder = useMemo(() => {
    if (!catalog?.suggestedWorkspaceFoldersRoot || !modId) return null;
    const root = catalog.suggestedWorkspaceFoldersRoot.replace(/[/\\]+$/, '');
    return `${root}/${modId}`;
  }, [catalog, modId]);

  const effectiveFolder = workspaceFolder.trim() || suggestedFolder || '';

  const previewTarget: PreviewTarget | undefined = previewResult?.targets?.find(target => target.generatorId === generatorId);
  const isPreview = previewTarget?.previewRequired === true;
  const chosen = isPreview && previewTarget.selected && choiceScope?.generatorId === generatorId && choiceScope.folder === effectiveFolder;
  const createCapability = previewTarget?.capabilities.operations?.find(operation => operation.operation === 'create_workspace');
  const previewCanCreate = !isPreview || (chosen && createCapability?.available === true);
  const changePreview = async (enable: boolean) => {
    if (previewPending) return;
    setPreviewPending(true); setPreviewError(null);
    try {
      const response = await previewTargets({ operation: enable ? 'opt_in' : 'revoke', generatorId,
        workspaceFolderPath: effectiveFolder });
      if (response.status !== 'succeeded') throw new Error(t(response.diagnostics[0]?.message));
      setPreviewResult(response);
      setChoiceScope(enable ? { generatorId, folder: effectiveFolder } : null);
    } catch (error) { setPreviewError(error instanceof Error ? error.message : String(error)); }
    finally { setPreviewPending(false); }
  };

  useEffect(() => {
    if (!choiceScope || (choiceScope.generatorId === generatorId && choiceScope.folder === effectiveFolder)) return;
    const previous = choiceScope;
    setChoiceScope(null);
    void previewTargets({ operation: 'revoke', generatorId: previous.generatorId, workspaceFolderPath: previous.folder })
      .then(response => {
        if (response.status !== 'succeeded') throw new Error(t(response.diagnostics[0]?.message));
        setPreviewResult(response);
      }).catch(error => setPreviewError(String(error.message ?? error)));
  }, [choiceScope, effectiveFolder, generatorId, previewTargets]);

  const handleSubmit = async (event?: React.FormEvent<HTMLFormElement>) => {
    event?.preventDefault();
    if (!generatorId || !userApproved || !previewCanCreate || previewPending || selectedGenerator?.available !== true
      || submittingRef.current || result?.status === 'committed' || unknownSubmission || nativeUnavailable) return;
    submittingRef.current = true;
    setSubmitting(true);
    setResult(null);
    try {
      const form = {
        generatorId,
        modName: modName.trim(),
        modId: modId.trim(),
        packageName: packageName.trim() || undefined,
        workspaceFolderPath: effectiveFolder,
        version: version.trim() || undefined,
        userApproved
      };
      const extension = isPreview ? await previewTargets({ operation: 'command', generatorId,
        command: 'create_workspace', payload: form }) : null;
      if (extension && !extension.core) {
        setPreviewError(t(extension.diagnostics[0]?.message)); return;
      }
      const res = extension?.core ?? await createWorkspace(form);
      setResult(res);
      if (res.status === 'committed') onCreated();
      if (creationIntent) bindCreatedWorkspace(res);
      // 创建成功后请求宿主在新窗口打开该工作区（仅桌面宿主可用；浏览器预览环境跳过）。
      if (res.status === 'committed' && res.data?.workspaceFile) {
        try {
          if (workspaceOpenBridge.available) await workspaceOpenBridge.open(res.data.workspaceFile);
          setOpenError(null);
        } catch (error: unknown) {
          setOpenError(
            error instanceof Error ? error.message : tr("新工作区已创建，但在新窗口打开失败。")
          );
        }
      }
    } catch {
      setUnknownSubmission(uiMessage('创建结果待确认。请先检查所选目录与原工作区，再开始新的请求。', 'The creation result needs confirmation. Inspect the selected directory and original workspace before starting a new request.'));
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  const created = result?.status === 'committed';
  // The outcome and its next actions sit at the top of the page.
  useEffect(() => { if (created) scrollRef.current?.scrollTo({ top: 0 }); }, [created]);
  const rejected = result?.status === 'rejected' || result?.status === 'failed';
  const fieldDiagnostics = useMemo(() => {
    const byField = new Map<WorkspaceFormField, Diagnostic[]>();
    for (const diagnostic of result?.diagnostics ?? []) {
      const field = diagnosticField(diagnostic);
      if (!field) continue;
      byField.set(field, [...(byField.get(field) ?? []), diagnostic]);
    }
    return byField;
  }, [result]);
  const fieldError = (field: WorkspaceFormField) => fieldDiagnostics.get(field)?.[0] ?? null;

  const openFailureLogs = async (diagnostic: Diagnostic) => {
    const failureId = String(diagnostic.message.args?.failureId ?? '');
    try {
      await diagnosticsBridge.openLogs(failureId);
      setDiagnosticActionError(null);
    } catch {
      if (failureId && navigator.clipboard) void navigator.clipboard.writeText(failureId);
      setDiagnosticActionError(tr("当前宿主无法打开应用日志，错误编号 {0} 已复制。", [failureId]));
    }
  };

  useEffect(() => {
    if (!rejected) return;
    if (result?.diagnostics.some(d => ['modId', 'packageName', 'workspaceFolderPath'].includes(diagnosticField(d) ?? ''))) setAdvancedOpen(true);
    const animationFrame = window.requestAnimationFrame(() => errorSummaryRef.current?.focus());
    return () => window.cancelAnimationFrame(animationFrame);
  }, [rejected, result]);

  const canSubmit = generatorId !== '' && modName.trim() !== '' && modId.trim() !== '' &&
    effectiveFolder !== '' && userApproved && !submitting && !previewPending && previewCanCreate
    && !created && !unknownSubmission && !nativeUnavailable
    && selectedGenerator?.available === true;

  // Name the first unmet requirement beside the disabled action instead of a standing warning.
  const blocker = created || unknownSubmission || submitting || previewPending ? null
    : nativeUnavailable ? uiText('请在桌面应用中创建', 'Create this in the desktop app')
      : selectedGenerator?.available !== true ? uiText('先选择可用的版本', 'Choose an available version')
        : !modName.trim() ? uiText('先填写名称', 'Enter a name')
          : !modId.trim() ? uiText('先填写 ID', 'Enter an ID')
            : !effectiveFolder ? uiText('先选择文件夹', 'Choose a folder')
              : isPreview && !chosen ? uiText('先选择预览', 'Opt in to the preview')
                : !previewCanCreate ? uiText('此预览目标暂不可创建', 'This preview target cannot be created yet')
                  : !userApproved ? uiText('勾选确认后创建', 'Tick the confirmation to create') : null;
  const loaderName = (loader: string) => loader === 'resource_pack' ? tr("资源包") : loader === 'neoforge' ? 'NeoForge' : 'Fabric';
  const invalidClass = (field: WorkspaceFormField) => fieldError(field) ? 'is-invalid' : undefined;

  return (
    <form
      className="new-workspace-view"
      data-testid="new-workspace-view"
      onSubmit={(event) => void handleSubmit(event)}
    >
      <div className="new-workspace-scroll" ref={scrollRef}>
      <div className="new-workspace-page">
      {/* The tab (professional) or the desk title (simple) names this view for sighted users. */}
      <h2 className="new-workspace-title">{simpleMode ? uiText('新建作品', 'New work') : tr("新建工作区")}</h2>

      {creationIntent && <div className="first-creation-notice first-creation-form-notice" data-testid="first-creation-workspace-setup">
        <p>{uiText('第一件作品使用 Fabric 1.21.1。请核对名称并选择新的空目录，确认后才创建文件。', 'First creation uses Fabric 1.21.1. Check the names and choose a new empty directory; files are created only after confirmation.')}</p>
        <button type="button" className="btn-secondary" onClick={leaveCreation}>{uiText('退出指引，使用普通新建', 'Leave guide and use ordinary creation')}</button>
        {nativeUnavailable && <p role="status">{uiText('请在桌面应用中创建本机工作区。', 'Use the desktop app to create a local workspace.')}</p>}
        {catalog && !catalog.generators.some(generator => generator.generatorId === FIRST_CREATION_GENERATOR && generator.available) && <p role="alert" data-testid="first-creation-generator-unavailable">{uiText('Fabric 1.21.1 生成器不可用。请检查安装与插件后重试，此路径不会自动改选其他版本。', 'The Fabric 1.21.1 generator is unavailable. Check the installation and plugins, then retry; this path will not select another version automatically.')}</p>}
      </div>}

      {unknownSubmission && <div className="first-creation-notice" role="alert" data-testid="workspace-create-unconfirmed">
        <p>{renderUiMessage(unknownSubmission)}</p>
        <button type="button" className="btn-secondary" onClick={() => { setUnknownSubmission(null); setUserApproved(false); }}>{uiText('已检查原目录，返回表单', 'Original directory inspected; return to form')}</button>
      </div>}

      {/* 创建成功横幅 */}
      {created && result?.data && (
        <div className="new-workspace-banner is-success" data-testid="workspace-created-banner" role="status">
          <CheckCircle2 size={18} aria-hidden="true" />
          <div className="new-workspace-banner-copy">
            <strong>{tr("工作区已创建！")}</strong>
            <code title={result.data.generatorId}>{result.data.workspaceFile}</code>
            <span>{workspaceOpenBridge.available
              ? uiText('已请求在新窗口打开工作区。', 'Opening the workspace in a new window was requested.')
              : tr("请在桌面版中打开此工作区。")}</span>
          </div>
          <div className="new-workspace-banner-actions">
            {creationIntent && <button type="button" className="btn-secondary" data-testid="first-creation-open-created-guide" onClick={() => openGuide(result.data!.workspaceFile)}>{uiText('查看首作指引', 'View first-creation guide')}</button>}
            <button type="button" className="btn-secondary" data-testid="create-another-workspace" onClick={onStartAnother}>{uiText('新建另一件作品', 'Create another project')}</button>
          </div>
        </div>
      )}

      {/* 宿主打开失败提示（工作区本身已创建成功） */}
      {created && openError && (
        <div className="new-workspace-banner is-warning" role="alert">
          <AlertTriangle size={18} aria-hidden="true" />
          <div className="new-workspace-banner-copy"><span>{tr("新工作区已创建，但在新窗口打开失败：")}{openError}</span></div>
          <div className="new-workspace-banner-actions">
            <button type="button" className="btn-secondary" disabled={!workspaceOpenBridge.available} onClick={() => {
              const file = result?.data?.workspaceFile;
              if (file) void workspaceOpenBridge.open(file).then(() => setOpenError(null)).catch(error => setOpenError(error instanceof Error ? error.message : uiText('打开失败。', 'Could not open.')));
            }}>{uiText('重新打开已创建工作区', 'Reopen the created workspace')}</button>
          </div>
        </div>
      )}

      {/* 驳回 / 失败诊断横幅 */}
      {rejected && result && (
        <div
          ref={errorSummaryRef}
          className="new-workspace-banner is-error is-stacked"
          data-testid="workspace-rejected-banner"
          role="alert"
          tabIndex={-1}
          aria-labelledby="workspace-error-summary-title"
        >
          <strong id="workspace-error-summary-title">{tr("工作区未创建，请修正以下问题")}</strong>
          {result.diagnostics.map((d) => (
            <div key={d.code} className="new-workspace-diagnostic">
              <AlertTriangle size={14} aria-hidden="true" />
              <div>
                {diagnosticField(d) ? (
                  <a
                    href={`#${FIELD_ELEMENT_ID[diagnosticField(d)!]}`}
                    onClick={(event) => {
                      event.preventDefault();
                      document.getElementById(FIELD_ELEMENT_ID[diagnosticField(d)!])?.focus();
                    }}
                  >
                    {t(d.message, d.code)}
                  </a>
                ) : t(d.message, d.code)}
                <code>{d.code}</code>
                {d.message.args?.failureId != null && (
                  <code>{tr("错误编号：")}{String(d.message.args.failureId)}</code>
                )}
                {d.actions.filter((action) => action.kind === 'open_logs').map((action) => (
                  <button key={action.id} type="button" className="btn-secondary" onClick={() => void openFailureLogs(d)}>
                    {t(action.label)}
                  </button>
                ))}
              </div>
            </div>
          ))}
          {result.diagnostics.some((diagnostic) => diagnostic.recoverable && !diagnosticField(diagnostic)) && (
            <button type="button" className="btn-secondary" onClick={() => void handleSubmit()} disabled={submitting}>
              <RefreshCw size={13} aria-hidden="true" />
              {tr("重新尝试")}</button>
          )}
          {diagnosticActionError && <span role="alert" className="new-workspace-error">{diagnosticActionError}</span>}
          {result.diagnostics.some(diagnostic => diagnostic.code === 'WORKSPACE_FOLDER_NOT_EMPTY') && <button type="button" className="btn-secondary" disabled={!workspaceOpenBridge.available} onClick={openWorkspaceOpen}>{uiText('选择其他空目录，或打开已有工作区', 'Choose another empty directory, or open an existing workspace')}</button>}
        </div>
      )}

      {/* 目录加载失败 */}
      {catalogError && (
        <div className="new-workspace-banner is-error" role="alert">
          <AlertTriangle size={18} aria-hidden="true" />
          <div className="new-workspace-banner-copy"><span>{catalogError}</span></div>
          <div className="new-workspace-banner-actions">
            <button type="button" className="btn-secondary" onClick={() => setCatalogReloadToken((token) => token + 1)}>
              <RefreshCw size={13} aria-hidden="true" />
              {tr("重试")}</button>
          </div>
        </div>
      )}

      <div className="new-workspace-grid">
        {/* 左列：生成器选择 */}
        <div
          id={FIELD_ELEMENT_ID.generatorId}
          className={`new-workspace-card${fieldError('generatorId') ? ' is-invalid' : ''}`}
          data-testid="generator-catalog"
          tabIndex={-1}
          aria-invalid={fieldError('generatorId') ? true : undefined}
          aria-describedby={fieldError('generatorId') ? 'new-workspace-generator-error' : undefined}
        >
          <div className="new-workspace-card-heading">
            <h3>{simpleMode ? uiText('游戏版本与加载器', 'Game version and loader') : tr("选择生成器")}</h3>
            {selectedGenerator && (
              <span className="new-workspace-selected-generator" data-testid="selected-generator-info">
                <span className="sr-only">{tr("当前选择：")}</span>
                <code>{selectedGenerator.workspaceGeneratorName}</code>
                {selectedGenerator.dynamic && <span className="badge badge-copper">{tr("动态轨")}</span>}
              </span>
            )}
          </div>
          {simpleMode && <p className="new-workspace-simple-hint">{uiText('要加入整合包，就选与它相同的版本和加载器。', 'To join a modpack, match its game version and loader.')}</p>}

          {!catalog && !catalogError && (
            <div className="new-workspace-loading" role="status">
              <Loader2 size={14} className="spin" aria-hidden="true" />
              {tr("正在加载生成器目录…")}</div>
          )}

          <div className="new-workspace-tracks">
            {groupedByTrack.map((track) => (
              <div key={track.id} className="new-workspace-track" data-testid={`generator-track-${track.id}`}>
                <div className="new-workspace-track-label">{track.label}</div>
                <div className="new-workspace-generator-options">
                  {track.generators.map((g) => (
                    <button
                      key={g.generatorId}
                      type="button"
                      className="new-workspace-generator"
                      data-testid={`generator-option-${g.generatorId}`}
                      disabled={!g.available || previewPending || submitting || (creationIntent && g.generatorId !== FIRST_CREATION_GENERATOR)}
                      onClick={() => setGeneratorId(g.generatorId)}
                      aria-pressed={generatorId === g.generatorId}
                      title={g.available ? g.workspaceGeneratorName : tr("生成器插件未安装或未加载")}
                    >
                      {/* The row already shows the version; exact historical targets differ within one row. */}
                      <span>{loaderName(g.loader)}<span className={track.id === 'advanced' ? undefined : 'sr-only'}> {g.minecraftVersion}</span></span>
                      {!g.available ? <small>{uiText('未安装', 'Not installed')}</small>
                        : simpleMode && g.generatorId === FIRST_CREATION_GENERATOR && <small className="is-recommended">{uiText('推荐', 'Recommended')}</small>}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>

          <label className="preview-choice">
            <input type="checkbox" checked={showAdvanced} onChange={event => setShowAdvanced(event.target.checked)} />
            {tr("显示历史精确版本")}
          </label>
          {previewError && <div role="alert" className="new-workspace-error" data-testid="preview-error">{previewError}</div>}
          {isPreview && previewTarget && (
            <section className="preview-capabilities" data-testid="preview-capabilities" aria-busy={previewPending}>
              <strong>{previewTarget.capabilities.supportStage === 'native_preview' ? tr("原生预览")
                : previewTarget.capabilities.supportStage === 'scoped_support' ? tr("范围支持") : tr("已登记")}</strong>
              <p>{previewTarget.capabilities.environmentQualified ? tr("依赖环境已验证") : tr("依赖环境尚未验证")}</p>
              <label className="preview-choice">
                <input data-testid="preview-opt-in" type="checkbox" checked={chosen}
                  disabled={previewPending || !effectiveFolder || submitting}
                  onChange={event => void changePreview(event.target.checked)} />
                {tr("为此工作区和目标目录选择预览")}
              </label>
              {!effectiveFolder && <p>{tr("填写工作区目录后可选择预览。")}</p>}
              <div role="status">{previewPending ? tr("正在更新预览选择…") : chosen ? tr("已选择预览，可随时撤回") : tr("尚未选择预览")}</div>
              {createCapability && !createCapability.ready && <p data-testid="preview-readiness-reason">
                {t({ key: `diagnostic.${createCapability.reasonCode.toLowerCase()}`, fallback: createCapability.reasonCode })}
              </p>}
              <ul>
                {previewTarget.capabilities.operations?.map(operation => (
                  <li key={operation.operation} data-testid={`preview-operation-${operation.operation}`}>
                    {operationLabel(operation.operation)}：{operation.ready ? tr("已验证") : tr("尚未验证")}
                  </li>
                ))}
              </ul>
              <p>{tr("类型和字段范围")}</p>
              <ul>{previewTarget.capabilities.elementTypes?.map(type => <li key={type.elementType}>
                <code>{type.elementType}</code>：{type.supported ? type.fields.join(', ') : tr("尚未验证")}
              </li>)}</ul>
            </section>
          )}
          {fieldError('generatorId') && (
            <span id="new-workspace-generator-error" role="alert" className="new-workspace-error">
              {t(fieldError('generatorId')!.message)}
            </span>
          )}
        </div>

        {/* 右列：表单 */}
        <div className="new-workspace-card" data-testid="new-workspace-info">
          <h3>{simpleMode ? uiText('作品信息', 'Work details') : tr("工作区信息")}</h3>

          <label className="new-workspace-field">
            {isResourcePack ? tr("资源包名称") : simpleMode ? uiText('名称', 'Name') : tr("模组名称")}
            <input
              id={FIELD_ELEMENT_ID.modName}
              type="text"
              className={invalidClass('modName')}
              data-testid="new-workspace-mod-name-input"
              value={modName}
              onChange={(e) => setModName(e.target.value)}
              placeholder={tr("例如 Copper Trails")}
              maxLength={64}
              aria-invalid={fieldError('modName') ? true : undefined}
              aria-describedby={fieldError('modName') ? 'new-workspace-mod-name-error' : undefined}
            />
            {fieldError('modName') && (
              <span id="new-workspace-mod-name-error" role="alert" className="new-workspace-error">
                {t(fieldError('modName')!.message)}
              </span>
            )}
          </label>

          {/* The save location is shown once, beside the confirmation that commits to it. */}
          {simpleMode && <button type="button" className="new-workspace-advanced-toggle" aria-expanded={advancedOpen} aria-controls="new-workspace-advanced" onClick={() => setAdvancedOpen(value => !value)}>{uiText('更多设置', 'More settings')}<ChevronDown size={14} aria-hidden="true" /></button>}
          <div id="new-workspace-advanced" className="new-workspace-advanced" hidden={simpleMode && !advancedOpen}>
          <label className="new-workspace-field">
            {isResourcePack ? tr("资源包 ID（命名空间）") : tr("模组 ID（modid）")}
            <input
              id={FIELD_ELEMENT_ID.modId}
              type="text"
              className={invalidClass('modId')}
              data-testid="new-workspace-mod-id-input"
              value={modId}
              onChange={(e) => { setModIdTouched(true); setModId(e.target.value.toLowerCase()); }}
              placeholder={tr("例如 copper_trails")}
              maxLength={32}
              aria-invalid={fieldError('modId') ? true : undefined}
              aria-describedby={`new-workspace-mod-id-help${fieldError('modId') ? ' new-workspace-mod-id-error' : ''}`}
            />
            {/* An error replaces the standing rule it restates. */}
            <span id="new-workspace-mod-id-help" className={fieldError('modId') ? 'sr-only' : 'new-workspace-help'}>
              {uiText('2–32 位小写字母、数字或下划线', '2–32 lowercase letters, digits or underscores')}
            </span>
            {fieldError('modId') && (
              <span id="new-workspace-mod-id-error" role="alert" className="new-workspace-error">
                {t(fieldError('modId')!.message)}
              </span>
            )}
          </label>

          {!isResourcePack && (
            <label className="new-workspace-field">
              {tr("Java 包名")}<input
                id={FIELD_ELEMENT_ID.packageName}
                type="text"
                className={invalidClass('packageName')}
                data-testid="new-workspace-package-input"
                value={packageName}
                onChange={(e) => {
                  setPackageTouched(true);
                  setPackageName(e.target.value);
                }}
                placeholder={`net.mcreator.${modId || 'mymod'}`}
                aria-invalid={fieldError('packageName') ? true : undefined}
                aria-describedby={`new-workspace-package-help${fieldError('packageName') ? ' new-workspace-package-error' : ''}`}
              />
              {/* The field fills itself from the ID, so the rule is only read out. */}
              <span id="new-workspace-package-help" className="sr-only">
                {tr("留空时自动使用 net.mcreator.&lt;modid&gt;。")}</span>
              {fieldError('packageName') && (
                <span id="new-workspace-package-error" role="alert" className="new-workspace-error">
                  {t(fieldError('packageName')!.message)}
                </span>
              )}
            </label>
          )}

          <label className="new-workspace-field">
            {tr("工作区文件夹")}<div className="new-workspace-folder-row">
              <input
                id={FIELD_ELEMENT_ID.workspaceFolderPath}
                type="text"
                className={invalidClass('workspaceFolderPath')}
                data-testid="new-workspace-folder-input"
                value={workspaceFolder}
                onChange={(e) => setWorkspaceFolder(e.target.value)}
                placeholder={suggestedFolder ?? (catalog?.suggestedWorkspaceFoldersRoot ?? 'C:\\Users\\you\\MCreatorWorkspaces')}
                aria-invalid={fieldError('workspaceFolderPath') ? true : undefined}
                aria-describedby={`new-workspace-folder-help${fieldError('workspaceFolderPath') ? ' new-workspace-folder-error' : ''}`}
              />
              <button
                type="button"
                className="btn-secondary"
                data-testid="new-workspace-browse-btn"
                disabled={!workspaceOpenBridge.workspaceFolderPickerAvailable || folderPicking}
                title={workspaceOpenBridge.workspaceFolderPickerAvailable ? uiText('选择作品文件夹', 'Choose a work folder') : tr("当前无法浏览文件夹，请手动输入路径")}
                onClick={() => {
                  setFolderPicking(true); setFolderPickerError(null);
                  void workspaceOpenBridge.chooseWorkspaceFolder(effectiveFolder).then(selection => {
                    if (!selection.cancelled && selection.path) setWorkspaceFolder(selection.path);
                  }).catch(error => setFolderPickerError(error instanceof Error ? error.message : uiText('无法选择文件夹，请手动输入路径。', 'Could not select a folder. Enter its path.')))
                    .finally(() => setFolderPicking(false));
                }}
              >
                <FolderOpen size={14} aria-hidden="true" />
                {tr("浏览…")}</button>
            </div>
            {folderPickerError && <span role="alert" className="new-workspace-error">{folderPickerError}</span>}
            <span id="new-workspace-folder-help" className="new-workspace-help">
              {suggestedFolder && !workspaceFolder.trim() ? <>{uiText('留空则使用 ', 'Leave empty to use ')}<code>{suggestedFolder}</code></>
                : catalog ? <>{uiText('需位于 ', 'Must be inside ')}<code>{catalog.suggestedWorkspaceFoldersRoot}</code>{uiText(' 之下', '')}</>
                  : tr("必须位于建议的工作区根目录之下。")}
            </span>
            {fieldError('workspaceFolderPath') && (
              <span id="new-workspace-folder-error" role="alert" className="new-workspace-error">
                {t(fieldError('workspaceFolderPath')!.message)}
              </span>
            )}
          </label>

          <label className="new-workspace-field is-short">
            {tr("版本号")}<input
              type="text"
              data-testid="new-workspace-version-input"
              value={version}
              onChange={(e) => setVersion(e.target.value)}
              maxLength={32}
            />
          </label>
          </div>
        </div>
      </div>
      </div>
      </div>

      {/* 确认门与提交：始终停在视图底部，路径就在确认项里。 */}
      {!created && <footer className="new-workspace-actions">
        <div className="new-workspace-page">
          <label className="new-workspace-confirm">
            <input
              type="checkbox"
              data-testid="confirm-create-workspace-checkbox"
              checked={userApproved}
              onChange={(e) => setUserApproved(e.target.checked)}
            />
            <span>{uiText('在这里创建文件夹和 .mcreator 文件', 'Create the folder and .mcreator file here')}
              {effectiveFolder && <code>{effectiveFolder}</code>}</span>
          </label>
          <div className="new-workspace-submit">
            {blocker && <span id="new-workspace-blocker" className="new-workspace-blocker">{blocker}</span>}
            <button
              type="submit"
              className="btn-primary"
              data-testid="create-workspace-submit-btn"
              disabled={!canSubmit}
              aria-describedby={blocker ? 'new-workspace-blocker' : undefined}
            >
              {submitting ? <Loader2 size={14} className="spin" aria-hidden="true" /> : <Plus size={14} aria-hidden="true" />}
              <span>{submitting ? tr("正在创建…") : simpleMode ? uiText('创建作品', 'Create work') : tr("创建工作区")}</span>
            </button>
          </div>
        </div>
      </footer>}
    </form>
  );
};
