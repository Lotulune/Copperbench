import React, { useEffect, useRef, useState } from 'react';
import {
  LayoutDashboard, Box, Palette, GitBranch, Bot, Plug, Terminal,
  Compass, Plus, Database, HelpCircle, Settings, FolderOpen
} from 'lucide-react';
import { useWorkbench, NavView } from '../context/WorkbenchContext';
import { windowBridge } from '../bridge/windowBridge';
import { uiText } from '../i18n';

type NavigationItem = {
  id: NavView | 'settings';
  label: string;
  icon: React.ComponentType<{ size: number; 'aria-hidden'?: boolean }>;
  count?: number;
};

export const NavRail: React.FC = () => {
  const { activeView, setActiveView, state } = useWorkbench();
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

  const groups: { label: string; items: NavigationItem[] }[] = [
    { label: uiText('创作', 'CREATE'), items: [
      { id: 'hub', label: uiText('总览', 'Overview'), icon: LayoutDashboard },
      { id: 'elements', label: uiText('模组元素', 'Mod elements'), icon: Box, count: state.elements.length },
      { id: 'assets', label: uiText('资产与模型', 'Assets & models'), icon: Palette },
      { id: 'data', label: uiText('变量与数据', 'Variables & data'), icon: Database }
    ] },
    { label: uiText('项目', 'PROJECT'), items: [
      { id: 'history', label: uiText('本地历史', 'Local history'), icon: GitBranch },
      { id: 'tracks', label: uiText('版本与迁移', 'Version tracks'), icon: Compass }
    ] },
    { label: uiText('工具', 'TOOLS'), items: [
      { id: 'ai', label: uiText('AI 与 MCP', 'AI & MCP'), icon: Bot },
      { id: 'python', label: uiText('Python 工作台', 'Python workbench'), icon: Terminal },
      { id: 'plugins', label: uiText('插件中心', 'Plugins'), icon: Plug }
    ] }
  ];

  const renderItem = (item: NavigationItem) => {
    const Icon = item.icon;
    const disabled = item.id === 'settings' && !windowBridge.canOpenPreferences;
    return (
      <button key={item.id} type="button" className="nav-item"
        ref={item.id === 'ai' ? aiNavigationRef : undefined}
        onClick={() => {
          if (item.id === 'settings') {
            setSettingsError(false);
            void windowBridge.openPreferences().catch(() => setSettingsError(true));
          } else setActiveView(item.id);
        }}
        disabled={disabled}
        aria-label={item.label}
        title={disabled ? uiText('设置仅在桌面应用中可用', 'Settings are available in the desktop app') : item.label}
        aria-current={activeView === item.id ? 'page' : undefined}
        aria-keyshortcuts={item.id === 'ai' ? 'Control+Shift+M' : undefined}
        data-testid={`nav-${item.id}`}>
        <Icon size={18} aria-hidden={true} />
        <span className="nav-item-label">{item.label}</span>
        {item.count !== undefined && <span className="nav-item-badge">{item.count}</span>}
      </button>
    );
  };

  return (
    <aside className="nav-rail" data-testid="nav-rail">
      <div className="nav-workspace" title={state.workbench?.workspace.name}>
        <div className="nav-workspace-icon"><FolderOpen size={19} aria-hidden="true" /></div>
        <div className="nav-workspace-copy">
          <strong>{state.workbench?.workspace.name || uiText('工作区', 'Workspace')}</strong>
          <span>{state.workbench?.workspace.generator?.displayName || 'Copperbench'}</span>
        </div>
      </div>
      <button className="nav-new-workspace" type="button" data-testid="nav-new-workspace"
        aria-label={uiText('新建工作区', 'New workspace')}
        title={uiText('新建工作区', 'New workspace')}
        aria-current={activeView === 'new-workspace' ? 'page' : undefined}
        onClick={() => setActiveView('new-workspace')}>
        <Plus size={16} aria-hidden="true" /><span className="nav-item-label">{uiText('新建工作区', 'New workspace')}</span>
      </button>
      <nav className="nav-groups" aria-label={uiText('主导航', 'Main navigation')}>
        {groups.map(group => <div className="nav-group" key={group.label}>
          <div className="nav-section-label">{group.label}</div>
          {group.items.map(renderItem)}
        </div>)}
      </nav>
      <div className="nav-utilities">
        {renderItem({ id: 'help', label: uiText('帮助与关于', 'Help & about'), icon: HelpCircle })}
        {renderItem({ id: 'settings', label: uiText('设置', 'Settings'), icon: Settings })}
        {settingsError && <div role="alert">{uiText('无法打开设置，请重试。', 'Could not open settings. Please retry.')}</div>}
      </div>
    </aside>
  );
};
