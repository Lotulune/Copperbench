import React, { useState, useMemo } from 'react';
import {
  Box,
  Compass,
  Scroll,
  Terminal,
  Search,
  LayoutGrid,
  List as ListIcon,
  Plus
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ModElementType } from '../types/contract';
import { ElementInspector } from './ElementInspector';

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

  const filteredElements = useMemo(() => {
    return state.elements.filter((elem) => {
      const matchSearch =
        elem.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        elem.displayName.toLowerCase().includes(searchQuery.toLowerCase());
      const matchType = selectedType === 'all' || elem.type === selectedType;
      const matchState = selectedState === 'all' || elem.state === selectedState;
      return matchSearch && matchType && matchState;
    });
  }, [state.elements, searchQuery, selectedType, selectedState]);

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
      default:
        return <Box size={18} />;
    }
  };

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
            padding: '14px 20px',
            background: 'var(--bg-surface)',
            borderBottom: '1px solid var(--border-subtle)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '16px',
            flexWrap: 'wrap'
          }}
        >
          {/* Search & Type Filters */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1 }}>
            <div style={{ position: 'relative', width: '220px' }}>
              <Search
                size={14}
                style={{ position: 'absolute', left: '10px', top: '9px', color: 'var(--text-sub)' }}
              />
              <input
                type="text"
                placeholder="Filter elements..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{ paddingLeft: '30px', width: '100%' }}
                data-testid="elements-search-input"
              />
            </div>

            {/* Type Selector Pills */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '4px', background: 'var(--bg-panel)', padding: '3px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              {(['all', 'block', 'item', 'recipe', 'procedure'] as const).map((type) => (
                <button
                  key={type}
                  onClick={() => setSelectedType(type)}
                  style={{
                    padding: '3px 8px',
                    fontSize: '11px',
                    fontWeight: selectedType === type ? 600 : 500,
                    borderRadius: 'var(--radius-xs)',
                    background: selectedType === type ? 'var(--accent-copper)' : 'transparent',
                    color: selectedType === type ? '#ffffff' : 'var(--text-muted)'
                  }}
                >
                  {type === 'all' ? '全部' : { block: '方块', item: '物品', recipe: '配方', procedure: '过程' }[type]}
                </button>
              ))}
            </div>

            {/* State Filter */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              {(['all', 'valid', 'draft'] as const).map((st) => (
                <button
                  key={st}
                  onClick={() => setSelectedState(st)}
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
          </div>

          {/* Right: View Mode & Create Action */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ display: 'flex', alignItems: 'center', background: 'var(--bg-panel)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', padding: '2px' }}>
              <button
                onClick={() => setViewMode('grid')}
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
          {filteredElements.length === 0 ? (
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
                创建一个新的方块、物品、配方或过程开始创作。
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
              {filteredElements.map((elem) => {
                const isSelected = selectedElementId === elem.id;
                return (
                  <div
                    key={elem.id}
                    data-element-id={elem.id}
                    onClick={() => setSelectedElementId(elem.id)}
                    style={{
                      background: isSelected ? 'var(--bg-panel)' : 'var(--bg-surface)',
                      border: isSelected
                        ? '1px solid var(--accent-copper)'
                        : '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-md)',
                      padding: '16px',
                      cursor: 'pointer',
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
                          background: elem.type === 'block' ? 'var(--accent-copper-dim)' : 'var(--badge-blue-bg)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: elem.type === 'block' ? 'var(--accent-copper)' : 'var(--badge-blue)'
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
                      <span>{elem.type.toUpperCase()}</span>
                    </div>
                  </div>
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
                  {filteredElements.map((elem) => {
                    const isSelected = selectedElementId === elem.id;
                    return (
                      <tr
                        key={elem.id}
                        data-element-id={elem.id}
                        onClick={() => setSelectedElementId(elem.id)}
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
      </div>

      {/* Right: Contextual Inspector */}
      {selectedElement && (
        <ElementInspector
          element={selectedElement}
          onClose={() => setSelectedElementId(null)}
        />
      )}
    </div>
  );
};
