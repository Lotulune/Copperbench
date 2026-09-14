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

export const NavRail: React.FC = () => {
  const { activeView, setActiveView, state } = useWorkbench();
  const elementCount = state.elements.length;
  const permission = state.workbench?.permission?.profile ?? 'workspace';
  const aiNavigationRef = useRef<HTMLButtonElement | null>(null);
  const [settingsError, setSettingsError] = useState('');

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
    { id: 'hub', label: '总览', icon: LayoutDashboard },
    { id: 'elements', label: '模组元素', icon: Box, badge: elementCount, badgeType: 'copper' },
    { id: 'data', label: '变量与数据', icon: Database, badge: '引用', badgeType: 'green' },
    { id: 'tracks', label: '版本与迁移', icon: Compass, badge: '4轨', badgeType: 'copper' },
    { id: 'new-workspace', label: '新建工作区', icon: Layers, badge: '4×2', badgeType: 'blue' },
    { id: 'assets', label: '资产与模型', icon: Palette },
    { id: 'python', label: 'Python 工作台', icon: Terminal },
    { id: 'history', label: '本地历史', icon: GitBranch },
    { id: 'ai', label: 'AI 与 MCP', icon: Bot, badge: permission === 'workspace' ? '读写' : permission === 'full_access' ? '完全' : '只读', badgeType: 'green' },
    { id: 'plugins', label: '插件中心', icon: Plug, badge: 'A/B/C', badgeType: 'blue' },
    { id: 'help', label: '帮助与关于', icon: HelpCircle, badge: '0.1.0', badgeType: 'copper' },
    { id: 'settings', label: '设置', icon: Settings }
  ];

  return (
    <aside
      className="nav-rail"
      data-testid="nav-rail"
      style={{
        width: '190px',
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
          导航
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
                  setSettingsError('');
                  void windowBridge.openPreferences().catch(() => setSettingsError('无法打开设置，请重试或重新启动应用。'));
                } else setActiveView(item.id);
              }}
              disabled={item.id === 'settings' && !windowBridge.canOpenPreferences}
              aria-label={item.label}
              title={item.id === 'settings' && !windowBridge.canOpenPreferences ? '设置仅在支持此功能的桌面应用中可用' : item.label}
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
        {settingsError && <div role="alert" style={{ padding: '8px', fontSize: '12px' }}>{settingsError}</div>}
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
          <span>模组创作工作台</span>
        </div>
        <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
          {state.currentScenarioId === 'native' ? 'JCEF 原生桥接 · 协议 v1.0' : 'Mock 桥接已连接 · 协议 v1.0'}
        </div>
      </div>
    </aside>
  );
};
