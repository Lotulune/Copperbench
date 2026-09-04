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
  ArrowUpDown
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ALL_MOD_ELEMENT_TYPES, ModElementType } from '../types/contract';
import { ElementInspector } from './ElementInspector';
import { t } from '../i18n';

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

type SortOption = 'updated_desc' | 'updated_asc' | 'name_asc' | 'name_desc' | 'type_asc';

const ELEMENT_LABELS: Partial<Record<ModElementType, string>> = {
  block: '方块', item: '物品', recipe: '配方', procedure: '过程', function: '函数', loottable: '战利品表', achievement: '进度',
  armor: '盔甲', armortrim: '盔甲纹饰', tool: '工具', itemextension: '物品扩展', attribute: '属性', bannerpattern: '旗帜图案',
  command: '命令', damagetype: '伤害类型', enchantment: '附魔', gamerule: '游戏规则', keybind: '按键绑定', painting: '画', particle: '粒子',
  potion: '药水', potioneffect: '药水效果', tab: '创造模式标签页', villagerprofession: '村民职业', villagertrade: '村民交易', biome: '生物群系',
  dimension: '维度', feature: '世界特征', fluid: '流体', plant: '植物', structure: '结构', livingentity: '生物实体', specialentity: '特殊实体',
  projectile: '投射物', gui: '界面', overlay: '覆盖层', code: '代码'
};

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

  const filteredAndSortedElements = useMemo(() => {
    let list = state.elements.filter((elem) => {
      const matchSearch =
        elem.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        elem.displayName.toLowerCase().includes(searchQuery.toLowerCase());
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
  }, [state.elements, searchQuery, selectedType, selectedState, sortBy]);

  const totalPages = Math.max(1, Math.ceil(filteredAndSortedElements.length / pageSize));
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
      <React.Suspense fallback={<div className="procedure-route-loading">正在加载 Procedure 编辑器…</div>}>
        <ProcedureWorkbench element={selectedElement} onClose={() => setSelectedElementId(null)} />
      </React.Suspense>
    );
  }

  if (selectedElement?.type === 'gui') {
    return (
      <React.Suspense fallback={<div className="procedure-route-loading">正在加载 GUI 深度编辑器…</div>}>
        <GuiWorkbench element={selectedElement} onClose={() => setSelectedElementId(null)} />
      </React.Suspense>
    );
  }

  if (selectedElement?.type === 'function') {
    return (
      <React.Suspense fallback={<div className="procedure-route-loading">正在加载 Function 编辑器…</div>}>
        <FunctionWorkbench element={selectedElement} onClose={() => setSelectedElementId(null)} />
      </React.Suspense>
    );
  }

  if (selectedElement?.type === 'loottable') {
    return (
      <React.Suspense fallback={<div className="procedure-route-loading">正在加载 Loot Table 编辑器…</div>}>
        <LootTableWorkbench element={selectedElement} onClose={() => setSelectedElementId(null)} />
      </React.Suspense>
    );
  }

  if (selectedElement?.type === 'achievement') {
    return (
      <React.Suspense fallback={<div className="procedure-route-loading">正在加载 Advancement 编辑器…</div>}>
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
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          background: 'var(--bg-base)'
        }}
      >
        {/* Filter & Action Toolbar */}
        <div
          style={{
            padding: '12px 18px',
            background: 'var(--bg-surface)',
            borderBottom: '1px solid var(--border-subtle)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px',
            flexWrap: 'wrap'
          }}
        >
          {/* Search & Type Filters */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1, flexWrap: 'wrap' }}>
            <div style={{ position: 'relative', width: '200px' }}>
              <Search
                size={14}
                style={{ position: 'absolute', left: '10px', top: '9px', color: 'var(--text-sub)' }}
              />
              <input
                type="text"
                placeholder={t({ key: 'placeholder.filter_elements', fallback: 'Filter elements...' })}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{ paddingLeft: '30px', width: '100%', fontSize: '11px' }}
                data-testid="elements-search-input"
              />
            </div>

            {/* Type Selector Pills */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '2px', background: 'var(--bg-panel)', padding: '2px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', flexWrap: 'wrap' }}>
              {(['all', ...ALL_MOD_ELEMENT_TYPES] as const).map((type) => (
                <button
                  key={type}
                  onClick={() => setSelectedType(type)}
                  aria-pressed={selectedType === type}
                  data-testid={`filter-type-${type}`}
                  style={{
                    padding: '3px 8px',
                    fontSize: '11px',
                    fontWeight: selectedType === type ? 600 : 500,
                    borderRadius: 'var(--radius-xs)',
                    background: selectedType === type ? 'var(--accent-copper-fill)' : 'transparent',
                    color: selectedType === type ? 'var(--text-on-accent)' : 'var(--text-muted)'
                  }}
                >
                  {type === 'all' ? '全部' : ELEMENT_LABELS[type] ?? type}
                </button>
              ))}
            </div>

            {/* State Filter */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              {(['all', 'valid', 'draft'] as const).map((st) => (
                <button
                  key={st}
                  onClick={() => setSelectedState(st)}
                  aria-pressed={selectedState === st}
                  style={{
                    padding: '3px 8px',
                    fontSize: '11px',
                    borderRadius: 'var(--radius-xs)',
                    border: '1px solid var(--border-subtle)',
                    background: selectedState === st ? 'var(--bg-hover)' : 'transparent',
                    color: selectedState === st ? 'var(--text-main)' : 'var(--text-sub)'
                  }}
                >
                  {st === 'all' ? '全部状态' : st === 'valid' ? '有效' : '草稿'}
                </button>
              ))}
            </div>

            {/* Sort Dropdown */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <ArrowUpDown size={13} color="var(--text-sub)" />
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as SortOption)}
                data-testid="elements-sort-select"
                style={{ padding: '3px 6px', fontSize: '11px' }}
              >
                <option value="updated_desc">最新更新</option>
                <option value="updated_asc">最早更新</option>
                <option value="name_asc">名称 (A-Z)</option>
                <option value="name_desc">名称 (Z-A)</option>
                <option value="type_asc">类型</option>
              </select>
            </div>
          </div>

          {/* Right: View Mode & Create Action */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ display: 'flex', alignItems: 'center', background: 'var(--bg-panel)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', padding: '2px' }}>
              <button
                onClick={() => setViewMode('grid')}
                aria-pressed={viewMode === 'grid'}
                aria-label="卡片网格视图"
                style={{
                  padding: '4px 6px',
                  borderRadius: 'var(--radius-xs)',
                  background: viewMode === 'grid' ? 'var(--bg-hover)' : 'transparent',
                  color: viewMode === 'grid' ? 'var(--accent-copper)' : 'var(--text-sub)'
                }}
                title="卡片网格视图"
              >
                <LayoutGrid size={14} />
              </button>
              <button
                onClick={() => setViewMode('table')}
                aria-pressed={viewMode === 'table'}
                aria-label="紧凑表格视图"
                style={{
                  padding: '4px 6px',
                  borderRadius: 'var(--radius-xs)',
                  background: viewMode === 'table' ? 'var(--bg-hover)' : 'transparent',
                  color: viewMode === 'table' ? 'var(--accent-copper)' : 'var(--text-sub)'
                }}
                title="紧凑表格视图"
              >
                <ListIcon size={14} />
              </button>
            </div>

            <button
              className="btn-primary"
              onClick={() => setIsCreateModalOpen(true)}
              data-testid="create-element-btn"
            >
              <Plus size={14} />
              <span>新建元素</span>
            </button>
          </div>
        </div>

        {/* Elements View Area */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '16px' }}>
          {filteredAndSortedElements.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                height: '70%',
                gap: '14px',
                color: 'var(--text-muted)'
              }}
            >
              <Box size={40} color="var(--border-active)" />
              <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-main)' }}>
                没有匹配的模组元素
              </div>
              <div style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
                创建方块、物品、配方、过程或数据驱动元素开始创作。
              </div>
              <button
                className="btn-primary"
                onClick={() => setIsCreateModalOpen(true)}
                data-testid="empty-primary-action"
              >
                <Plus size={14} />
                <span>新建模组元素</span>
              </button>
            </div>
          ) : viewMode === 'grid' ? (
            <div className="elements-grid">
              {paginatedElements.map((elem) => {
                const isSelected = selectedElementId === elem.id;
                return (
                  <button
                    type="button"
                    key={elem.id}
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
                      boxShadow: isSelected ? '0 0 0 2px var(--accent-copper-dim)' : 'var(--shadow-sm)',
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
                          <span className="badge badge-amber" data-testid="element-outside-slice">切片外</span>
                        )}
                        <span className={`badge badge-${elem.state === 'valid' ? 'green' : elem.state === 'draft' ? 'amber' : 'red'}`}>
                          {elem.state.toUpperCase()}
                        </span>
                      </div>
                    </div>

                    {/* Content */}
                    <div>
                      <div style={{ fontWeight: 700, fontSize: '14px', color: 'var(--text-main)' }}>
                        {elem.displayName}
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--text-sub)', marginTop: '2px', fontFamily: 'var(--font-mono)' }}>
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
                        fontSize: '10px',
                        color: 'var(--text-sub)'
                      }}
                    >
                      <span>{elem.ownership.toUpperCase()}</span>
                      <span className="badge badge-copper">{elem.type.toUpperCase()}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          ) : (
            <div
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
                    <th style={{ padding: '10px 14px' }}>名称 / 标识符</th>
                    <th style={{ padding: '10px 14px' }}>类型</th>
                    <th style={{ padding: '10px 14px' }}>状态</th>
                    <th style={{ padding: '10px 14px' }}>所有权</th>
                    <th style={{ padding: '10px 14px' }}>更新时间</th>
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
                          <span className="badge badge-copper">{elem.type.toUpperCase()}</span>
                        </td>
                        <td style={{ padding: '10px 14px' }}>
                          <span className={`badge badge-${elem.state === 'valid' ? 'green' : 'amber'}`}>
                            {elem.state.toUpperCase()}
                          </span>
                        </td>
                        <td style={{ padding: '10px 14px', color: 'var(--text-sub)' }}>
                          {elem.ownership}
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
          <footer
            data-testid="elements-pagination"
            style={{
              padding: '10px 18px',
              background: 'var(--bg-surface)',
              borderTop: '1px solid var(--border-subtle)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              fontSize: '11px',
              color: 'var(--text-sub)'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <span>
                显示第 {(currentPage - 1) * pageSize + 1} - {Math.min(currentPage * pageSize, filteredAndSortedElements.length)} 项，共 {filteredAndSortedElements.length} 个元素
              </span>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <span>每页：</span>
                <select
                  value={pageSize}
                  onChange={(e) => setPageSize(parseInt(e.target.value) || 24)}
                  data-testid="elements-page-size-select"
                  style={{ padding: '2px 6px', fontSize: '11px' }}
                >
                  <option value={24}>24 项</option>
                  <option value={48}>48 项</option>
                  <option value={96}>96 项</option>
                  <option value={200}>200 项</option>
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
                <span>上一页</span>
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
                <span>下一页</span>
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
