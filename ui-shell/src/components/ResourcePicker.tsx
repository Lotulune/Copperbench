import React, { useId, useMemo, useRef, useState } from 'react';
import { Image as ImageIcon, Link as LinkIcon } from 'lucide-react';
import { uiText, useUiLocale } from '../i18n';
import { useAssetThumbnail } from './assetThumbnails';
import { filterResourceChoices, findResourceChoice, sameResourceValue, type ResourceChoice } from './resourcePickerModel';
import './resourcePicker.css';

const PAGE = 48;

function Thumbnail({ workspaceId, choice }: { workspaceId?: string; choice?: ResourceChoice }) {
  const url = useAssetThumbnail(workspaceId, choice?.asset?.category === 'TEXTURE' ? choice.asset : undefined);
  if (url) return <img className="resource-picker-thumbnail" src={url} alt="" />;
  return <span className="resource-picker-thumbnail is-empty" data-loading={url === undefined || undefined}>
    {choice?.asset ? <ImageIcon size={14} aria-hidden="true" /> : <LinkIcon size={13} aria-hidden="true" />}</span>;
}

/**
 * A resource field with the workspace's matching assets shown as thumbnails. The text input stays editable, so
 * references outside the workspace (vanilla or other namespaces) can still be typed.
 */
export const ResourcePicker: React.FC<{
  id: string;
  value: string;
  choices: readonly ResourceChoice[];
  workspaceId?: string;
  disabled?: boolean;
  testId: string;
  kindLabel: string;
  disabledStyle?: React.CSSProperties;
  onChange: (value: string) => void;
}> = ({ id, value, choices, workspaceId, disabled, testId, kindLabel, disabledStyle, onChange }) => {
  useUiLocale();
  const panelId = useId();
  const toggleRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [shown, setShown] = useState(PAGE);
  const current = findResourceChoice(choices, value);
  const filtered = useMemo(() => filterResourceChoices(choices, query), [choices, query]);
  const close = () => { setOpen(false); setQuery(''); setShown(PAGE); toggleRef.current?.focus(); };

  return (
    <div className="resource-picker">
      <div className="resource-picker-row">
        <Thumbnail workspaceId={workspaceId} choice={current} />
        <input
          id={id}
          type="text"
          value={value}
          disabled={disabled}
          readOnly={disabled}
          onChange={event => onChange(event.target.value)}
          style={disabledStyle}
          aria-describedby={`${id}-help`}
          data-testid={testId}
        />
        <button
          ref={toggleRef}
          type="button"
          className="btn-secondary"
          aria-expanded={open}
          aria-controls={panelId}
          disabled={disabled || choices.length === 0}
          onClick={() => open ? close() : setOpen(true)}
          data-testid={`${testId}-browse`}
        >{uiText('选择…', 'Choose…')}</button>
      </div>
      <div id={`${id}-help`} className="resource-picker-help">
        {kindLabel}{choices.length > 0
          ? uiText(` · ${choices.length} 个候选`, ` · Candidates: ${choices.length}`)
          : uiText(' · 工作区里没有匹配的资源，可输入完整引用', ' · No matching workspace resources; enter a full reference')}
        {current && <span title={current.relativePath}> · {current.relativePath}</span>}
      </div>
      {open && (
        <div
          id={panelId}
          className="resource-picker-panel"
          role="group"
          aria-label={uiText(`选择${kindLabel}`, `Choose: ${kindLabel}`)}
          onKeyDown={event => { if (event.key === 'Escape') { event.stopPropagation(); close(); } }}
          data-testid={`${testId}-panel`}
        >
          <input
            type="search"
            value={query}
            autoFocus
            placeholder={uiText('按名称或路径搜索', 'Search by name or path')}
            aria-label={uiText('搜索资源', 'Search resources')}
            onChange={event => { setQuery(event.target.value); setShown(PAGE); }}
            data-testid={`${testId}-search`}
          />
          {filtered.length === 0
            ? <p className="resource-picker-empty" role="status">{uiText('没有匹配的资源。', 'No matching resources.')}</p>
            : <div className="resource-picker-grid">
              {filtered.slice(0, shown).map(choice => (
                <button
                  key={choice.value}
                  type="button"
                  className="resource-picker-option"
                  aria-pressed={sameResourceValue(choice.value, value)}
                  title={choice.relativePath || choice.label}
                  onClick={() => { onChange(choice.value); close(); }}
                  data-testid={`${testId}-option`}
                  data-value={choice.value}
                >
                  <Thumbnail workspaceId={workspaceId} choice={choice} />
                  <span>{choice.value}</span>
                </button>
              ))}
            </div>}
          {filtered.length > shown && (
            <button type="button" className="btn-secondary resource-picker-more" onClick={() => setShown(count => count + PAGE)}>
              {uiText(`显示更多（还有 ${filtered.length - shown} 个）`, `Show more (${filtered.length - shown} left)`)}</button>
          )}
        </div>
      )}
    </div>
  );
};
