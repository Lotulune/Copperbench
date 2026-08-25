import React from 'react';
import {
  HelpCircle,
  Info,
  ShieldAlert,
  Layers,
  Box,
  Palette,
  GitBranch,
  Bot,
  Plug,
  Compass,
  Download,
  ArrowRight,
  ShieldCheck,
  CheckCircle2,
  AlertTriangle
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import {
  USER_GUIDE_METADATA,
  ABOUT_FACTS,
  TRACK_HONEST_FACTS,
  USER_GUIDE_SECTIONS,
  UserGuideSection
} from '../content/userGuide';

export const HelpView: React.FC = () => {
  const { setActiveView } = useWorkbench();

  const getSectionIcon = (id: string) => {
    switch (id) {
      case 'workspace':
        return <Layers size={18} color="var(--accent-copper)" aria-hidden="true" />;
      case 'version-tracks':
        return <Compass size={18} color="var(--accent-copper)" aria-hidden="true" />;
      case 'mod-elements':
        return <Box size={18} color="var(--accent-copper)" aria-hidden="true" />;
      case 'local-history':
        return <GitBranch size={18} color="var(--badge-blue)" aria-hidden="true" />;
      case 'mcp-permissions':
        return <Bot size={18} color="var(--badge-green)" aria-hidden="true" />;
      case 'blockbench-assets':
        return <Palette size={18} color="var(--badge-blue)" aria-hidden="true" />;
      case 'loader-migration':
        return <Compass size={18} color="var(--badge-amber)" aria-hidden="true" />;
      case 'plugins':
        return <Plug size={18} color="var(--badge-blue)" aria-hidden="true" />;
      case 'install-uninstall':
        return <Download size={18} color="var(--accent-copper)" aria-hidden="true" />;
      default:
        return <HelpCircle size={18} color="var(--accent-copper)" aria-hidden="true" />;
    }
  };

  return (
    <div
      className="help-view animate-fade-in"
      data-testid="help-view"
      style={{
        flex: 1,
        padding: '24px',
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
        gap: '24px'
      }}
    >
      {/* Header & Source Notice */}
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: '12px',
          borderBottom: '1px solid var(--border-subtle)',
          paddingBottom: '16px'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                padding: '8px',
                borderRadius: 'var(--radius-md)',
                background: 'var(--accent-copper-dim)',
                color: 'var(--accent-copper)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              <HelpCircle size={24} aria-hidden="true" />
            </div>
            <div>
              <h1 style={{ fontSize: '20px', fontWeight: 700, margin: 0, color: 'var(--text-main)' }}>
                帮助与使用说明
              </h1>
              <p style={{ fontSize: '12px', color: 'var(--text-sub)', margin: '4px 0 0 0' }}>
                Copperbench 0.1.0 使用指南 · 4轨版本矩阵 · 安全约束与插件分级
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span className="badge badge-copper" style={{ fontSize: '11px', padding: '3px 8px' }}>
              协议 v1.0
            </span>
            <span className="badge badge-blue" style={{ fontSize: '11px', padding: '3px 8px' }}>
              开发测试版
            </span>
          </div>
        </div>

        {/* Source document callout */}
        <div
          role="note"
          style={{
            background: 'var(--bg-panel)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-md)',
            padding: '10px 14px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            fontSize: '12px',
            color: 'var(--text-muted)'
          }}
        >
          <Info size={16} color="var(--accent-copper)" style={{ flexShrink: 0 }} aria-hidden="true" />
          <span style={{ lineHeight: 1.5 }}>
            <strong>源文档声明：</strong> 源文档是 <code>{USER_GUIDE_METADATA.sourceDoc}</code>。这是开发测试版说明，不是商店发行手册。产品名 <code>Copperbench</code> 是公开名称。公开分发走 GitHub，安装包未签名。
          </span>
        </div>
      </div>

      {/* About Panel Card */}
      <section
        className="about-panel"
        data-testid="about-panel"
        aria-labelledby="about-panel-heading"
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border-subtle)',
          borderRadius: 'var(--radius-lg)',
          padding: '20px 24px',
          display: 'flex',
          flexDirection: 'column',
          gap: '16px',
          boxShadow: 'var(--shadow-sm)'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldCheck size={18} color="var(--accent-copper)" aria-hidden="true" />
            <h2 id="about-panel-heading" style={{ fontSize: '16px', fontWeight: 700, margin: 0, color: 'var(--text-main)' }}>
              关于 Copperbench
            </h2>
          </div>
          <span className="badge badge-copper" style={{ fontSize: '11px' }}>
            Facts Only
          </span>
        </div>

        {/* Facts Grid */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '12px' }}>
          {ABOUT_FACTS.map((fact) => (
            <div
              key={fact.label}
              style={{
                background: 'var(--bg-panel)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '12px 14px',
                display: 'flex',
                flexDirection: 'column',
                gap: '4px'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: '11px', color: 'var(--text-sub)', fontWeight: 600 }}>
                  {fact.label}
                </span>
                {fact.badge && (
                  <span className={`badge badge-${fact.badgeType ?? 'copper'}`} style={{ fontSize: '10px' }}>
                    {fact.badge}
                  </span>
                )}
              </div>
              <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-main)' }}>
                {fact.value}
              </div>
              {fact.description && (
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                  {fact.description}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Notice regarding unsigned development build */}
        <div
          style={{
            background: 'var(--badge-amber-bg)',
            border: '1px solid rgba(210, 153, 34, 0.3)',
            borderRadius: 'var(--radius-md)',
            padding: '10px 14px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '10px',
            fontSize: '12px',
            color: 'var(--text-main)'
          }}
        >
          <ShieldAlert size={16} color="var(--badge-amber)" style={{ flexShrink: 0, marginTop: '2px' }} aria-hidden="true" />
          <div style={{ lineHeight: 1.5 }}>
            <strong>版本事实声明：</strong> Copperbench 0.1.0 采用 GPL-3.0-only 协议开源，独立衍生自 MCreator 2026.2.33518。当前为开发测试版（未生产签名，GitHub 分发政策如此），UI 仅通过 UI-Core 1.0 协议与 Java 领域服务交互，不直接访问本机文件系统。
          </div>
        </div>
      </section>

      {/* Honest Track Status Section */}
      <section
        aria-labelledby="tracks-honest-heading"
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border-subtle)',
          borderRadius: 'var(--radius-lg)',
          padding: '20px 24px',
          display: 'flex',
          flexDirection: 'column',
          gap: '14px',
          boxShadow: 'var(--shadow-sm)'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Compass size={18} color="var(--accent-copper)" aria-hidden="true" />
            <h2 id="tracks-honest-heading" style={{ fontSize: '15px', fontWeight: 700, margin: 0, color: 'var(--text-main)' }}>
              版本轨道真实状态矩阵 (4轨并进)
            </h2>
          </div>

          <button
            type="button"
            className="btn-secondary"
            onClick={() => setActiveView('tracks')}
            data-testid="help-to-tracks-btn"
            style={{ fontSize: '11px', padding: '4px 10px', display: 'flex', alignItems: 'center', gap: '6px' }}
          >
            <span>进入版本与迁移工作台</span>
            <ArrowRight size={12} aria-hidden="true" />
          </button>
        </div>

        <p style={{ fontSize: '12px', color: 'var(--text-muted)', margin: 0, lineHeight: 1.5 }}>
          版本轨道状态与 UI-Core 严格一致：新项目应优先选择目录中标记为正式支持的最新稳定轨。实际 Fabric / NeoForge 版本以“版本轨道”与“新建工作区”页面的 Core 投影为准。仅支持 Windows 11 x64。
        </p>

        <div style={{ overflowX: 'auto' }}>
          <table
            data-testid="help-tracks-table"
            style={{
              width: '100%',
              borderCollapse: 'collapse',
              fontSize: '12px'
            }}
          >
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-active)', textAlign: 'left' }}>
                <th style={{ padding: '8px 10px', width: '130px' }}>轨道名称</th>
                <th style={{ padding: '8px 10px', width: '110px' }}>Minecraft 版本</th>
                <th style={{ padding: '8px 10px', width: '220px' }}>支持状态</th>
                <th style={{ padding: '8px 10px' }}>详细说明与依据</th>
              </tr>
            </thead>
            <tbody>
              {TRACK_HONEST_FACTS.map((track) => (
                <tr
                  key={track.trackName}
                  data-testid={`help-track-row-${track.minecraftVersion}`}
                  style={{
                    borderBottom: '1px solid var(--border-subtle)',
                    background: track.isGolden ? 'var(--accent-copper-dim)' : 'transparent'
                  }}
                >
                  <td style={{ padding: '10px 10px', fontWeight: 600, color: 'var(--text-main)' }}>
                    {track.trackName}
                  </td>
                  <td style={{ padding: '10px 10px', color: 'var(--text-sub)' }}>
                    {track.minecraftVersion}
                  </td>
                  <td style={{ padding: '10px 10px' }}>
                    <span className={`badge badge-${track.isGolden ? 'green' : 'amber'}`}>
                      {track.isGolden ? <CheckCircle2 size={11} aria-hidden="true" /> : <AlertTriangle size={11} aria-hidden="true" />}
                      <span>{track.statusLabel}</span>
                    </span>
                  </td>
                  <td style={{ padding: '10px 10px', color: 'var(--text-muted)' }}>
                    {track.notes} <code style={{ fontSize: '10px', color: 'var(--accent-copper)' }}>({track.statusCode})</code>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Complete User Guide Sections */}
      <section
        aria-labelledby="user-guide-sections-heading"
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: '16px'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Layers size={18} color="var(--accent-copper)" aria-hidden="true" />
          <h2 id="user-guide-sections-heading" style={{ fontSize: '16px', fontWeight: 700, margin: 0, color: 'var(--text-main)' }}>
            功能模块详解 (基于 docs/user/README.md)
          </h2>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '16px' }}>
          {USER_GUIDE_SECTIONS.map((section: UserGuideSection) => (
            <div
              key={section.id}
              data-testid={`guide-section-${section.id}`}
              style={{
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '16px 18px',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                gap: '12px'
              }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '8px' }}>
                  {getSectionIcon(section.id)}
                  <h3 style={{ fontSize: '14px', fontWeight: 700, margin: 0, color: 'var(--text-main)' }}>
                    {section.title}
                  </h3>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  {section.content.map((paragraph, idx) => (
                    <p
                      key={idx}
                      style={{
                        fontSize: '12px',
                        color: 'var(--text-muted)',
                        lineHeight: 1.5,
                        margin: 0
                      }}
                    >
                      {paragraph}
                    </p>
                  ))}
                </div>

                {section.table && (
                  <div style={{ overflowX: 'auto', marginTop: '4px' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '11px' }}>
                      <thead>
                        <tr style={{ borderBottom: '1px solid var(--border-active)', textAlign: 'left' }}>
                          {section.table.headers.map((h, i) => (
                            <th key={i} style={{ padding: '6px 8px' }}>{h}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {section.table.rows.map((r, i) => (
                          <tr key={i} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                            {r.map((cell, j) => (
                              <td key={j} style={{ padding: '6px 8px', color: j === 0 ? 'var(--text-main)' : 'var(--text-muted)' }}>
                                {cell}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              {section.linkView && section.linkLabel && (
                <div style={{ paddingTop: '8px', borderTop: '1px dashed var(--border-subtle)' }}>
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => setActiveView(section.linkView!)}
                    data-testid={`guide-link-${section.id}`}
                    style={{
                      fontSize: '11px',
                      padding: '4px 10px',
                      width: '100%',
                      justifyContent: 'space-between'
                    }}
                  >
                    <span>{section.linkLabel}</span>
                    <ArrowRight size={12} aria-hidden="true" />
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
};
