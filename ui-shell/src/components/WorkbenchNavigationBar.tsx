import React, { useEffect, useRef, useState } from 'react';
import { ArrowRight, Box, ChevronRight, Search, X } from 'lucide-react';
import { useWorkbench, type NavView } from '../context/WorkbenchContext';
import { uiText } from '../i18n';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { OPEN_WORKBENCH_SEARCH_EVENT, workbenchNavigation } from './workbenchNavigation';

import { WorkspaceActions } from './WorkspaceActions';

export const WorkbenchNavigationBar: React.FC = () => {
  const { activeView, setActiveView, state, selectedElement, setSelectedElementId } = useWorkbench();
  const [tabs, setTabs] = useState<NavView[]>(['hub']);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [selection, setSelection] = useState(0);
  const searchRef = useRef<HTMLInputElement>(null);
  const tabListRef = useRef<HTMLDivElement>(null);
  const dialogRef = useDialogA11y(open, () => setOpen(false));
  const navigation = workbenchNavigation();
  useEffect(() => { setTabs(previous => previous.includes(activeView) ? previous : [...previous, activeView]); }, [activeView]);
  useEffect(() => { setTabs([activeView]); }, [state.workbench?.workspace.id]);
  useEffect(() => {
    const keyboard = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && !event.altKey) {
        if (document.querySelector('[role="dialog"], dialog[open]') && !open) return;
        event.preventDefault(); setOpen(previous => !previous); setQuery(''); setSelection(0);
      }
    };
    window.addEventListener('keydown', keyboard);
    return () => window.removeEventListener('keydown', keyboard);
  }, [open]);
  useEffect(() => {
    const showSearch = () => { setQuery(''); setSelection(0); setOpen(true); };
    window.addEventListener(OPEN_WORKBENCH_SEARCH_EVENT, showSearch);
    return () => window.removeEventListener(OPEN_WORKBENCH_SEARCH_EVENT, showSearch);
  }, []);
  useEffect(() => { if (open) searchRef.current?.focus(); }, [open]);
  const normalized = query.trim().toLowerCase();
  const results = [
    ...navigation.map(item => ({ id: item.id, label: item.label, detail: uiText('打开视图', 'Open view'), icon: item.icon,
      run: () => setActiveView(item.id) })),
    ...state.elements.map(element => ({ id: element.id, label: element.displayName, detail: element.name, icon: Box,
      run: () => { setSelectedElementId(element.id); setActiveView('elements'); } }))
  ].filter(item => `${item.label} ${item.detail}`.toLowerCase().includes(normalized)).slice(0, 30);
  const visibleTabs = tabs.includes(activeView) ? tabs : [...tabs, activeView];
  useEffect(() => {
    const list = tabListRef.current;
    if (!list) return;
    const revealActiveTab = () => {
      const active = list.querySelector('[aria-selected="true"]')?.closest('.workbench-view-tab');
      if (!active) return;
      const viewport = list.getBoundingClientRect(), tab = active.getBoundingClientRect();
      if (tab.left < viewport.left) list.scrollLeft -= viewport.left - tab.left;
      else if (tab.right > viewport.right) list.scrollLeft += tab.right - viewport.right;
    };
    revealActiveTab();
    const observer = new ResizeObserver(revealActiveTab);
    observer.observe(list);
    return () => observer.disconnect();
  }, [activeView, visibleTabs.length]);
  const closeTab = (view: NavView) => {
    const next = visibleTabs.filter(item => item !== view);
    setTabs(next.length ? next : ['hub']);
    if (activeView === view) {
      const target = next.at(-1) ?? 'hub'; setActiveView(target);
      requestAnimationFrame(() => document.querySelector<HTMLButtonElement>(`[data-testid="workbench-tab-${target}"]`)?.focus());
    }
  };
  return <>
    <div className="workbench-navigation-bar">
      <div className="workbench-view-tabs" ref={tabListRef} role="tablist" aria-label={uiText('工作区视图', 'Workspace views')}
        onKeyDown={event => {
          if (!(event.target instanceof HTMLElement) || event.target.getAttribute('role') !== 'tab' || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
          event.preventDefault();
          const index = visibleTabs.indexOf(activeView);
          const next = event.key === 'Home' ? 0 : event.key === 'End' ? visibleTabs.length - 1
            : (index + (event.key === 'ArrowRight' ? 1 : -1) + visibleTabs.length) % visibleTabs.length;
          const target = visibleTabs[next]; setActiveView(target);
          requestAnimationFrame(() => document.querySelector<HTMLButtonElement>(`[data-testid="workbench-tab-${target}"]`)?.focus());
        }}>
        {visibleTabs.map(view => { const item = navigation.find(item => item.id === view)!;
          const label = view === 'elements' && selectedElement ? selectedElement.displayName : item.label;
          return <div key={view} className={`workbench-view-tab${activeView === view ? ' is-active' : ''}`}>
            <button type="button" role="tab" aria-selected={activeView === view} tabIndex={activeView === view ? 0 : -1} data-testid={`workbench-tab-${view}`}
              onClick={() => setActiveView(view)} title={item.label}><item.icon size={14} aria-hidden="true" /><span>{label}</span></button>
            {view !== 'hub' && <button type="button" className="workbench-tab-close" onClick={() => closeTab(view)}
              aria-label={uiText(`关闭视图：${item.label}`, `Close view: ${item.label}`)}><X size={12} aria-hidden="true" /></button>}
          </div>;
        })}
      </div>
    </div>
    <div className="workbench-context-toolbar">
      <nav className="workbench-breadcrumb" aria-label={uiText('当前位置', 'Current location')}>
        <button type="button" onClick={() => setActiveView('hub')}>{state.workbench?.workspace.name ?? 'Copperbench'}</button>
        <ChevronRight size={12} aria-hidden="true" />
        <span>{activeView === 'elements' && selectedElement ? selectedElement.displayName : navigation.find(item => item.id === activeView)?.label}</span>
      </nav>
      <WorkspaceActions />
    </div>
    {open && <div className="modal-overlay workbench-command-overlay" onMouseDown={event => { if (event.target === event.currentTarget) setOpen(false); }}>
      <div className="workbench-command-palette" ref={dialogRef} role="dialog" aria-modal="true"
        aria-label={uiText('搜索元素与工具', 'Search elements and tools')} data-testid="workbench-command-palette">
        <label className="workbench-command-input"><Search size={18} aria-hidden="true" />
          <input ref={searchRef} value={query} role="combobox" aria-expanded="true" aria-controls="workbench-command-results"
            aria-activedescendant={results[selection] ? `workbench-command-${selection}` : undefined}
            aria-label={uiText('搜索元素与工具', 'Search elements and tools')} placeholder={uiText('输入名称或视图…', 'Type a name or view…')}
            onChange={event => { setQuery(event.target.value); setSelection(0); }}
            onKeyDown={event => {
              if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); setSelection(index => Math.max(0, Math.min(results.length - 1, index + (event.key === 'ArrowDown' ? 1 : -1)))); }
              if (event.key === 'Enter' && results[selection]) { event.preventDefault(); results[selection].run(); setOpen(false); }
            }} /><button type="button" aria-label={uiText('关闭搜索', 'Close search')} onClick={() => setOpen(false)}><X size={16} aria-hidden="true" /></button>
        </label>
        <div id="workbench-command-results" role="listbox" className="workbench-command-results">
          {results.map((item, index) => <button key={item.id} type="button" role="option" id={`workbench-command-${index}`}
            aria-selected={index === selection} className={index === selection ? 'is-selected' : ''}
            onClick={() => { item.run(); setOpen(false); }}><item.icon size={16} aria-hidden="true" />
            <span>{item.label}<small>{item.detail}</small></span><ArrowRight size={13} aria-hidden="true" /></button>)}
          {!results.length && <p>{uiText('没有匹配的元素或视图。', 'No matching elements or views.')}</p>}
        </div>
      </div>
    </div>}
  </>;
};
