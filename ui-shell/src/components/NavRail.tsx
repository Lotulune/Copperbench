import React, { useEffect, useRef, useState } from 'react';
import { ChevronDown, Plus } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { uiText } from '../i18n';
import { workbenchNavigation } from './workbenchNavigation';
import { WorkspaceIconChooser } from './WorkspaceIconChooser';

export const NavRail: React.FC = () => {
  const { activeView, setActiveView, state } = useWorkbench();
  const [toolsOpen, setToolsOpen] = useState(false);
  const aiNavigationRef = useRef<HTMLButtonElement | null>(null);
  const navigation = workbenchNavigation();
  useEffect(() => {
    const openAi = (event: KeyboardEvent) => {
      if (!event.ctrlKey || !event.shiftKey || event.altKey || event.metaKey || event.key.toLowerCase() !== 'm') return;
      event.preventDefault(); setToolsOpen(true); setActiveView('ai');
      requestAnimationFrame(() => aiNavigationRef.current?.focus());
    };
    window.addEventListener('keydown', openAi);
    return () => window.removeEventListener('keydown', openAi);
  }, [setActiveView]);
  useEffect(() => {
    if (['ai', 'python', 'plugins', 'tracks'].includes(activeView)) setToolsOpen(true);
  }, [activeView]);
  const renderItem = (item: ReturnType<typeof workbenchNavigation>[number]) => <button key={item.id}
    type="button" className="nav-item" ref={item.id === 'ai' ? aiNavigationRef : undefined}
    onClick={() => setActiveView(item.id)} aria-label={item.label} title={item.label}
    aria-current={activeView === item.id ? 'page' : undefined}
    aria-keyshortcuts={item.id === 'ai' ? 'Control+Shift+M' : undefined} data-testid={`nav-${item.id}`}>
    <item.icon size={16} aria-hidden="true" /><span className="nav-item-label">{item.label}</span>
    {item.id === 'elements' && <span className="nav-item-badge">{state.workbench?.elementCounts.total ?? state.elements.length}</span>}
  </button>;

  return <aside className="nav-rail" data-testid="nav-rail">
    <div className="nav-workspace" title={state.workbench?.workspace.name}>
      <WorkspaceIconChooser key={state.workbench?.workspace.id ?? 'none'} workspaceId={state.workbench?.workspace.id ?? 'none'} name={state.workbench?.workspace.name ?? uiText('工作区', 'Workspace')} />
      <div className="nav-workspace-copy"><strong>{state.workbench?.workspace.name || uiText('工作区', 'Workspace')}</strong>
        <span>{state.workbench?.workspace.generator?.displayName || 'Copperbench'}</span></div>
      <button type="button" className="nav-workspace-add" data-testid="nav-new-workspace"
        aria-label={uiText('新建工作区', 'New workspace')} title={uiText('新建工作区', 'New workspace')}
        onClick={() => setActiveView('new-workspace')}><Plus size={15} aria-hidden="true" /></button>
    </div>
    <nav className="nav-groups" aria-label={uiText('主导航', 'Main navigation')}>
      <div className="nav-group">{navigation.filter(item => item.group === 'project').map(renderItem)}</div>
      <div className="nav-group nav-tools-group">
        <button type="button" className="nav-tools-toggle" data-testid="nav-tools-toggle" aria-expanded={toolsOpen} aria-controls="workbench-tools"
          aria-label={uiText('展开或收起工具', 'Expand or collapse tools')}
          onClick={() => setToolsOpen(open => !open)}><span>{uiText('工具', 'Tools')}</span><ChevronDown size={13} aria-hidden="true" /></button>
        <div id="workbench-tools" hidden={!toolsOpen}>{navigation.filter(item => item.group === 'tools').map(renderItem)}</div>
      </div>
    </nav>
    <div className="nav-utilities">{navigation.filter(item => item.id === 'help' || item.id === 'settings').map(renderItem)}</div>
  </aside>;
};
