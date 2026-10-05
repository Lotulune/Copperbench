import { tr, uiText } from '../i18n/locale';
import React, { useState } from 'react';
import { ArrowRight, FileArchive, LoaderCircle } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { diagnosticsBridge } from '../bridge/diagnosticsBridge';
import { ABOUT_FACTS, TRACK_HONEST_FACTS, USER_GUIDE_SECTIONS } from '../content/userGuide';

export const HelpView: React.FC = () => {
  const { setActiveView } = useWorkbench();
  const [includeWorkspaceFiles, setIncludeWorkspaceFiles] = useState(false);
  const [exportingDiagnostics, setExportingDiagnostics] = useState(false);
  const [diagnosticExportStatus, setDiagnosticExportStatus] = useState<string | null>(null);

  const exportDiagnostics = async () => {
    setExportingDiagnostics(true);
    setDiagnosticExportStatus(null);
    try {
      const result = await diagnosticsBridge.exportBundle(includeWorkspaceFiles);
      setDiagnosticExportStatus(tr("已导出 {0}{1}。", [result.fileName, result.includedWorkspaceFiles ? `，附加 ${result.reproductionFileCount} 个复现文件` : '']));
    } catch (error) {
      setDiagnosticExportStatus(error instanceof Error ? error.message : tr("诊断包导出失败。"));
    } finally {
      setExportingDiagnostics(false);
    }
  };

  return <div className="help-view" data-testid="help-view" style={{ flex: 1, minWidth: 0, padding: '24px', overflowY: 'auto' }}>
    <div style={{ width: '100%', maxWidth: 980, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
      <h1 style={{ margin: 0, fontSize: 22, fontWeight: 550 }}>{uiText('帮助', 'Help')}</h1>

      <section data-testid="diagnostic-support-panel" aria-labelledby="diagnostic-support-heading"
        style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 16, paddingBottom: 20, borderBottom: '1px solid var(--border-subtle)' }}>
        <div style={{ minWidth: 0 }}>
          <h2 id="diagnostic-support-heading" style={{ margin: '0 0 10px', fontSize: 14 }}>{tr("诊断与反馈")}</h2>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--text-muted)' }}>
            <input type="checkbox" checked={includeWorkspaceFiles} onChange={event => setIncludeWorkspaceFiles(event.target.checked)} data-testid="diagnostic-include-workspace" />
            <span>{uiText('附加复现文件（可能含源码与内容）', 'Include reproduction files (may contain source and content)')}</span>
          </label>
          {diagnosticExportStatus && <div role="status" aria-live="polite" data-testid="diagnostic-export-status" style={{ marginTop: 8, fontSize: 12 }}>{diagnosticExportStatus}</div>}
        </div>
        <button type="button" className="btn-primary" onClick={() => void exportDiagnostics()}
          disabled={!diagnosticsBridge.available || exportingDiagnostics} data-testid="diagnostic-export-btn"
          title={diagnosticsBridge.available ? tr("将诊断包保存到本机并打开文件位置") : tr("仅桌面宿主可导出诊断包")}>
          {exportingDiagnostics ? <LoaderCircle className="spin" size={14} aria-hidden="true" /> : <FileArchive size={14} aria-hidden="true" />}
          <span>{exportingDiagnostics ? tr("正在导出") : tr("导出脱敏诊断包")}</span>
        </button>
      </section>

      <section aria-labelledby="user-guide-sections-heading">
        <h2 id="user-guide-sections-heading" style={{ fontSize: 14, margin: '0 0 8px' }}>{uiText('使用指南', 'User guide')}</h2>
        {USER_GUIDE_SECTIONS.map(section => <details key={section.id} data-testid={`guide-section-${section.id}`}
          style={{ borderBottom: '1px solid var(--border-subtle)', fontSize: 13 }}>
          <summary style={{ cursor: 'pointer', padding: '12px 0', fontWeight: 500 }}>{section.title}</summary>
          <div style={{ padding: '0 0 14px 16px', color: 'var(--text-muted)', lineHeight: 1.6 }}>
            {section.content.map((paragraph, index) => <p key={index} style={{ margin: '0 0 8px' }}>{paragraph}</p>)}
            {section.table && <div style={{ overflowX: 'auto', margin: '12px 0' }}><table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead><tr>{section.table.headers.map((heading, index) => <th key={index} style={{ padding: 8, textAlign: 'left' }}>{heading}</th>)}</tr></thead>
              <tbody>{section.table.rows.map((row, index) => <tr key={index}>{row.map((cell, column) => <td key={column} style={{ padding: 8, borderTop: '1px solid var(--border-subtle)' }}>{cell}</td>)}</tr>)}</tbody>
            </table></div>}
            {section.linkView && section.linkLabel && <button type="button" className="btn-secondary" onClick={() => setActiveView(section.linkView!)} data-testid={`guide-link-${section.id}`}>
              {section.linkLabel}<ArrowRight size={12} aria-hidden="true" />
            </button>}
          </div>
        </details>)}
      </section>

      <section aria-labelledby="help-versions-heading">
        <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
          <h2 id="help-versions-heading" style={{ fontSize: 14, margin: 0 }}>{uiText('Minecraft 版本', 'Minecraft versions')}</h2>
          <button type="button" className="btn-secondary" onClick={() => setActiveView('tracks')} data-testid="help-to-tracks-btn">{uiText('版本与迁移', 'Versions & migration')}<ArrowRight size={12} aria-hidden="true" /></button>
        </div>
        <details data-testid="help-track-details" style={{ marginTop: 10 }}>
          <summary style={{ cursor: 'pointer', fontSize: 13 }}>{uiText('查看支持状态', 'View supported versions')}</summary>
          <div style={{ overflowX: 'auto', marginTop: 10 }}><table data-testid="help-tracks-table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
            <thead><tr style={{ textAlign: 'left' }}>
              <th style={{ padding: 8 }}>{tr("轨道名称")}</th><th style={{ padding: 8 }}>{tr("Minecraft 版本")}</th>
              <th style={{ padding: 8 }}>{tr("支持状态")}</th><th style={{ padding: 8 }}>{tr("说明")}</th>
            </tr></thead>
            <tbody>{TRACK_HONEST_FACTS.map(track => <tr key={track.trackName} data-testid={`help-track-row-${track.minecraftVersion}`} style={{ borderTop: '1px solid var(--border-subtle)' }}>
              <td style={{ padding: 8 }}>{track.trackName}</td><td style={{ padding: 8 }}>{track.minecraftVersion}</td>
              <td style={{ padding: 8 }}>{track.statusLabel}</td><td style={{ padding: 8, color: 'var(--text-muted)' }}>{track.notes}</td>
            </tr>)}</tbody>
          </table></div>
        </details>
      </section>

      <section className="about-panel" data-testid="about-panel" aria-labelledby="about-panel-heading" style={{ paddingTop: 18, borderTop: '1px solid var(--border-subtle)' }}>
        <h2 id="about-panel-heading" style={{ fontSize: 14, margin: '0 0 12px' }}>{tr("关于 Copperbench")}</h2>
        <dl style={{ margin: 0, display: 'flex', flexDirection: 'column', gap: 9, fontSize: 12 }}>
          {ABOUT_FACTS.map(fact => <div key={fact.label} style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 16px' }}>
            <dt style={{ width: 145, color: 'var(--text-muted)' }}>{fact.label}</dt><dd style={{ margin: 0 }} title={fact.description}>{fact.value}</dd>
          </div>)}
        </dl>
      </section>
    </div>
  </div>;
};
