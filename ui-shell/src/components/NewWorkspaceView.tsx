import React, { useState, useEffect, useMemo } from 'react';
import {
  Layers,
  ShieldCheck,
  AlertTriangle,
  CheckCircle2,
  Loader2,
  Info,
  FolderOpen
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { workspaceOpenBridge } from '../bridge/workspaceOpenBridge';
import {
  NewWorkspaceGeneratorCatalog,
  NewWorkspaceGenerator,
  CommandResult
} from '../types/contract';
import { t } from '../i18n';

/**
 * 产品外壳原生「新建工作区」视图（替代跳回旧版 Swing NewWorkspaceDialog）。
 *
 * 合同约束：
 * - 生成器清单来自 list_new_workspace_generators 查询（四轨 × Fabric/NeoForge），
 *   UI 不根据加载器名称自行推导可用能力。
 * - 提交走 create_workspace 命令（需要 userApproved 确认门）。
 * - 诊断展示 Core 返回的稳定代码，不在 UI 重新实现域校验。
 */
export const NewWorkspaceView: React.FC = () => {
  const { listNewWorkspaceGenerators, createWorkspace, state } = useWorkbench();

  const [catalog, setCatalog] = useState<NewWorkspaceGeneratorCatalog | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);

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

  // 读取生成器目录（挂载时）
  useEffect(() => {
    let cancelled = false;
    void listNewWorkspaceGenerators()
      .then((data) => {
        if (cancelled) return;
        setCatalog(data);
        if (data && data.generators.length > 0) {
          // 默认选中维护轨 1.21.1 Fabric（与帮助文档的推荐首选一致）
          const preferred =
            data.generators.find((g) => g.generatorId === 'fabric-1.21.1') ?? data.generators[0];
          setGeneratorId(preferred.generatorId);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled)
          setCatalogError(error instanceof Error ? error.message : '生成器目录无法加载');
      });
    return () => {
      cancelled = true;
    };
  }, [listNewWorkspaceGenerators]);

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

  // 按 track 分组渲染（目录按 track 顺序返回）
  const groupedByTrack = useMemo(() => {
    if (!catalog) return [];
    const trackOrder: { id: string; label: string }[] = [
      { id: 'latest_stable', label: '最新稳定轨' },
      { id: 'previous_stable', label: '前一稳定轨' },
      { id: 'minecraft_1_21_1', label: '维护轨 1.21.1' },
      { id: 'minecraft_1_20_1', label: '维护轨 1.20.1' }
    ];
    return trackOrder
      .map((track) => ({
        ...track,
        generators: catalog.generators.filter((g) => g.trackId === track.id)
      }))
      .filter((track) => track.generators.length > 0);
  }, [catalog]);

  // 建议的完整工作区文件夹路径（跟随 modId）
  const suggestedFolder = useMemo(() => {
    if (!catalog?.suggestedWorkspaceFoldersRoot || !modId) return null;
    const root = catalog.suggestedWorkspaceFoldersRoot.replace(/[/\\]+$/, '');
    return `${root}/${modId}`;
  }, [catalog, modId]);

  const effectiveFolder = workspaceFolder.trim() || suggestedFolder || '';

  const handleSubmit = async () => {
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
    } catch (error: unknown) {
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
              fallback:
                error instanceof Error ? error.message : '与 Java Core 的通信失败，工作区未创建。'
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
  const canSubmit = generatorId !== '' && modName.trim() !== '' && modId.trim() !== '' &&
    effectiveFolder !== '' && userApproved && !submitting;

  return (
    <div
      className="new-workspace-view animate-fade-in"
      data-testid="new-workspace-view"
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
            四轨 Fabric / NeoForge 生成器 · 创建后写入 .mcreator 工作区文件并在新窗口打开
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
          data-testid="workspace-rejected-banner"
          role="alert"
          style={{ background: 'var(--badge-red-bg)', border: '1px solid rgba(248, 81, 73, 0.4)', borderRadius: 'var(--radius-md)', padding: '14px 18px', display: 'flex', flexDirection: 'column', gap: '8px' }}
        >
          {result.diagnostics.map((d) => (
            <div key={d.code} style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
              <AlertTriangle size={14} color="var(--badge-red)" style={{ flexShrink: 0, marginTop: '2px' }} />
              <div style={{ fontSize: '12px', color: 'var(--text-main)', lineHeight: 1.5 }}>
                {t(d.message)}
                <code style={{ marginLeft: '8px', fontSize: '10px', color: 'var(--text-sub)' }}>{d.code}</code>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 目录加载失败 */}
      {catalogError && (
        <div role="alert" style={{ background: 'var(--badge-red-bg)', border: '1px solid rgba(248, 81, 73, 0.4)', borderRadius: 'var(--radius-md)', padding: '12px 16px', fontSize: '12px', color: 'var(--badge-red)' }}>
          {catalogError}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 1fr) minmax(320px, 1fr)', gap: '20px', alignItems: 'start' }}>
        {/* 左列：生成器选择 */}
        <div data-testid="generator-catalog" style={{ background: 'var(--bg-panel)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
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
                      <span style={{ fontSize: '12px', textTransform: 'capitalize' }}>
                        {g.loader} {g.minecraftVersion}
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
        </div>

        {/* 右列：表单 */}
        <div style={{ background: 'var(--bg-panel)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, margin: 0 }}>工作区信息</h3>

          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
            模组名称
            <input
              type="text"
              data-testid="new-workspace-mod-name-input"
              value={modName}
              onChange={(e) => setModName(e.target.value)}
              placeholder="例如 Copper Trails"
              maxLength={64}
              style={{ padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
            />
          </label>

          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
            模组 ID（modid）
            <input
              type="text"
              data-testid="new-workspace-mod-id-input"
              value={modId}
              onChange={(e) => setModId(e.target.value.toLowerCase())}
              placeholder="例如 copper_trails"
              maxLength={32}
              style={{ padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
            />
            <span style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
              2-32 位小写字母、数字或下划线，用作工作区文件名与 mod ID。
            </span>
          </label>

          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
            Java 包名
            <input
              type="text"
              data-testid="new-workspace-package-input"
              value={packageName}
              onChange={(e) => {
                setPackageTouched(true);
                setPackageName(e.target.value);
              }}
              placeholder={`net.mcreator.${modId || 'mymod'}`}
              style={{ padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
            />
            <span style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
              留空时自动使用 net.mcreator.&lt;modid&gt;（与旧版对话框一致）。
            </span>
          </label>

          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
            工作区文件夹
            <div style={{ display: 'flex', gap: '8px' }}>
              <input
                type="text"
                data-testid="new-workspace-folder-input"
                value={workspaceFolder}
                onChange={(e) => setWorkspaceFolder(e.target.value)}
                placeholder={suggestedFolder ?? (catalog?.suggestedWorkspaceFoldersRoot ?? 'C:\\Users\\you\\MCreatorWorkspaces')}
                style={{ flex: 1, padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
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
            <span style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
              {catalog ? (
                <>必须位于建议根目录之下：<code>{catalog.suggestedWorkspaceFoldersRoot}</code>{suggestedFolder && <>（默认 <code>{suggestedFolder}</code>）</>}</>
              ) : '必须位于建议的工作区根目录之下。'}
            </span>
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
            type="button"
            className="btn-primary"
            data-testid="create-workspace-submit-btn"
            disabled={!canSubmit}
            onClick={() => void handleSubmit()}
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
    </div>
  );
};
