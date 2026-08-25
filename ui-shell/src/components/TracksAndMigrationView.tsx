import React, { useState, useEffect, useMemo } from 'react';
import {
  Compass,
  ShieldCheck,
  AlertTriangle,
  FileArchive,
  Info,
  CheckCircle2,
  Play,
  Plus,
  Lock
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { t } from '../i18n';
import {
  VersionTracksProjection,
  VersionTrack,
  TrackLoader,
  TrackStatus,
  MigrationReport,
  MigrationDisposition,
  MigrationItem,
  PublishBatch,
  CommandResult,
  ClientLoadPreparation
} from '../types/contract';

type U3Tab = 'matrix' | 'migration' | 'upstream' | 'publish';

const sanitizeOutputName = (raw: string): string => {
  return raw.toLowerCase().replace(/[^a-z0-9_-]/g, '_').replace(/^_+/, 'w_').slice(0, 64);
};

export const TracksAndMigrationView: React.FC = () => {
  const {
    state,
    getVersionTracks,
    previewLoaderMigration,
    executeLoaderMigration,
    previewUpstreamImport,
    importUpstreamWorkspace,
    listPublishBatches,
    createPublishBatch,
    prepareResourcePackClient,
    elevatePermission,
    runDiagnosticAction
  } = useWorkbench();

  const [activeTab, setActiveTab] = useState<U3Tab>('matrix');

  // Track Matrix state
  const [tracksData, setTracksData] = useState<VersionTracksProjection | null>(state.versionTracks);

  // Migration state
  const [targetGeneratorId, setTargetGeneratorId] = useState<string>('');
  const [migrationOutputName, setMigrationOutputName] = useState<string>('workspace_migrated_copy');
  const [migrationPreview, setMigrationPreview] = useState<MigrationReport | null>(null);
  const [migrationLoading, setMigrationLoading] = useState(false);
  const [migrationConfirmed, setMigrationConfirmed] = useState(false);
  const [migrationResult, setMigrationResult] = useState<CommandResult | null>(null);

  // Upstream Import state
  const [upstreamSourcePath, setUpstreamSourcePath] = useState<string>('fixtures/upstream/sample_workspace');
  const [upstreamOutputName, setUpstreamOutputName] = useState<string>('workspace_imported_copy');
  const [upstreamPreview, setUpstreamPreview] = useState<MigrationReport | null>(null);
  const [upstreamLoading, setUpstreamLoading] = useState(false);
  const [upstreamConfirmed, setUpstreamConfirmed] = useState(false);
  const [upstreamResult, setUpstreamResult] = useState<CommandResult | null>(null);

  // Publish Batches state
  const [batches, setBatches] = useState<PublishBatch[]>(state.publishBatches);
  const [isNewBatchModalOpen, setIsNewBatchModalOpen] = useState(false);
  const [newBatchName, setNewBatchName] = useState('pack_release_v1');
  const [newBatchSourceDir, setNewBatchSourceDir] = useState('src/main/resources');
  const [newBatchOutput, setNewBatchOutput] = useState('build/distributions/copperbench-assets-1.0.0.zip');
  const [clientPreparationNotice, setClientPreparationNotice] = useState<string | null>(null);

  const renderActionableDiagnostics = (result: CommandResult | null, testId: string) => {
    const diagnostics = result?.diagnostics.filter((diagnostic) =>
      diagnostic.actions.length > 0 || diagnostic.message.args?.failureId != null) ?? [];
    if (diagnostics.length === 0) return null;
    return (
      <div
        role="alert"
        data-testid={testId}
        style={{
          background: 'var(--badge-red-bg)',
          border: '1px solid rgba(248, 81, 73, 0.4)',
          borderRadius: 'var(--radius-md)',
          padding: '14px 18px',
          display: 'flex',
          flexDirection: 'column',
          gap: '10px'
        }}
      >
        {diagnostics.map((diagnostic) => (
          <div key={diagnostic.code} style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
            <AlertTriangle size={20} color="var(--badge-red)" aria-hidden="true" style={{ flexShrink: 0 }} />
            <div style={{ minWidth: 0, display: 'flex', flexDirection: 'column', gap: '7px' }}>
              <div style={{ fontSize: '12px', color: 'var(--text-main)', lineHeight: 1.5 }}>
                {t(diagnostic.message)}
                {diagnostic.message.args?.failureId != null && (
                  <code style={{ marginLeft: '8px', fontSize: '10px', color: 'var(--text-sub)', overflowWrap: 'anywhere' }}>
                    错误编号：{String(diagnostic.message.args.failureId)}
                  </code>
                )}
              </div>
              {diagnostic.actions.length > 0 && (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                  {diagnostic.actions.map((action) => (
                    <button
                      key={action.id}
                      type="button"
                      className="btn-primary"
                      style={{ fontSize: '11px', padding: '4px 10px' }}
                      onClick={() => runDiagnosticAction(action, diagnostic)}
                    >
                      {t(action.label)}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    );
  };

  // Load version tracks on mount
  useEffect(() => {
    let isMounted = true;
    const fetchTracks = async () => {
      const data = await getVersionTracks();
      if (isMounted && data) {
        setTracksData(data);
      }
    };
    void fetchTracks();
    return () => {
      isMounted = false;
    };
  }, [getVersionTracks]);

  const currentGeneratorId = tracksData?.currentWorkspace?.generator?.id
    ?? state.workbench?.workspace.generator.id;
  const migrationTargets = useMemo(() => {
    const targets = new Map<string, { loader: TrackLoader; track: VersionTrack }>();
    for (const track of tracksData?.tracks ?? []) {
      for (const loader of track.loaders) {
        if (loader.loader === 'resource_pack' || loader.status === 'unavailable'
          || loader.generatorId === currentGeneratorId) continue;
        targets.set(loader.generatorId, { loader, track });
      }
    }
    return [...targets.values()];
  }, [currentGeneratorId, tracksData]);

  useEffect(() => {
    if (migrationTargets.length === 0 || migrationTargets.some(({ loader }) => loader.generatorId === targetGeneratorId)) {
      return;
    }
    const currentVersion = tracksData?.currentWorkspace?.generator.minecraftVersion
      ?? state.workbench?.workspace.generator.minecraftVersion;
    const preferred = migrationTargets.find(({ loader }) => loader.minecraftVersion === currentVersion)
      ?? migrationTargets[0];
    setTargetGeneratorId(preferred.loader.generatorId);
    setMigrationOutputName(sanitizeOutputName(`workspace_${preferred.loader.generatorId}`));
  }, [migrationTargets, state.workbench?.workspace.generator.minecraftVersion, targetGeneratorId, tracksData]);

  // Load publish batches on tab switch or mount
  useEffect(() => {
    let isMounted = true;
    const fetchBatches = async () => {
      const data = await listPublishBatches();
      if (isMounted && data) {
        setBatches(data.items);
      }
    };
    if (activeTab === 'publish') {
      void fetchBatches();
    }
    return () => {
      isMounted = false;
    };
  }, [activeTab, listPublishBatches]);

  // Handle migration preview
  const handlePreviewMigration = async (targetId?: string) => {
    const id = targetId || targetGeneratorId;
    setMigrationLoading(true);
    setMigrationResult(null);
    try {
      const preview = await previewLoaderMigration(id);
      setMigrationPreview(preview);
    } finally {
      setMigrationLoading(false);
    }
  };

  // Handle migration execution
  const handleExecuteMigration = async () => {
    if (!migrationConfirmed) return;
    setMigrationLoading(true);
    try {
      const sanitizedName = sanitizeOutputName(migrationOutputName);
      const result = await executeLoaderMigration(targetGeneratorId, sanitizedName, migrationConfirmed);
      setMigrationResult(result);
    } finally {
      setMigrationLoading(false);
    }
  };

  // Handle upstream preview
  const handlePreviewUpstream = async () => {
    setUpstreamLoading(true);
    setUpstreamResult(null);
    try {
      const preview = await previewUpstreamImport(upstreamSourcePath);
      setUpstreamPreview(preview);
    } finally {
      setUpstreamLoading(false);
    }
  };

  // Handle upstream import execution
  const handleExecuteUpstreamImport = async () => {
    if (!upstreamConfirmed) return;
    setUpstreamLoading(true);
    try {
      const sanitizedName = sanitizeOutputName(upstreamOutputName);
      const result = await importUpstreamWorkspace(upstreamSourcePath, sanitizedName, upstreamConfirmed);
      setUpstreamResult(result);
    } finally {
      setUpstreamLoading(false);
    }
  };

  // Handle new publish batch
  const handleCreateBatch = async () => {
    if (!newBatchName.trim()) return;
    const sanitizedName = sanitizeOutputName(newBatchName);
    const result = await createPublishBatch(sanitizedName, newBatchSourceDir, newBatchOutput);
    if (result.status === 'committed') {
      setIsNewBatchModalOpen(false);
      const updated = await listPublishBatches();
      if (updated) {
        setBatches(updated.items);
      }
    }
  };

  // Handle prepare client
  const handlePrepareClient = async (batch: PublishBatch) => {
    setClientPreparationNotice(null);
    const result = await prepareResourcePackClient(batch.sourceDirectory, `${batch.name}.zip`);
    if (result.status === 'committed') {
      const prep = result.data as ClientLoadPreparation | undefined;
      if (prep?.clientLaunched === false) {
        setClientPreparationNotice('已就绪，尚未启动客户端');
      }
    }
  };

  const getStatusBadge = (status: TrackStatus) => {
    switch (status) {
      case 'supported':
        return <span className="badge badge-green" data-testid="status-supported">正式支持</span>;
      case 'preview':
        return <span className="badge badge-amber" data-testid="status-preview">技术预览</span>;
      case 'unavailable':
        return <span className="badge badge-red" data-testid="status-unavailable">暂不可用</span>;
      case 'coincides':
        return <span className="badge badge-blue" data-testid="status-coincides">并轨共用</span>;
      default:
        return <span className="badge">{status}</span>;
    }
  };

  const getDispositionLabel = (disp: MigrationDisposition) => {
    switch (disp) {
      case 'supported':
        return { label: '完全支持', badgeClass: 'badge-green' };
      case 'substitute':
        return { label: '等价替换', badgeClass: 'badge-blue' };
      case 'lost':
        return { label: '丢失 / 降级', badgeClass: 'badge-amber' };
      case 'blocked':
        return { label: '阻断', badgeClass: 'badge-red' };
      case 'manual':
        return { label: '需手动处理', badgeClass: 'badge-copper' };
    }
  };

  const isCurrentGenerator = (loader: TrackLoader) => {
    return currentGeneratorId === loader.generatorId;
  };

  return (
    <div className="tracks-migration-view animate-fade-in" data-testid="tracks-view" style={{ flex: 1, padding: '24px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ padding: '8px', borderRadius: 'var(--radius-md)', background: 'var(--accent-copper-dim)', color: 'var(--accent-copper)' }}>
            <Compass size={24} />
          </div>
          <div>
            <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0 }}>版本轨道与工作区迁移</h2>
            <p style={{ fontSize: '12px', color: 'var(--text-sub)', margin: '4px 0 0 0' }}>
              Minecraft 4轨版本矩阵 · 跨加载器副本迁移 · 上游迁入与资源包发布
            </p>
          </div>
        </div>

        {/* Tab Switcher */}
        <div style={{ display: 'flex', gap: '6px', background: 'var(--bg-panel)', padding: '4px', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
          <button
            type="button"
            className={`btn-ghost ${activeTab === 'matrix' ? 'is-active' : ''}`}
            data-testid="tab-track-matrix"
            onClick={() => setActiveTab('matrix')}
            style={{ fontSize: '12px', padding: '6px 12px', borderRadius: 'var(--radius-sm)', background: activeTab === 'matrix' ? 'var(--accent-copper-dim)' : 'transparent', color: activeTab === 'matrix' ? 'var(--accent-copper)' : 'var(--text-main)', fontWeight: activeTab === 'matrix' ? 600 : 400 }}
          >
            版本轨道矩阵
          </button>
          <button
            type="button"
            className={`btn-ghost ${activeTab === 'migration' ? 'is-active' : ''}`}
            data-testid="tab-loader-migration"
            onClick={() => setActiveTab('migration')}
            style={{ fontSize: '12px', padding: '6px 12px', borderRadius: 'var(--radius-sm)', background: activeTab === 'migration' ? 'var(--accent-copper-dim)' : 'transparent', color: activeTab === 'migration' ? 'var(--accent-copper)' : 'var(--text-main)', fontWeight: activeTab === 'migration' ? 600 : 400 }}
          >
            加载器迁移
          </button>
          <button
            type="button"
            className={`btn-ghost ${activeTab === 'upstream' ? 'is-active' : ''}`}
            data-testid="tab-upstream-import"
            onClick={() => setActiveTab('upstream')}
            style={{ fontSize: '12px', padding: '6px 12px', borderRadius: 'var(--radius-sm)', background: activeTab === 'upstream' ? 'var(--accent-copper-dim)' : 'transparent', color: activeTab === 'upstream' ? 'var(--accent-copper)' : 'var(--text-main)', fontWeight: activeTab === 'upstream' ? 600 : 400 }}
          >
            上游工作区迁入
          </button>
          <button
            type="button"
            className={`btn-ghost ${activeTab === 'publish' ? 'is-active' : ''}`}
            data-testid="tab-publish-batches"
            onClick={() => setActiveTab('publish')}
            style={{ fontSize: '12px', padding: '6px 12px', borderRadius: 'var(--radius-sm)', background: activeTab === 'publish' ? 'var(--accent-copper-dim)' : 'transparent', color: activeTab === 'publish' ? 'var(--accent-copper)' : 'var(--text-main)', fontWeight: activeTab === 'publish' ? 600 : 400 }}
          >
            资源包发布批次
          </button>
        </div>
      </div>

      {/* Tab 1: Version Track Matrix */}
      {activeTab === 'matrix' && (
        <div className="track-matrix-content animate-fade-in" data-testid="track-matrix-section" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: 'var(--bg-panel)', padding: '12px 16px', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px' }}>
              <Info size={16} color="var(--accent-copper)" />
              <span>当前工作区所用生成器：</span>
              <code style={{ background: 'var(--bg-canvas)', padding: '2px 8px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', fontWeight: 600 }}>
                {tracksData?.currentWorkspace?.generator?.displayName ?? state.workbench?.workspace.generator.displayName ?? '生成器信息不可用'}
                {' '}({tracksData?.currentWorkspace?.generator?.id ?? state.workbench?.workspace.generator.id ?? 'unknown'})
              </code>
            </div>
            <div style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
              {tracksData
                ? `版本策略：最新稳定轨 ${tracksData.latestMinecraftVersion} · 前一稳定轨 ${tracksData.previousMinecraftVersion} · 维护轨`
                : '正在读取版本轨道策略…'}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '16px' }}>
            {tracksData?.tracks.map((track: VersionTrack) => (
              <div
                key={track.id}
                className="track-card"
                data-testid={`track-card-${track.id}`}
                style={{
                  background: 'var(--bg-panel)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-md)',
                  padding: '16px',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '12px'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '10px' }}>
                  <div>
                    <h3 style={{ fontSize: '14px', fontWeight: 700, margin: 0 }}>{track.displayName}</h3>
                    <div style={{ fontSize: '11px', color: 'var(--text-sub)', marginTop: '2px' }}>
                      Minecraft {track.minecraftVersion} {track.dynamic && '· 动态轨'}
                    </div>
                  </div>
                  <span className={`badge ${track.dynamic ? 'badge-copper' : 'badge-blue'}`} style={{ fontSize: '10px' }}>
                    {track.id}
                  </span>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {track.loaders.map((loader: TrackLoader) => {
                    const isCurrent = isCurrentGenerator(loader);
                    return (
                      <div
                        key={`${track.id}-${loader.loader}`}
                        data-testid={`loader-row-${track.id}-${loader.loader}`}
                        style={{
                          background: isCurrent ? 'var(--accent-copper-dim)' : 'var(--bg-canvas)',
                          border: isCurrent ? '1px solid var(--accent-copper)' : '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-sm)',
                          padding: '10px 12px',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '6px'
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <strong style={{ textTransform: 'capitalize', fontSize: '13px' }}>{loader.loader}</strong>
                            <code style={{ fontSize: '11px', color: 'var(--text-sub)' }}>{loader.generatorId}</code>
                            {isCurrent && <span className="badge badge-copper" style={{ fontSize: '9px' }}>当前工作区</span>}
                          </div>
                          {getStatusBadge(loader.status)}
                        </div>

                        <div style={{ fontSize: '11px', color: 'var(--text-main)', lineHeight: 1.4 }}>
                          {loader.notes}
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '4px', paddingTop: '4px', borderTop: '1px dashed var(--border-subtle)' }}>
                          <span style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                            代码: <code style={{ color: 'var(--accent-copper)' }}>{loader.reasonCode}</code>
                          </span>
                          {loader.status !== 'unavailable' && !isCurrent && (
                            <button
                              type="button"
                              className="btn-ghost"
                              data-testid={`migrate-to-${loader.generatorId}`}
                              style={{ fontSize: '11px', padding: '2px 8px', color: 'var(--accent-copper)' }}
                              onClick={() => {
                                setTargetGeneratorId(loader.generatorId);
                                setMigrationOutputName(sanitizeOutputName(`workspace_${loader.generatorId}`));
                                setActiveTab('migration');
                                void handlePreviewMigration(loader.generatorId);
                              }}
                            >
                              准备迁移至此加载器 &rarr;
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tab 2: Loader Migration */}
      {activeTab === 'migration' && (
        <div className="migration-content animate-fade-in" data-testid="loader-migration-section" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {/* Top Info Alert */}
          <div role="note" style={{ background: 'var(--badge-blue-bg)', border: '1px solid rgba(88, 166, 255, 0.4)', borderRadius: 'var(--radius-md)', padding: '12px 16px', display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
            <ShieldCheck size={18} color="var(--badge-blue)" style={{ flexShrink: 0, marginTop: '2px' }} />
            <div style={{ fontSize: '12px', color: 'var(--text-main)', lineHeight: 1.5 }}>
              <strong>安全拷贝保证：</strong> 当前工作区完全只读，不会被修改或破坏。迁移结果将写入新的副本目录。即便目标生成器处于技术预览阶段（<code>complete=false</code>），原工作区仍完好无损（<code>sourceUnchanged: true</code>）。
            </div>
          </div>

          {/* Configuration Form */}
          <div style={{ background: 'var(--bg-panel)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <h3 style={{ fontSize: '14px', fontWeight: 700, margin: 0 }}>选择迁移目标生成器</h3>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '12px' }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
                目标生成器
                <select
                  data-testid="migration-target-select"
                  value={targetGeneratorId}
                  onChange={(e) => {
                    const nextId = e.target.value;
                    setTargetGeneratorId(nextId);
                    setMigrationOutputName(sanitizeOutputName(`workspace_${nextId}`));
                    setMigrationPreview(null);
                    setMigrationConfirmed(false);
                    setMigrationResult(null);
                  }}
                  style={{ padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
                >
                  {migrationTargets.map(({ loader, track }) => (
                    <option key={loader.generatorId} value={loader.generatorId}>
                      {loader.loader === 'neoforge' ? 'NeoForge' : 'Fabric'} {loader.minecraftVersion}
                      {' · '}{track.id === 'latest_stable' ? '最新稳定轨' : track.id === 'previous_stable' ? '前一稳定轨' : '维护轨'}
                      {' · '}{loader.status === 'supported' ? '正式支持' : loader.status === 'preview' ? '技术预览' : '并轨共用'}
                    </option>
                  ))}
                  {migrationTargets.length === 0 && <option value="">没有可用的迁移目标</option>}
                </select>
              </label>

              <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
                新工作区副本名称
                <input
                  type="text"
                  data-testid="migration-output-name-input"
                  value={migrationOutputName}
                  onChange={(e) => setMigrationOutputName(e.target.value)}
                  placeholder={targetGeneratorId ? sanitizeOutputName(`workspace_${targetGeneratorId}`) : 'workspace_migrated_copy'}
                  style={{ padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
                />
              </label>
            </div>

            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
              <button
                type="button"
                className="btn-secondary"
                data-testid="preview-migration-btn"
                onClick={() => void handlePreviewMigration()}
                disabled={migrationLoading || !targetGeneratorId}
                style={{ fontSize: '12px', padding: '6px 14px' }}
              >
                {migrationLoading ? '正在分析差异…' : '分析跨加载器迁移差异'}
              </button>
            </div>
          </div>

          {/* Migration Preview Report */}
          {migrationPreview && (
            <div data-testid="migration-preview-report" style={{ background: 'var(--bg-panel)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '16px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '10px' }}>
                <div>
                  <h4 style={{ fontSize: '14px', fontWeight: 700, margin: 0 }}>
                    迁移可行性报告：{migrationPreview.sourceGeneratorId} &rarr; {migrationPreview.targetGeneratorId}
                  </h4>
                  <div style={{ fontSize: '11px', color: 'var(--text-sub)', marginTop: '2px' }}>
                    源工作区保持只读未受影响（SHA-256: {migrationPreview.sourceHash ? migrationPreview.sourceHash.slice(0, 16) + '…' : '已校验'}）
                  </div>
                </div>
                <div>
                  {migrationPreview.complete ? (
                    <span className="badge badge-green" data-testid="preview-complete-badge">可完全迁移</span>
                  ) : (
                    <span className="badge badge-amber" data-testid="preview-incomplete-badge">部分特性需适配</span>
                  )}
                </div>
              </div>

              {/* Disposition Groups */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                {(['supported', 'substitute', 'lost', 'blocked', 'manual'] as MigrationDisposition[]).map((disp) => {
                  const items = migrationPreview.items.filter((i: MigrationItem) => i.disposition === disp);
                  if (items.length === 0) return null;
                  const info = getDispositionLabel(disp);
                  return (
                    <div key={disp} data-testid={`disposition-group-${disp}`} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span className={`badge ${info.badgeClass}`} style={{ fontSize: '11px' }}>
                          {info.label} ({items.length})
                        </span>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', paddingLeft: '8px' }}>
                        {items.map((item: MigrationItem) => (
                          <div
                            key={item.path}
                            data-testid={`migration-item-${item.name}`}
                            style={{
                              background: 'var(--bg-canvas)',
                              border: '1px solid var(--border-subtle)',
                              borderRadius: 'var(--radius-sm)',
                              padding: '8px 12px',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              fontSize: '12px'
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                              <strong style={{ color: 'var(--text-main)' }}>{item.name}</strong>
                              <code style={{ fontSize: '10px', color: 'var(--text-sub)' }}>{item.path}</code>
                              <span className="badge" style={{ fontSize: '10px' }}>{item.type}</span>
                              <span style={{ color: 'var(--accent-copper)', fontSize: '11px' }}>{item.reasonCode}</span>
                            </div>
                            <div style={{ fontSize: '11px', color: 'var(--text-sub)', maxWidth: '45%' }}>
                              {item.nextStep}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Explicit User Confirmation */}
              <div style={{ background: 'var(--bg-canvas)', padding: '12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '12px', fontWeight: 600 }}>
                  <input
                    type="checkbox"
                    data-testid="confirm-migration-checkbox"
                    checked={migrationConfirmed}
                    onChange={(e) => setMigrationConfirmed(e.target.checked)}
                  />
                  <span>我已知晓目标生成器差异，并确认执行迁移并写入新副本目录</span>
                </label>

                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <button
                    type="button"
                    className="btn-primary"
                    data-testid="execute-migration-btn"
                    disabled={!migrationConfirmed || migrationLoading}
                    onClick={() => void handleExecuteMigration()}
                    style={{ fontSize: '12px', padding: '6px 16px' }}
                  >
                    {migrationLoading ? '正在执行迁移…' : '执行加载器迁移并生成新副本'}
                  </button>
                  {!migrationConfirmed && (
                    <span style={{ fontSize: '11px', color: 'var(--badge-amber)' }}>
                      必须勾选确认后方可执行迁移
                    </span>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Migration Success Result Banner */}
          {renderActionableDiagnostics(migrationResult, 'migration-diagnostics-banner')}

          {migrationResult && migrationResult.status === 'committed' && migrationResult.data?.complete && (
            <div data-testid="migration-success-banner" style={{ background: 'var(--badge-green-bg)', border: '1px solid rgba(63, 185, 80, 0.4)', borderRadius: 'var(--radius-md)', padding: '14px 18px', display: 'flex', alignItems: 'center', gap: '12px' }}>
              <CheckCircle2 size={20} color="var(--badge-green)" />
              <div>
                <strong style={{ fontSize: '13px', color: 'var(--text-main)' }}>加载器迁移已完成！</strong>
                <div style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
                  新工作区已生成至 <code>{migrationResult.data?.targetDirectory ?? `workspaces/${migrationOutputName}`}</code>，源工作区保持只读未受任何修改。
                </div>
              </div>
            </div>
          )}

          {/* Migration Incomplete / Rejected Banner */}
          {migrationResult && (migrationResult.status === 'rejected' || (migrationResult.data && !migrationResult.data.complete)) && (
            <div data-testid="migration-incomplete-banner" style={{ background: 'var(--badge-amber-bg)', border: '1px solid rgba(210, 153, 34, 0.4)', borderRadius: 'var(--radius-md)', padding: '14px 18px', display: 'flex', alignItems: 'center', gap: '12px' }}>
              <AlertTriangle size={20} color="var(--badge-amber)" />
              <div>
                <strong style={{ fontSize: '13px', color: 'var(--text-main)' }}>目标生成器尚不支持完全自动迁移</strong>
                <div style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
                  迁移未完成（complete=false），源工作区保持只读未受任何修改 (sourceUnchanged: true)。
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Tab 3: Upstream Import */}
      {activeTab === 'upstream' && (
        <div className="upstream-import-content animate-fade-in" data-testid="upstream-import-section" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {/* Desktop Full Access Notice */}
          <div role="alert" style={{ background: 'var(--badge-amber-bg)', border: '1px solid rgba(210, 153, 34, 0.4)', borderRadius: 'var(--radius-md)', padding: '12px 16px', display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
            <AlertTriangle size={18} color="var(--badge-amber)" style={{ flexShrink: 0, marginTop: '2px' }} />
            <div style={{ fontSize: '12px', color: 'var(--text-main)', lineHeight: 1.5 }}>
              <strong>环境约束说明：</strong> 迁入外部上游工作区需要桌面宿主提供的系统文件选择器与 Full Access 权限。在浏览器与模拟环境下，直接文件系统选择器处于禁用状态。
            </div>
          </div>

          <div style={{ background: 'var(--bg-panel)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <h3 style={{ fontSize: '14px', fontWeight: 700, margin: 0 }}>上游工作区迁入配置</h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
                上游工作区路径（模拟夹具固定路径）
                <div style={{ display: 'flex', gap: '8px' }}>
                  <input
                    type="text"
                    data-testid="upstream-path-input"
                    value={upstreamSourcePath}
                    onChange={(e) => setUpstreamSourcePath(e.target.value)}
                    style={{ flex: 1, padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
                  />
                  <button
                    type="button"
                    className="btn-secondary"
                    data-testid="upstream-browse-btn"
                    disabled
                    title="仅桌面 Full Access 宿主可用"
                    style={{ fontSize: '12px', padding: '6px 12px', opacity: 0.6 }}
                  >
                    <Lock size={12} style={{ marginRight: '4px' }} />
                    浏览… (仅桌面 Full Access)
                  </button>
                </div>
              </label>

              <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', fontWeight: 600 }}>
                新工作区副本名称
                <input
                  type="text"
                  data-testid="upstream-output-name-input"
                  value={upstreamOutputName}
                  onChange={(e) => setUpstreamOutputName(e.target.value)}
                  placeholder="workspace_imported_copy"
                  style={{ padding: '8px 10px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
                />
              </label>
            </div>

            <div style={{ display: 'flex', gap: '10px' }}>
              <button
                type="button"
                className="btn-secondary"
                data-testid="preview-upstream-btn"
                onClick={() => void handlePreviewUpstream()}
                disabled={upstreamLoading}
                style={{ fontSize: '12px', padding: '6px 14px' }}
              >
                {upstreamLoading ? '正在分析上游工作区…' : '分析上游工程兼容性'}
              </button>
            </div>
          </div>

          {/* Upstream Preview Report */}
          {upstreamPreview && (
            <div data-testid="upstream-preview-report" style={{ background: 'var(--bg-panel)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '10px' }}>
                <div>
                  <h4 style={{ fontSize: '14px', fontWeight: 700, margin: 0 }}>
                    检测到上游格式：{upstreamPreview.sourceGeneratorId}
                  </h4>
                  <div style={{ fontSize: '11px', color: 'var(--text-sub)', marginTop: '2px' }}>
                    源工作区保持只读未受影响（SHA-256: {upstreamPreview.sourceHash ? upstreamPreview.sourceHash.slice(0, 16) + '…' : '已校验'}）
                  </div>
                </div>
                <span className="badge badge-green">可安全迁入</span>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {upstreamPreview.items.map((item: MigrationItem) => (
                  <div
                    key={item.path}
                    style={{
                      background: 'var(--bg-canvas)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                      padding: '8px 12px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      fontSize: '12px'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <strong>{item.name}</strong>
                      <code style={{ fontSize: '10px', color: 'var(--text-sub)' }}>{item.path}</code>
                      <span className="badge" style={{ fontSize: '10px' }}>{item.type}</span>
                      <span style={{ color: 'var(--accent-copper)', fontSize: '11px' }}>{item.reasonCode}</span>
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-sub)', maxWidth: '45%' }}>
                      {item.nextStep}
                    </div>
                  </div>
                ))}
              </div>

              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '12px', fontWeight: 600 }}>
                <input
                  type="checkbox"
                  data-testid="confirm-upstream-checkbox"
                  checked={upstreamConfirmed}
                  onChange={(e) => setUpstreamConfirmed(e.target.checked)}
                />
                <span>确认迁入上游工程至新副本</span>
              </label>

              <button
                type="button"
                className="btn-primary"
                data-testid="import-upstream-btn"
                disabled={!upstreamConfirmed || upstreamLoading}
                onClick={() => void handleExecuteUpstreamImport()}
                style={{ fontSize: '12px', padding: '6px 16px', alignSelf: 'flex-start' }}
              >
                {upstreamLoading ? '正在迁入…' : '开始迁入并创建独立副本'}
              </button>
            </div>
          )}

          {/* Upstream Denial Banner */}
          {renderActionableDiagnostics(upstreamResult, 'upstream-diagnostics-banner')}

          {upstreamResult && upstreamResult.status === 'rejected' && upstreamResult.denial && (
            <div data-testid="upstream-denial-banner" style={{ background: 'var(--badge-red-bg)', border: '1px solid rgba(248, 81, 73, 0.4)', borderRadius: 'var(--radius-md)', padding: '14px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <AlertTriangle size={20} color="var(--badge-red)" />
                <div>
                  <strong style={{ fontSize: '13px', color: 'var(--badge-red)' }}>权限不足（PERMISSION_DENIED）</strong>
                  <div style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
                    迁入上游工作区需要桌面 Full Access 权限（当前配置: {upstreamResult.denial.currentProfile}，所需配置: {upstreamResult.denial.requiredProfile}）。
                  </div>
                </div>
              </div>
              <button
                type="button"
                className="btn-primary"
                data-testid="elevate-full-access-btn"
                onClick={() => {
                  elevatePermission('full_access');
                  setUpstreamResult(null);
                }}
                style={{ fontSize: '11px', padding: '4px 12px' }}
              >
                提升至 Full Access
              </button>
            </div>
          )}

          {/* Upstream Success Banner */}
          {upstreamResult && upstreamResult.status === 'committed' && (
            <div data-testid="upstream-success-banner" style={{ background: 'var(--badge-green-bg)', border: '1px solid rgba(63, 185, 80, 0.4)', borderRadius: 'var(--radius-md)', padding: '14px 18px', display: 'flex', alignItems: 'center', gap: '12px' }}>
              <CheckCircle2 size={20} color="var(--badge-green)" />
              <div>
                <strong style={{ fontSize: '13px', color: 'var(--text-main)' }}>上游工作区迁入成功！</strong>
                <div style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
                  已创建新副本至 <code>{upstreamResult.data?.targetDirectory ?? `workspaces/${upstreamOutputName}`}</code>。
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Tab 4: Resource Pack Publish Batches */}
      {activeTab === 'publish' && (
        <div className="publish-batches-content animate-fade-in" data-testid="publish-batches-section" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <h3 style={{ fontSize: '15px', fontWeight: 700, margin: 0 }}>资源包发布与分发批次</h3>
              <p style={{ fontSize: '12px', color: 'var(--text-sub)', margin: '2px 0 0 0' }}>
                打包与验证独立资源包 · 支持导出与测试客户端挂载
              </p>
            </div>
            <button
              type="button"
              className="btn-primary"
              data-testid="new-batch-btn"
              onClick={() => setIsNewBatchModalOpen(true)}
              style={{ fontSize: '12px', padding: '6px 14px', display: 'flex', alignItems: 'center', gap: '6px' }}
            >
              <Plus size={14} />
              <span>新建发布批次</span>
            </button>
          </div>

          {clientPreparationNotice && (
            <div data-testid="client-preparation-notice" style={{ background: 'var(--badge-blue-bg)', border: '1px solid rgba(88, 166, 255, 0.4)', borderRadius: 'var(--radius-md)', padding: '12px 16px', display: 'flex', alignItems: 'center', gap: '10px', fontSize: '12px', color: 'var(--text-main)' }}>
              <Info size={16} color="var(--badge-blue)" />
              <span>{clientPreparationNotice}</span>
            </div>
          )}

          {batches.length === 0 ? (
            <div
              data-testid="publish-batch-empty"
              style={{
                background: 'var(--bg-panel)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '48px 24px',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '12px',
                textAlign: 'center'
              }}
            >
              <FileArchive size={36} color="var(--accent-copper)" />
              <strong style={{ fontSize: '14px', color: 'var(--text-main)' }}>暂无资源包发布批次</strong>
              <p style={{ fontSize: '12px', color: 'var(--text-sub)', maxWidth: '400px', margin: 0 }}>
                创建发布批次以打包和验证独立资源包，支持生成 distribution zip 并装载到测试客户端。
              </p>
              <button
                type="button"
                className="btn-secondary"
                data-testid="create-first-batch-btn"
                onClick={() => setIsNewBatchModalOpen(true)}
                style={{ fontSize: '12px', padding: '6px 14px' }}
              >
                新建第一个发布批次
              </button>
            </div>
          ) : (
            <div data-testid="publish-batch-list" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {batches.map((batch: PublishBatch) => (
                <div
                  key={batch.id}
                  data-testid={`publish-batch-card-${batch.id}`}
                  style={{
                    background: 'var(--bg-panel)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-md)',
                    padding: '16px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '10px'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <FileArchive size={18} color="var(--accent-copper)" />
                      <strong style={{ fontSize: '14px' }}>{batch.name}</strong>
                      <span className="badge">资源数: {batch.assetCount}</span>
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-sub)' }}>
                      {batch.createdAt}
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '10px', fontSize: '12px', color: 'var(--text-sub)' }}>
                    <div>源目录: <code>{batch.sourceDirectory}</code></div>
                    <div>输出: <code>{batch.outputPath}</code></div>
                    <div>SHA-256: <code>{batch.sha256 ? batch.sha256.slice(0, 16) + '…' : ''}</code></div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderTop: '1px solid var(--border-subtle)', paddingTop: '10px' }}>
                    <div style={{ fontSize: '12px', color: 'var(--text-main)' }}>
                      客户端状态：<span data-testid={`batch-client-status-${batch.id}`} style={{ color: 'var(--accent-copper)', fontWeight: 600 }}>已就绪，尚未启动客户端</span>
                    </div>
                    <button
                      type="button"
                      className="btn-secondary"
                      data-testid={`prepare-client-${batch.id}`}
                      onClick={() => void handlePrepareClient(batch)}
                      style={{ fontSize: '11px', padding: '4px 10px', display: 'flex', alignItems: 'center', gap: '6px' }}
                    >
                      <Play size={12} />
                      <span>准备测试客户端</span>
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* New Batch Modal */}
          {isNewBatchModalOpen && (
            <div
              className="modal-overlay"
              data-testid="new-batch-modal"
              style={{
                position: 'fixed',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                background: 'rgba(0, 0, 0, 0.65)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                zIndex: 1000
              }}
            >
              <div
                style={{
                  background: 'var(--bg-panel)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-lg)',
                  padding: '24px',
                  width: '440px',
                  maxWidth: '90vw',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '16px'
                }}
              >
                <h3 style={{ fontSize: '16px', fontWeight: 700, margin: 0 }}>新建资源包发布批次</h3>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '12px', fontWeight: 600 }}>
                    批次标识名称
                    <input
                      type="text"
                      data-testid="new-batch-name-input"
                      value={newBatchName}
                      onChange={(e) => setNewBatchName(e.target.value)}
                      placeholder="pack_v1"
                      style={{ padding: '8px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
                    />
                  </label>

                  <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '12px', fontWeight: 600 }}>
                    资源源目录
                    <input
                      type="text"
                      data-testid="new-batch-source-input"
                      value={newBatchSourceDir}
                      onChange={(e) => setNewBatchSourceDir(e.target.value)}
                      style={{ padding: '8px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
                    />
                  </label>

                  <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '12px', fontWeight: 600 }}>
                    发布输出目标 (.zip)
                    <input
                      type="text"
                      data-testid="new-batch-output-input"
                      value={newBatchOutput}
                      onChange={(e) => setNewBatchOutput(e.target.value)}
                      style={{ padding: '8px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--bg-canvas)', color: 'var(--text-main)', fontSize: '12px' }}
                    />
                  </label>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}>
                  <button
                    type="button"
                    className="btn-ghost"
                    onClick={() => setIsNewBatchModalOpen(false)}
                    style={{ fontSize: '12px', padding: '6px 14px' }}
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    className="btn-primary"
                    data-testid="confirm-create-batch-btn"
                    onClick={() => void handleCreateBatch()}
                    style={{ fontSize: '12px', padding: '6px 14px' }}
                  >
                    确认创建
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
