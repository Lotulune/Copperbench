import React from 'react';
import {
  LayoutDashboard,
  Box,
  Palette,
  GitBranch,
  Bot,
  Plug,
  Sparkles,
  Compass,
  Layers,
  HelpCircle
} from 'lucide-react';
import { useWorkbench, NavView } from '../context/WorkbenchContext';

export const NavRail: React.FC = () => {
  const { activeView, setActiveView, state } = useWorkbench();
  const elementCount = state.elements.length;
  const permission = state.workbench?.permission?.profile ?? 'workspace';

  const navItems: {
    id: NavView;
    label: string;
    icon: React.ComponentType<{ size: number }>;
    badge?: string | number;
    badgeType?: 'copper' | 'blue' | 'green';
  }[] = [
    { id: 'hub', label: '总览', icon: LayoutDashboard },
    { id: 'elements', label: '模组元素', icon: Box, badge: elementCount, badgeType: 'copper' },
    { id: 'tracks', label: '版本与迁移', icon: Compass, badge: '4轨', badgeType: 'copper' },
    { id: 'new-workspace', label: '新建工作区', icon: Layers, badge: '4×2', badgeType: 'blue' },
    { id: 'assets', label: '资产与模型', icon: Palette, badge: 'Stage 6', badgeType: 'blue' },
    { id: 'history', label: '本地历史', icon: GitBranch, badge: 'JGit', badgeType: 'blue' },
    { id: 'ai', label: 'AI 与 MCP', icon: Bot, badge: permission === 'workspace' ? 'WS' : permission === 'full_access' ? 'FULL' : 'RO', badgeType: 'green' },
    { id: 'plugins', label: '插件中心', icon: Plug, badge: 'A/B/C', badgeType: 'blue' },
    { id: 'help', label: '帮助与关于', icon: HelpCircle, badge: '0.1.0', badgeType: 'copper' }
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
              onClick={() => setActiveView(item.id)}
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
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Icon size={16} />
                <span style={{ fontSize: '12px' }}>{item.label}</span>
              </div>

              {item.badge !== undefined && (
                <span
                  className={`badge badge-${item.badgeType || 'copper'}`}
                  style={{ fontSize: '10px', padding: '1px 6px' }}
                >
                  {item.badge}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Bottom Info Card */}
      <div
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
          <span>UI-Core 工作台</span>
        </div>
        <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
          {state.currentScenarioId === 'native' ? 'JCEF 原生桥接 · 协议 v1.0' : 'Mock 桥接已连接 · 协议 v1.0'}
        </div>
      </div>
    </aside>
  );
};
