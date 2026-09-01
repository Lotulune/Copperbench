import React, { useState } from 'react';
import { PlayCircle, ChevronDown, ChevronUp } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { SCENARIOS } from '../mock/scenarios';
import { t } from '../i18n';
import { isNativeHostPresent } from '../bridge';

export const ScenarioSwitcher: React.FC = () => {
  const { state, loadScenario } = useWorkbench();
  const [isOpen, setIsOpen] = useState(false);

  const scenarioList = Object.keys(SCENARIOS);
  const current = SCENARIOS[state.currentScenarioId] || SCENARIOS['ready'];

  if (isNativeHostPresent()) return null;

  return (
    <div
      className="scenario-switcher"
      data-testid="scenario-switcher"
      style={{
        position: 'fixed',
        bottom: '36px',
        left: '200px', // Docked at bottom left above status footer, away from right inspector
        zIndex: 100,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'flex-start',
        gap: '6px'
      }}
    >
      {/* Expandable Scenario Tray */}
      {isOpen && (
        <div
          className="animate-fade-in"
          style={{
            background: 'var(--bg-surface)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-lg)',
            boxShadow: 'var(--shadow-lg)',
            padding: '8px',
            width: '320px',
            maxHeight: '380px',
            overflowY: 'auto',
            display: 'flex',
            flexDirection: 'column',
            gap: '4px'
          }}
        >
          <div
            style={{
              padding: '6px 8px',
              fontSize: '11px',
              fontWeight: 700,
              color: 'var(--text-sub)',
              textTransform: 'uppercase',
              letterSpacing: '0.5px'
            }}
          >
            合同场景（13 个契约状态）
          </div>

          {scenarioList.map((id) => {
            const sc = SCENARIOS[id];
            const isSelected = state.currentScenarioId === id;
            return (
              <button
                key={id}
                onClick={() => {
                  loadScenario(id);
                  setIsOpen(false);
                }}
                data-testid={`scenario-btn-${id}`}
                style={{
                  padding: '8px 10px',
                  borderRadius: 'var(--radius-sm)',
                  background: isSelected ? 'var(--accent-copper-dim)' : 'transparent',
                  color: isSelected ? 'var(--accent-copper)' : 'var(--text-main)',
                  fontWeight: isSelected ? 600 : 400,
                  border: isSelected ? '1px solid rgba(200, 122, 62, 0.3)' : '1px solid transparent',
                  textAlign: 'left',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '2px'
                }}
              >
                <div style={{ fontSize: '12px' }}>{t(sc.title)}</div>
                <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                  状态：{sc.viewportState}
                </div>
              </button>
            );
          })}
        </div>
      )}

      {/* Trigger Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        style={{
          background: 'var(--accent-copper)',
          color: '#ffffff',
          padding: '6px 12px',
          borderRadius: 'var(--radius-full)',
          boxShadow: 'var(--shadow-md)',
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          fontWeight: 600,
          fontSize: '12px'
        }}
        data-testid="scenario-switcher-trigger"
      >
        <PlayCircle size={15} />
        <span>场景：{t(current.title)}</span>
        {isOpen ? <ChevronDown size={14} /> : <ChevronUp size={14} />}
      </button>
    </div>
  );
};
