import { UI_LOCALE } from '../i18n/locale';
import React, { useEffect, useRef, useState } from 'react';
import {
  LayoutDashboard,
  Box,
  Palette,
  GitBranch,
  Bot,
  Plug,
  Sparkles,
  Terminal,
  Compass,
  Layers,
  Database,
  HelpCircle,
  Settings
} from 'lucide-react';
import { useWorkbench, NavView } from '../context/WorkbenchContext';
import { windowBridge } from '../bridge/windowBridge';
import { uiText } from '../i18n';

export const NavRail: React.FC = () => {
  const { activeView, setActiveView, state } = useWorkbench();
  const elementCount = state.elements.length;
  const permission = state.workbench?.permission?.profile ?? 'workspace';
  const aiNavigationRef = useRef<HTMLButtonElement | null>(null);
  const [settingsError, setSettingsError] = useState(false);

  useEffect(() => {
    const openAiNavigation = (event: KeyboardEvent) => {
      if (!event.ctrlKey || !event.shiftKey || event.altKey || event.metaKey || event.key.toLowerCase() !== 'm') return;
      event.preventDefault();
      setActiveView('ai');
      requestAnimationFrame(() => aiNavigationRef.current?.focus());
    };
    window.addEventListener('keydown', openAiNavigation);
    return () => window.removeEventListener('keydown', openAiNavigation);
  }, [setActiveView]);

  const navItems: {
    id: NavView | 'settings';
    label: string;
    icon: React.ComponentType<{ size: number }>;
    badge?: string | number;
    badgeType?: 'copper' | 'blue' | 'green';
  }[] = [
    { id: 'hub', label: uiText('总览', 'Overview'), icon: LayoutDashboard },
    { id: 'elements', label: uiText('模组元素', 'Mod elements'), icon: Box, badge: elementCount, badgeType: 'copper' },
    { id: 'data', label: uiText('变量与数据', 'Variables & data'), icon: Database, badge: uiText('引用', 'Refs'), badgeType: 'green' },
    { id: 'tracks', label: uiText('版本与迁移', 'Version tracks'), icon: Compass, badge: uiText('4轨', '4'), badgeType: 'copper' },
    { id: 'new-workspace', label: uiText('新建工作区', 'New workspace'), icon: Layers, badge: '4×2', badgeType: 'blue' },
    { id: 'assets', label: uiText('资产与模型', 'Assets & models'), icon: Palette },
    { id: 'python', label: uiText('Python 工作台', 'Python workbench'), icon: Terminal },
    { id: 'history', label: uiText('本地历史', 'Local history'), icon: GitBranch },
    { id: 'ai', label: uiText('AI 与 MCP', 'AI & MCP'), icon: Bot, badge: permission === 'workspace' ? uiText('读写', 'RW') : permission === 'full_access' ? uiText('完全', 'Full') : uiText('只读', 'RO'), badgeType: 'green' },
    { id: 'plugins', label: uiText('插件中心', 'Plugins'), icon: Plug, badge: 'A/B/C', badgeType: 'blue' },
    { id: 'help', label: uiText('帮助与关于', 'Help & about'), icon: HelpCircle, badge: '0.1.1', badgeType: 'copper' },
    { id: 'settings', label: uiText('设置', 'Settings'), icon: Settings }
  ];

  return (
    <aside
      className="nav-rail"
      data-testid="nav-rail"
      style={{
        width: UI_LOCALE === 'en' ? '232px' : '190px',
        background: 'var(--navrail-bg)',
        borderRight: '1px solid var(--border-subtle)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: '12px 8px',
        flexShrink: 0,
        userSelect: 'none'
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <div
          className="nav-section-label"
          style={{
            padding: '4px 10px 10px 10px',
            fontSize: '11px',
            fontWeight: 700,
            textTransform: 'uppercase',
            letterSpacing: '0',
            color: 'var(--text-sub)'
          }}
        >
          {uiText('导航', 'Navigation')}
        </div>

        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeView === item.id;
          return (
            <button
              key={item.id}
              ref={item.id === 'ai' ? aiNavigationRef : undefined}
              type="button"
              onClick={() => {
                if (item.id === 'settings') {
                  setSettingsError(false);
                  void windowBridge.openPreferences().catch(() => setSettingsError(true));
                } else setActiveView(item.id);
              }}
              disabled={item.id === 'settings' && !windowBridge.canOpenPreferences}
              aria-label={item.label}
              title={item.id === 'settings' && !windowBridge.canOpenPreferences ? uiText('设置仅在支持此功能的桌面应用中可用', 'Settings are available in supported desktop hosts') : item.label}
              aria-current={isActive ? 'page' : undefined}
              aria-keyshortcuts={item.id === 'ai' ? 'Control+Shift+M' : undefined}
              data-testid={`nav-${item.id}`}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '8px 12px',
                borderRadius: 'var(--radius-md)',
                background: isActive ? 'var(--accent-copper-dim)' : 'transparent',
                color: isActive ? 'var(--accent-copper)' : 'var(--text-muted)',
                fontWeight: isActive ? 600 : 500,
                border: isActive ? '1px solid rgba(200, 122, 62, 0.3)' : '1px solid transparent',
                textAlign: 'left',
                transition: 'all 0.15s ease'
              }}
            >
              <div className="nav-item-content" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Icon size={16} />
                <span className="nav-item-label" style={{ fontSize: '12px' }}>{item.label}</span>
              </div>

              {item.badge !== undefined && (
                <span
                  className={`nav-item-badge badge badge-${item.badgeType || 'copper'}`}
                  style={{ fontSize: '10px', padding: '1px 6px' }}
                >
                  {item.badge}
                </span>
              )}
            </button>
          );
        })}
        {settingsError && <div role="alert" style={{ padding: '8px', fontSize: '12px' }}>{uiText('无法打开设置，请重试或重新启动应用。', 'Could not open settings. Retry or restart the app.')}</div>}
      </div>

      {/* Bottom Info Card */}
      <div
        className="nav-bottom-info"
        style={{
          background: 'var(--bg-panel)',
          border: '1px solid var(--border-subtle)',
          borderRadius: 'var(--radius-md)',
          padding: '10px 12px',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--accent-copper)', fontSize: '11px', fontWeight: 600 }}>
          <Sparkles size={12} />
          <span>{uiText('模组创作工作台', 'Mod creation workbench')}</span>
        </div>
        <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
          {state.currentScenarioId === 'native' ? uiText('JCEF 原生桥接 · 协议 v1.0', 'JCEF bridge · Protocol v1.0') : uiText('Mock 桥接已连接 · 协议 v1.0', 'Mock bridge · Protocol v1.0')}
        </div>
      </div>
    </aside>
  );
};
