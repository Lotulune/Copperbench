import React, { useState, useEffect, useMemo, useRef } from 'react';
import {
  Layers,
  ShieldCheck,
  AlertTriangle,
  CheckCircle2,
  Loader2,
  Info,
  FolderOpen,
  RefreshCw
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { workspaceOpenBridge } from '../bridge/workspaceOpenBridge';
import { diagnosticsBridge } from '../bridge/diagnosticsBridge';
import {
  NewWorkspaceGeneratorCatalog,
  NewWorkspaceGenerator,
  CommandResult,
  Diagnostic
} from '../types/contract';
import { t } from '../i18n';

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
export const NewWorkspaceView: React.FC = () => {
  const { listNewWorkspaceGenerators, createWorkspace, state } = useWorkbench();

  const [catalog, setCatalog] = useState<NewWorkspaceGeneratorCatalog | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [catalogReloadToken, setCatalogReloadToken] = useState(0);

  const [generatorId, setGeneratorId] = useState<string>('');
  const [modName, setModName] = useState('');
  const [modId, setModId] = useState('');
  const [packageName, setPackageName] = useState('');
  const [packageTouched, setPackageTouched] = useState(false);
  const [workspaceFolder, setWorkspaceFolder] = useState('');
  const [version, setVersion] = useState('1.0.0');
  const [userApproved, setUserApproved] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<CommandResult | null>(null);
  const [openError, setOpenError] = useState<string | null>(null);
  const [diagnosticActionError, setDiagnosticActionError] = useState<string | null>(null);
  const errorSummaryRef = useRef<HTMLDivElement>(null);

  // 读取生成器目录（挂载时）
  useEffect(() => {
    let cancelled = false;
    setCatalog(null);
    setCatalogError(null);
    void listNewWorkspaceGenerators()
      .then((data) => {
        if (cancelled) return;
        if (!data) {
          setCatalogError('生成器目录返回了空结果。');
          return;
        }
        setCatalog(data);
        if (data && data.generators.length > 0) {
          const preferred = data.generators.find((g) => g.available) ?? data.generators[0];
          setGeneratorId(preferred.generatorId);
        }
      })
      .catch(() => {
        if (!cancelled) setCatalogError('生成器目录无法加载。');
      });
    return () => {
      cancelled = true;
    };
  }, [catalogReloadToken, listNewWorkspaceGenerators]);

  // 包名自动补全：未手动修改时跟随 modId（net.mcreator.<modid>，与 Swing 对话框一致）
  useEffect(() => {
    if (!packageTouched && modId) {
      setPackageName(`net.mcreator.${modId.replaceAll(/[^a-z0-9_]/g, '')}`);
    }
  }, [modId, packageTouched]);

  const selectedGenerator = useMemo(
    () => catalog?.generators.find((g) => g.generatorId === generatorId) ?? null,
    [catalog, generatorId]
  );
  const isResourcePack = selectedGenerator?.loader === 'resource_pack';

  // 按 track 分组渲染（目录按 track 顺序返回）
  const groupedByTrack = useMemo(() => {
    if (!catalog) return [];
    return [...new Set(catalog.generators.map((generator) => generator.trackId))].map((id) => {
      const generators = catalog.generators.filter((generator) => generator.trackId === id);
      const minecraftVersion = generators[0]?.minecraftVersion;
      const label = id === 'latest_stable'
        ? `最新稳定轨 · Minecraft ${minecraftVersion}`
        : id === 'previous_stable'
          ? `前一稳定轨 · Minecraft ${minecraftVersion}`
          : id === 'resource_pack'
            ? `独立资源包 · Minecraft ${minecraftVersion}`
            : `维护轨 · Minecraft ${minecraftVersion}`;
      return { id, label, generators };
    });
  }, [catalog]);

  // 建议的完整工作区文件夹路径（跟随 modId）
  const suggestedFolder = useMemo(() => {
    if (!catalog?.suggestedWorkspaceFoldersRoot || !modId) return null;
    const root = catalog.suggestedWorkspaceFoldersRoot.replace(/[/\\]+$/, '');
    return `${root}/${modId}`;
  }, [catalog, modId]);

  const effectiveFolder = workspaceFolder.trim() || suggestedFolder || '';

  const handleSubmit = async (event?: React.FormEvent<HTMLFormElement>) => {
    event?.preventDefault();
    if (!generatorId || !userApproved) return;
    setSubmitting(true);
    setResult(null);
    try {
      const res = await createWorkspace({
        generatorId,
        modName: modName.trim(),
        modId: modId.trim(),
        packageName: packageName.trim() || undefined,
        workspaceFolderPath: effectiveFolder,
        version: version.trim() || undefined,
        userApproved
      });
      setResult(res);
      // 创建成功后请求宿主在新窗口打开该工作区（仅桌面宿主可用；浏览器预览环境跳过）。
      if (res.status === 'committed' && res.data?.workspaceFile) {
        try {
          await workspaceOpenBridge.open(res.data.workspaceFile);
          setOpenError(null);
        } catch (error: unknown) {
          setOpenError(
            error instanceof Error ? error.message : '新工作区已创建，但在新窗口打开失败。'
          );
        }
      }
    } catch {
      setResult({
        messageType: 'command_result',
        schemaVersion: '1.0',
        requestId: '',
        workspaceId: state.workbench?.workspace.id ?? '',
        operation: 'create_workspace',
        status: 'failed',
        newRevision: state.workbench?.workspace.revision ?? 0,
        recoveryPointId: null,
        task: null,
        data: null,
        diagnostics: [
          {
            code: 'BRIDGE_TRANSPORT_FAILED',
            severity: 'error',
            message: {
              key: 'diagnostic.bridge_transport_failed',
              fallback: '与 Java Core 的通信失败，工作区未创建。'
            },
            path: null,
            elementId: null,
            recoverable: true,
            actions: []
          }
        ],
        conflict: null,
        denial: null
      });
    } finally {
      setSubmitting(false);
    }
  };

  const created = result?.status === 'committed';
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
      setDiagnosticActionError(`当前宿主无法打开应用日志，错误编号 ${failureId} 已复制。`);
    }
  };

  useEffect(() => {
    if (!rejected) return;
    const animationFrame = window.requestAnimationFrame(() => errorSummaryRef.current?.focus());
    return () => window.cancelAnimationFrame(animationFrame);
  }, [rejected, result]);

  const canSubmit = generatorId !== '' && modName.trim() !== '' && modId.trim() !== '' &&
    effectiveFolder !== '' && userApproved && !submitting;

  return (
    <form
      className="new-workspace-view animate-fade-in"
      data-testid="new-workspace-view"
      onSubmit={(event) => void handleSubmit(event)}
      style={{ flex: 1, padding: '24px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '20px' }}
    >
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '16px' }}>
        <div style={{ padding: '8px', borderRadius: 'var(--radius-md)', background: 'var(--accent-copper-dim)', color: 'var(--accent-copper)' }}>
          <Layers size={24} />
        </div>
        <div>
          <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0 }}>新建工作区</h2>
          <p style={{ fontSize: '12px', color: 'var(--text-sub)', margin: '4px 0 0 0' }}>
            四轨 Fabric / NeoForge 与独立资源包生成器 · 创建后写入 .mcreator 工作区文件并在新窗口打开
          </p>
        </div>
      </div>

      {/* 创建成功横幅 */}
      {created && result?.data && (
        <div
          data-testid="workspace-created-banner"
          role="status"
          style={{ background: 'var(--badge-green-bg)', border: '1px solid rgba(63, 185, 80, 0.4)', borderRadius: 'var(--radius-md)', padding: '14px 18px', display: 'flex', alignItems: 'center', gap: '12px' }}
        >
          <CheckCircle2 size={20} color="var(--badge-green)" />
          <div>
            <strong style={{ fontSize: '13px', color: 'var(--text-main)' }}>工作区已创建！</strong>
            <div style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
              工作区文件：<code>{result.data.workspaceFile}</code>（生成器 <code>{result.data.generatorId}</code>）。
              {workspaceOpenBridge.available
                ? '宿主正在新窗口中打开该工作区。'
                : '浏览器预览环境不连接 Swing 宿主，请在桌面版中打开该工作区文件。'}
            </div>
          </div>
        </div>
      )}

      {/* 宿主打开失败提示（工作区本身已创建成功） */}
      {created && openError && (
        <div role="alert" style={{ background: 'var(--badge-amber-bg)', border: '1px solid rgba(210, 153, 34, 0.4)', borderRadius: 'var(--radius-md)', padding: '12px 16px', display: 'flex', alignItems: 'center', gap: '10px', fontSize: '12px', color: 'var(--text-main)' }}>
          <AlertTriangle size={16} color="var(--badge-amber)" />
          <span>新工作区已创建，但在新窗口打开失败：{openError}</span>
        </div>
      )}

      {/* 驳回 / 失败诊断横幅 */}
      {rejected && result && (
        <div
          ref={errorSummaryRef}
          data-testid="workspace-rejected-banner"
          role="alert"
          tabIndex={-1}
          aria-labelledby="workspace-error-summary-title"
          style={{ background: 'var(--badge-red-bg)', border: '1px solid rgba(248, 81, 73, 0.4)', borderRadius: 'var(--radius-md)', padding: '14px 18px', display: 'flex', flexDirection: 'column', gap: '8px' }}
        >
          <strong id="workspace-error-summary-title" style={{ fontSize: '13px', color: 'var(--text-main)' }}>
            工作区未创建，请修正以下问题
          </strong>
          {result.diagnostics.map((d) => (
            <div key={d.code} style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
              <AlertTriangle size={14} color="var(--badge-red)" style={{ flexShrink: 0, marginTop: '2px' }} />
              <div style={{ fontSize: '12px', color: 'var(--text-main)', lineHeight: 1.5 }}>
                {diagnosticField(d) ? (
                  <a
                    href={`#${FIELD_ELEMENT_ID[diagnosticField(d)!]}`}
                    onClick={(event) => {
                      event.preventDefault();
                      document.getElementById(FIELD_ELEMENT_ID[diagnosticField(d)!])?.focus();
                    }}
                    style={{ color: 'inherit' }}
                  >
                    {t(d.message)}
                  </a>
                ) : t(d.message)}
                <code style={{ marginLeft: '8px', fontSize: '10px', color: 'var(--text-sub)' }}>{d.code}</code>
                {d.message.args?.failureId != null && (
                  <code style={{ marginLeft: '8px', fontSize: '10px', color: 'var(--text-sub)' }}>
                    错误编号：{String(d.message.args.failureId)}
                  </code>
                )}
                {d.actions.filter((action) => action.kind === 'open_logs').map((action) => (
                  <button
                    key={action.id}
                    type="button"
                    className="btn-secondary"
                    onClick={() => void openFailureLogs(d)}
                    style={{ marginLeft: '8px', fontSize: '10px', padding: '2px 8px' }}
                  >
                    {t(action.label)}
                  </button>
                ))}
              </div>
            </div>
          ))}
          {result.diagnostics.some((diagnostic) => diagnostic.recoverable && !diagnosticField(diagnostic)) && (
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void handleSubmit()}
              disabled={submitting}
              style={{ alignSelf: 'flex-start', marginTop: '4px', fontSize: '11px', padding: '4px 10px' }}
            >
              <RefreshCw size={12} aria-hidden="true" />
              重新尝试
            </button>
          )}
          {diagnosticActionError && (
            <span role="alert" style={{ fontSize: '11px', color: 'var(--badge-red)' }}>
              {diagnosticActionError}
            </span>
          )}
        </div>
      )}

      {/* 目录加载失败 */}
      {catalogError && (
        <div role="alert" style={{ background: 'var(--badge-red-bg)', border: '1px solid rgba(248, 81, 73, 0.4)', borderRadius: 'var(--radius-md)', padding: '12px 16px', fontSize: '12px', color: 'var(--badge-red)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px' }}>
          <span>{catalogError}</span>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => setCatalogReloadToken((token) => token + 1)}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}
          >
            <RefreshCw size={12} aria-hidden="true" />
            重试
          </button>
        </div>
      )}

      <div className="new-workspace-grid" style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 1fr) minmax(320px, 1fr)', gap: '20px', alignItems: 'start' }}>
        {/* 左列：生成器选择 */}
        <div
          id={FIELD_ELEMENT_ID.generatorId}
          data-testid="generator-catalog"
          tabIndex={-1}
          aria-invalid={fieldError('generatorId') ? true : undefined}
          aria-describedby={fieldError('generatorId') ? 'new-workspace-generator-error' : undefined}
          style={{ background: 'var(--bg-panel)', border: fieldError('generatorId') ? '1px solid var(--badge-red)' : '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}
        >
          <h3 style={{ fontSize: '14px', fontWeight: 700, margin: 0 }}>选择生成器</h3>

          {!catalog && !catalogError && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--text-sub)', fontSize: '12px' }}>
              <Loader2 size={14} className="spin" />
              正在加载生成器目录…
            </div>
          )}

          {groupedByTrack.map((track) => (
            <div key={track.id} data-testid={`generator-track-${track.id}`} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-sub)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                {track.label}
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '8px' }}>
                {track.generators.map((g: NewWorkspaceGenerator) => {
                  const isSel = generatorId === g.generatorId;
                  return (
                    <button
                      key={g.generatorId}
                      type="button"
                      data-testid={`generator-option-${g.generatorId}`}
                      disabled={!g.available}
                      onClick={() => setGeneratorId(g.generatorId)}
                      aria-pressed={isSel}
                      title={g.available ? g.workspaceGeneratorName : '生成器插件未安装或未加载'}
                      style={{
                        padding: '10px 12px',
                        background: isSel ? 'var(--accent-copper-dim)' : 'var(--bg-canvas)',
                        border: isSel ? '1px solid var(--accent-copper)' : '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-sm)',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '4px',
                        alignItems: 'flex-start',
                        color: isSel ? 'var(--accent-copper)' : 'var(--text-main)',
                        fontWeight: isSel ? 600 : 500,
                        cursor: g.available ? 'pointer' : 'not-allowed',
                        opacity: g.available ? 1 : 0.5,
                        textAlign: 'left'
                      }}
                    >
                      <span style={{ fontSize: '12px' }}>
                        {g.loader === 'resource_pack' ? '资源包' : g.loader === 'neoforge' ? 'NeoForge' : 'Fabric'} {g.minecraftVersion}
                      </span>
                      <span style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                        {g.available ? g.generatorId : `${g.generatorId} · 未安装`}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          ))}

          {selectedGenerator && (
            <div data-testid="selected-generator-info" style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'var(--bg-canvas)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', padding: '8px 12px', fontSize: '11px', color: 'var(--text-sub)' }}>
              <Info size={13} color="var(--accent-copper)" />
              当前选择：<code style={{ color: 'var(--accent-copper)' }}>{selectedGenerator.workspaceGeneratorName}</code>
              {selectedGenerator.dynamic && <span className="badge badge-copper" style={{ fontSize: '9px' }}>动态轨</span>}
            </div>
          )}
          {fieldError('generatorId') && (
            <span id="new-workspace-generator-error" role="alert" style={{ fontSize: '11px', color: 'var(--badge-red)' }}>
              {t(fieldError('generatorId')!.message)}
            </span>
          )}
        </div>

        {/* 右列：表单 */}
        <div style={{ background: 'var(--bg-panel)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, margin: 0 }}>工作区信息</h3>

          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
            {isResourcePack ? '资源包名称' : '模组名称'}
            <input
              id={FIELD_ELEMENT_ID.modName}
              type="text"
              data-testid="new-workspace-mod-name-input"
              value={modName}
              onChange={(e) => setModName(e.target.value)}
              placeholder="例如 Copper Trails"
              maxLength={64}
              aria-invalid={fieldError('modName') ? true : undefined}
              aria-describedby={fieldError('modName') ? 'new-workspace-mod-name-error' : undefined}
              style={{ padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: fieldError('modName') ? '1px solid var(--badge-red)' : '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
            />
            {fieldError('modName') && (
              <span id="new-workspace-mod-name-error" role="alert" style={{ fontSize: '11px', color: 'var(--badge-red)' }}>
                {t(fieldError('modName')!.message)}
              </span>
            )}
          </label>

          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
            {isResourcePack ? '资源包 ID（命名空间）' : '模组 ID（modid）'}
            <input
              id={FIELD_ELEMENT_ID.modId}
              type="text"
              data-testid="new-workspace-mod-id-input"
              value={modId}
              onChange={(e) => setModId(e.target.value.toLowerCase())}
              placeholder="例如 copper_trails"
              maxLength={32}
              aria-invalid={fieldError('modId') ? true : undefined}
              aria-describedby={`new-workspace-mod-id-help${fieldError('modId') ? ' new-workspace-mod-id-error' : ''}`}
              style={{ padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: fieldError('modId') ? '1px solid var(--badge-red)' : '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
            />
            <span id="new-workspace-mod-id-help" style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
              2-32 位小写字母、数字或下划线，用作工作区文件名与 {isResourcePack ? '资源包命名空间' : 'mod ID'}。
            </span>
            {fieldError('modId') && (
              <span id="new-workspace-mod-id-error" role="alert" style={{ fontSize: '11px', color: 'var(--badge-red)' }}>
                {t(fieldError('modId')!.message)}
              </span>
            )}
          </label>

          {!isResourcePack && (
            <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
              Java 包名
              <input
                id={FIELD_ELEMENT_ID.packageName}
                type="text"
                data-testid="new-workspace-package-input"
                value={packageName}
                onChange={(e) => {
                  setPackageTouched(true);
                  setPackageName(e.target.value);
                }}
                placeholder={`net.mcreator.${modId || 'mymod'}`}
                aria-invalid={fieldError('packageName') ? true : undefined}
                aria-describedby={`new-workspace-package-help${fieldError('packageName') ? ' new-workspace-package-error' : ''}`}
                style={{ padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: fieldError('packageName') ? '1px solid var(--badge-red)' : '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
              />
              <span id="new-workspace-package-help" style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                留空时自动使用 net.mcreator.&lt;modid&gt;（与旧版对话框一致）。
              </span>
              {fieldError('packageName') && (
                <span id="new-workspace-package-error" role="alert" style={{ fontSize: '11px', color: 'var(--badge-red)' }}>
                  {t(fieldError('packageName')!.message)}
                </span>
              )}
            </label>
          )}

          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
            工作区文件夹
            <div style={{ display: 'flex', gap: '8px' }}>
              <input
                id={FIELD_ELEMENT_ID.workspaceFolderPath}
                type="text"
                data-testid="new-workspace-folder-input"
                value={workspaceFolder}
                onChange={(e) => setWorkspaceFolder(e.target.value)}
                placeholder={suggestedFolder ?? (catalog?.suggestedWorkspaceFoldersRoot ?? 'C:\\Users\\you\\MCreatorWorkspaces')}
                aria-invalid={fieldError('workspaceFolderPath') ? true : undefined}
                aria-describedby={`new-workspace-folder-help${fieldError('workspaceFolderPath') ? ' new-workspace-folder-error' : ''}`}
                style={{ flex: 1, padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: fieldError('workspaceFolderPath') ? '1px solid var(--badge-red)' : '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
              />
              <button
                type="button"
                className="btn-secondary"
                data-testid="new-workspace-browse-btn"
                disabled
                title="仅桌面 Full Access 宿主可用；当前需手动输入路径"
                style={{ fontSize: '12px', padding: '6px 12px', opacity: 0.6, display: 'flex', alignItems: 'center', gap: '6px' }}
              >
                <FolderOpen size={12} />
                浏览…
              </button>
            </div>
            <span id="new-workspace-folder-help" style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
              {catalog ? (
                <>必须位于建议根目录之下：<code>{catalog.suggestedWorkspaceFoldersRoot}</code>{suggestedFolder && <>（默认 <code>{suggestedFolder}</code>）</>}</>
              ) : '必须位于建议的工作区根目录之下。'}
            </span>
            {fieldError('workspaceFolderPath') && (
              <span id="new-workspace-folder-error" role="alert" style={{ fontSize: '11px', color: 'var(--badge-red)' }}>
                {t(fieldError('workspaceFolderPath')!.message)}
              </span>
            )}
          </label>

          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
            版本号
            <input
              type="text"
              data-testid="new-workspace-version-input"
              value={version}
              onChange={(e) => setVersion(e.target.value)}
              maxLength={32}
              style={{ padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px', width: '140px' }}
            />
          </label>
        </div>
      </div>

      {/* 确认门与提交 */}
      <div style={{ background: 'var(--bg-panel)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
        <div role="note" style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', fontSize: '12px', color: 'var(--text-main)', lineHeight: 1.5 }}>
          <ShieldCheck size={16} color="var(--badge-blue)" style={{ flexShrink: 0, marginTop: '2px' }} />
          <span>
            创建工作区会写入新文件夹与 <code>.mcreator</code> 工作区文件。此命令走 <code>create_workspace</code> 审批门，需要显式确认后才会提交。
          </span>
        </div>

        <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '12px', fontWeight: 600 }}>
          <input
            type="checkbox"
            data-testid="confirm-create-workspace-checkbox"
            checked={userApproved}
            onChange={(e) => setUserApproved(e.target.checked)}
          />
          <span>我确认在上述文件夹中创建新的工作区</span>
        </label>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <button
            type="submit"
            className="btn-primary"
            data-testid="create-workspace-submit-btn"
            disabled={!canSubmit}
            style={{ fontSize: '12px', padding: '6px 16px', display: 'flex', alignItems: 'center', gap: '6px' }}
          >
            {submitting ? <Loader2 size={13} className="spin" /> : <Layers size={13} />}
            <span>{submitting ? '正在创建…' : '创建工作区'}</span>
          </button>
          {!userApproved && (
            <span style={{ fontSize: '11px', color: 'var(--badge-amber)' }}>
              必须勾选确认后方可创建
            </span>
          )}
        </div>
      </div>
    </form>
  );
};
