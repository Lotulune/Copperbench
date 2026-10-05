import { tr } from '../i18n/locale';
import { elementShortLabel, valueLabel } from '../i18n/labels';
import React, { useState, useMemo, useEffect } from 'react';
import {
  Box,
  Compass,
  Scroll,
  Terminal,
  Search,
  LayoutGrid,
  List as ListIcon,
  Plus,
  FileCode2,
  Gift,
  Trophy,
  ChevronLeft,
  ChevronRight,
  X
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ALL_MOD_ELEMENT_TYPES, ModElementType } from '../types/contract';
import { ElementInspector } from './ElementInspector';
import { t, uiText } from '../i18n';

const ProcedureWorkbench = React.lazy(() => import('./ProcedureWorkbench').then((module) => ({
  default: module.ProcedureWorkbench
})));

const FunctionWorkbench = React.lazy(() => import('./FunctionWorkbench').then((module) => ({
  default: module.FunctionWorkbench
})));

const LootTableWorkbench = React.lazy(() => import('./LootTableWorkbench').then((module) => ({
  default: module.LootTableWorkbench
})));

const AdvancementWorkbench = React.lazy(() => import('./AdvancementWorkbench').then((module) => ({
  default: module.AdvancementWorkbench
})));

const GuiWorkbench = React.lazy(() => import('./GuiWorkbench').then((module) => ({
  default: module.GuiWorkbench
})));

const OverlayWorkbench = React.lazy(() => import('./OverlayWorkbench').then((module) => ({
  default: module.OverlayWorkbench
})));

const COMMON_TYPES = ['all', 'block', 'item', 'procedure', 'recipe'] as const;

type SortOption = 'updated_desc' | 'updated_asc' | 'name_asc' | 'name_desc' | 'type_asc';


export const ModElementsWorkbench: React.FC = () => {
  const {
    state,
    selectedElementId,
    setSelectedElementId,
    selectedElement,
    setIsCreateModalOpen
  } = useWorkbench();

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedType, setSelectedType] = useState<ModElementType | 'all'>('all');
  const [selectedState, setSelectedState] = useState<'all' | 'valid' | 'draft' | 'invalid'>('all');
  const [viewMode, setViewMode] = useState<'grid' | 'table'>('grid');
  const [sortBy, setSortBy] = useState<SortOption>('updated_desc');
  const [pageSize, setPageSize] = useState<number>(24);
  const [currentPage, setCurrentPage] = useState<number>(1);

  // Reset to page 1 whenever filters change
  useEffect(() => {
    setCurrentPage(1);
  }, [searchQuery, selectedType, selectedState, sortBy, pageSize]);

  const normalizedSearch = searchQuery.trim().toLowerCase();
  const filteredAndSortedElements = useMemo(() => {
    let list = state.elements.filter((elem) => {
      const matchSearch =
        elem.name.toLowerCase().includes(normalizedSearch) ||
        elem.displayName.toLowerCase().includes(normalizedSearch);
      const matchType = selectedType === 'all' || elem.type === selectedType;
      const matchState = selectedState === 'all' || elem.state === selectedState;
      return matchSearch && matchType && matchState;
    });

    list.sort((a, b) => {
      switch (sortBy) {
        case 'updated_desc':
          return (b.updatedAt || '').localeCompare(a.updatedAt || '');
        case 'updated_asc':
          return (a.updatedAt || '').localeCompare(b.updatedAt || '');
        case 'name_asc':
          return a.displayName.localeCompare(b.displayName);
        case 'name_desc':
          return b.displayName.localeCompare(a.displayName);
        case 'type_asc':
          return a.type.localeCompare(b.type);
        default:
          return 0;
      }
    });

    return list;
  }, [state.elements, normalizedSearch, selectedType, selectedState, sortBy]);

  const hasFilters = normalizedSearch !== '' || selectedType !== 'all' || selectedState !== 'all';
  const clearFilters = () => { setSearchQuery(''); setSelectedType('all'); setSelectedState('all'); };

  const totalPages = Math.max(1, Math.ceil(filteredAndSortedElements.length / pageSize));
  useEffect(() => { setCurrentPage(page => Math.min(page, totalPages)); }, [totalPages]);
  const paginatedElements = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredAndSortedElements.slice(start, start + pageSize);
  }, [filteredAndSortedElements, currentPage, pageSize]);

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'block':
        return <Box size={18} />;
      case 'item':
        return <Compass size={18} />;
      case 'recipe':
        return <Scroll size={18} />;
      case 'procedure':
        return <Terminal size={18} />;
      case 'function':
        return <FileCode2 size={18} />;
      case 'loottable':
        return <Gift size={18} />;
      case 'achievement':
        return <Trophy size={18} />;
      default:
        return <Box size={18} />;
    }
  };

  // Dedicated full-screen workbenches for complex data-driven elements
  if (selectedElement?.type === 'procedure') {
    return (
      <React.Suspense fallback={<div className="procedure-route-loading">{tr("正在加载过程（Procedure）编辑器…")}</div>}>
        <ProcedureWorkbench element={selectedElement} onClose={() => setSelectedElementId(null)} />
      </React.Suspense>
    );
  }

  if (selectedElement?.type === 'overlay') {
    return (
      <React.Suspense fallback={<div className="procedure-route-loading">{tr("正在加载 Overlay 深度编辑器…")}</div>}>
        <OverlayWorkbench element={selectedElement} onClose={() => setSelectedElementId(null)} />
      </React.Suspense>
    );
  }

  if (selectedElement?.type === 'gui') {
    return (
      <React.Suspense fallback={<div className="procedure-route-loading">{tr("正在加载 GUI 深度编辑器…")}</div>}>
        <GuiWorkbench element={selectedElement} onClose={() => setSelectedElementId(null)} />
      </React.Suspense>
    );
  }

  if (selectedElement?.type === 'function') {
    return (
      <React.Suspense fallback={<div className="procedure-route-loading">{tr("正在加载函数（Function）编辑器…")}</div>}>
        <FunctionWorkbench element={selectedElement} onClose={() => setSelectedElementId(null)} />
      </React.Suspense>
    );
  }

  if (selectedElement?.type === 'loottable') {
    return (
      <React.Suspense fallback={<div className="procedure-route-loading">{tr("正在加载战利品表（Loot Table）编辑器…")}</div>}>
        <LootTableWorkbench element={selectedElement} onClose={() => setSelectedElementId(null)} />
      </React.Suspense>
    );
  }

  if (selectedElement?.type === 'achievement') {
    return (
      <React.Suspense fallback={<div className="procedure-route-loading">{tr("正在加载进度（Advancement）编辑器…")}</div>}>
        <AdvancementWorkbench element={selectedElement} onClose={() => setSelectedElementId(null)} />
      </React.Suspense>
    );
  }

  return (
    <div
      className="elements-workbench animate-fade-in"
      data-testid="elements-workbench"
      style={{
        flex: 1,
        display: 'flex',
        overflow: 'hidden',
        height: '100%'
      }}
    >
      {/* Left / Center: Main Elements Area */}
      <div
        className="elements-main"
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          background: 'var(--bg-base)'
        }}
      >
        <header className="elements-header">
          <div className="elements-title-row">
            <div className="elements-title"><h1>{uiText('模组元素', 'Mod elements')}</h1>
              <span className="elements-count">{state.elements.length}</span>
            </div>
            <button className="btn-primary" onClick={() => setIsCreateModalOpen(true)} data-testid="create-element-btn">
              <Plus size={16} aria-hidden="true" /><span>{tr("新建元素")}</span>
            </button>
          </div>
          <div className="elements-toolbar">
            <label className="elements-search">
              <Search size={16} aria-hidden="true" />
              <span className="sr-only">{uiText('搜索模组元素', 'Search mod elements')}</span>
              <input type="search" placeholder={t({ key: 'placeholder.filter_elements', fallback: 'Filter elements...' })}
                value={searchQuery} onChange={e => setSearchQuery(e.target.value)} data-testid="elements-search-input" />
            </label>
            <select value={selectedState} aria-label={uiText('元素状态', 'Element status')}
              data-testid="elements-state-select" onChange={e => setSelectedState(e.target.value as typeof selectedState)}>
              <option value="all">{tr("全部状态")}</option><option value="valid">{tr("有效")}</option>
              <option value="draft">{tr("草稿")}</option><option value="invalid">{uiText('无效', 'Invalid')}</option>
            </select>
            <select value={sortBy} onChange={e => setSortBy(e.target.value as SortOption)}
              aria-label={uiText('排序方式', 'Sort elements')} data-testid="elements-sort-select">
              <option value="updated_desc">{tr("最新更新")}</option><option value="updated_asc">{tr("最早更新")}</option>
              <option value="name_asc">{tr("名称 (A-Z)")}</option><option value="name_desc">{tr("名称 (Z-A)")}</option>
              <option value="type_asc">{tr("类型")}</option>
            </select>
            <div className="elements-view-toggle" role="group" aria-label={uiText('显示方式', 'View mode')}>
              <button onClick={() => setViewMode('grid')} aria-pressed={viewMode === 'grid'}
                aria-label={tr("卡片网格视图")} title={tr("卡片网格视图")}><LayoutGrid size={17} aria-hidden="true" /></button>
              <button onClick={() => setViewMode('table')} aria-pressed={viewMode === 'table'}
                aria-label={tr("紧凑表格视图")} title={tr("紧凑表格视图")}><ListIcon size={17} aria-hidden="true" /></button>
            </div>
          </div>
          <div className="elements-type-filters" role="group" aria-label={uiText('元素类型', 'Element type')}>
            {COMMON_TYPES.map(type => <button key={type} onClick={() => setSelectedType(type)}
              aria-pressed={selectedType === type} data-testid={`filter-type-${type}`}>
              {type === 'all' ? tr("全部") : elementShortLabel(type)}
            </button>)}
            <select className="elements-more-types" aria-label={uiText('更多元素类型', 'More element types')}
              data-testid="elements-type-select"
              value={COMMON_TYPES.some(type => type === selectedType) ? '' : selectedType}
              onChange={e => { if (e.target.value) setSelectedType(e.target.value as ModElementType); }}>
              <option value="" disabled>{uiText('更多类型', 'More types')}</option>
              {ALL_MOD_ELEMENT_TYPES.filter(type => !COMMON_TYPES.some(common => common === type)).map(type =>
                <option value={type} key={type}>{elementShortLabel(type)}</option>)}
            </select>
            {hasFilters && <button className="elements-clear-filters" onClick={clearFilters} data-testid="elements-clear-filters">
              <X size={13} aria-hidden="true" />{uiText('清除筛选', 'Clear filters')}
            </button>}
          </div>
        </header>

        {/* Elements View Area */}
        <div className="elements-results">
          {filteredAndSortedElements.length === 0 ? (
            <div className="elements-empty">
              <span className="hub-empty-icon">{hasFilters ? <Search size={30} aria-hidden="true" /> : <Box size={30} aria-hidden="true" />}</span>
              <h2>{hasFilters ? uiText('没有匹配的模组元素', 'No matching elements') : uiText('还没有模组元素', 'No mod elements yet')}</h2>
              <p>{hasFilters ? uiText('试试其他关键词，或清除筛选条件。', 'Try another keyword or clear your filters.')
                : uiText('创建第一个方块、物品或配方。', 'Create your first block, item or recipe.')}</p>
              {hasFilters ? <button className="btn-secondary" onClick={clearFilters}>{uiText('清除筛选', 'Clear filters')}</button>
                : <button className="btn-primary" onClick={() => setIsCreateModalOpen(true)} data-testid="empty-primary-action">
                  <Plus size={16} aria-hidden="true" />{tr("新建模组元素")}
                </button>}
            </div>
          ) : viewMode === 'grid' ? (
            <div className="elements-grid">
              {paginatedElements.map((elem) => {
                const isSelected = selectedElementId === elem.id;
                return (
                  <button
                    type="button"
                    key={elem.id}
                    className="element-card"
                    data-element-id={elem.id}
                    onClick={() => setSelectedElementId(elem.id)}
                    aria-pressed={isSelected}
                    style={{
                      background: isSelected ? 'var(--bg-panel)' : 'var(--bg-surface)',
                      border: isSelected
                        ? '1px solid var(--accent-copper)'
                        : '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-md)',
                      padding: '16px',
                      cursor: 'pointer',
                      width: '100%',
                      textAlign: 'left',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '12px',
                      boxShadow: isSelected ? '0 0 0 2px var(--accent-copper-dim)' : 'none',
                      transition: 'all 0.15s ease'
                    }}
                    onMouseEnter={(e) => {
                      if (!isSelected) e.currentTarget.style.borderColor = 'var(--border-focus)';
                    }}
                    onMouseLeave={(e) => {
                      if (!isSelected) e.currentTarget.style.borderColor = 'var(--border-subtle)';
                    }}
                  >
                    {/* Top Row */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
                      <div
                        style={{
                          width: '36px',
                          height: '36px',
                          borderRadius: 'var(--radius-sm)',
                          background:
                            elem.type === 'block'
                              ? 'var(--accent-copper-dim)'
                              : elem.type === 'function'
                              ? 'var(--badge-blue-bg)'
                              : elem.type === 'achievement'
                              ? 'var(--badge-amber-bg)'
                              : 'var(--bg-panel)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color:
                            elem.type === 'block'
                              ? 'var(--accent-copper)'
                              : elem.type === 'function'
                              ? 'var(--badge-blue)'
                              : elem.type === 'achievement'
                              ? 'var(--badge-amber)'
                              : 'var(--text-main)'
                        }}
                      >
                        {getTypeIcon(elem.type)}
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        {elem.firstParty === false && (
                          <span className="badge badge-amber" data-testid="element-outside-slice">{tr("暂不支持编辑")}</span>
                        )}
                        <span className={`badge badge-${elem.state === 'valid' ? 'green' : elem.state === 'draft' ? 'amber' : 'red'}`}>
                          {valueLabel(elem.state)}
                        </span>
                      </div>
                    </div>

                    {/* Content */}
                    <div>
                      <div style={{ fontWeight: 700, fontSize: '14px', color: 'var(--text-main)' }}>
                        {elem.displayName}
                      </div>
                      <div style={{ fontSize: '12px', color: 'var(--text-sub)', marginTop: '2px', fontFamily: 'var(--font-mono)' }}>
                        {elem.name}
                      </div>
                    </div>

                    {/* Bottom Meta */}
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        borderTop: '1px solid var(--border-subtle)',
                        paddingTop: '8px',
                        fontSize: '12px',
                        color: 'var(--text-sub)'
                      }}
                    >
                      <span>{valueLabel(elem.ownership)}</span>
                      <span className="badge badge-copper">{elementShortLabel(elem.type)}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          ) : (
            <div
              className="elements-table"
              style={{
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                overflow: 'hidden'
              }}
            >
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                <thead>
                  <tr style={{ background: 'var(--bg-panel)', borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-sub)', textAlign: 'left' }}>
                    <th style={{ padding: '10px 14px' }}>{tr("名称 / 标识符")}</th>
                    <th style={{ padding: '10px 14px' }}>{tr("类型")}</th>
                    <th style={{ padding: '10px 14px' }}>{tr("状态")}</th>
                    <th style={{ padding: '10px 14px' }}>{tr("所有权")}</th>
                    <th style={{ padding: '10px 14px' }}>{tr("更新时间")}</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedElements.map((elem) => {
                    const isSelected = selectedElementId === elem.id;
                    return (
                      <tr
                        key={elem.id}
                        data-element-id={elem.id}
                        onClick={() => setSelectedElementId(elem.id)}
                        tabIndex={0}
                        aria-selected={isSelected}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault();
                            setSelectedElementId(elem.id);
                          }
                        }}
                        style={{
                          borderBottom: '1px solid var(--border-subtle)',
                          background: isSelected ? 'var(--accent-copper-dim)' : 'transparent',
                          cursor: 'pointer'
                        }}
                      >
                        <td style={{ padding: '10px 14px', fontWeight: 600, color: 'var(--text-main)' }}>
                          {elem.displayName} <span style={{ color: 'var(--text-sub)', fontWeight: 400 }}>({elem.name})</span>
                        </td>
                        <td style={{ padding: '10px 14px' }}>
                          <span className="badge badge-copper">{elementShortLabel(elem.type)}</span>
                        </td>
                        <td style={{ padding: '10px 14px' }}>
                          <span className={`badge badge-${elem.state === 'valid' ? 'green' : 'amber'}`}>
                            {valueLabel(elem.state)}
                          </span>
                        </td>
                        <td style={{ padding: '10px 14px', color: 'var(--text-sub)' }}>
                          {valueLabel(elem.ownership)}
                        </td>
                        <td style={{ padding: '10px 14px', color: 'var(--text-sub)' }}>
                          {elem.updatedAt.slice(0, 10)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Large Workspace Pagination Controls */}
        {totalPages > 1 && (
          <footer className="elements-pagination"
            data-testid="elements-pagination"
            style={{
              padding: '10px 18px',
              background: 'var(--bg-surface)',
              borderTop: '1px solid var(--border-subtle)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              fontSize: '12px',
              color: 'var(--text-sub)'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <span>
                {tr("显示第 ")}{(currentPage - 1) * pageSize + 1} - {Math.min(currentPage * pageSize, filteredAndSortedElements.length)} {tr(" 项，共 ")}{filteredAndSortedElements.length} {tr(" 个元素")}</span>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <span>{tr("每页：")}</span>
                <select
                  aria-label={uiText("每页元素数量", "Elements per page")}
                  value={pageSize}
                  onChange={(e) => setPageSize(parseInt(e.target.value) || 24)}
                  data-testid="elements-page-size-select"
                  style={{ padding: '2px 6px', fontSize: '12px' }}
                >
                  <option value={24}>{tr("24 项")}</option>
                  <option value={48}>{tr("48 项")}</option>
                  <option value={96}>{tr("96 项")}</option>
                  <option value={200}>{tr("200 项")}</option>
                </select>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                disabled={currentPage <= 1}
                data-testid="elements-prev-page-btn"
                style={{ padding: '3px 8px' }}
              >
                <ChevronLeft size={13} />
                <span>{tr("上一页")}</span>
              </button>
              <span style={{ fontWeight: 600, color: 'var(--text-main)', padding: '0 4px' }}>
                {currentPage} / {totalPages}
              </span>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                disabled={currentPage >= totalPages}
                data-testid="elements-next-page-btn"
                style={{ padding: '3px 8px' }}
              >
                <span>{tr("下一页")}</span>
                <ChevronRight size={13} />
              </button>
            </div>
          </footer>
        )}
      </div>

      {/* Right: Contextual Inspector (for non-procedure/function/loottable/achievement elements like blocks, items, recipes) */}
      {selectedElement && (
        <ElementInspector
          element={selectedElement}
          onClose={() => setSelectedElementId(null)}
        />
      )}
    </div>
  );
};
