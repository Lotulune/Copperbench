import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown, RefreshCw, Search } from 'lucide-react';
import { agentText, validModelId, type AgentModelCatalog } from '../bridge/agentBridge';
import { uiText } from '../i18n';
import './agentModels.css';

export function AgentModelSelect({ value, usesSessionModel = false, catalog, disabled, active, onChange, onRefresh }: {
  value: string; usesSessionModel?: boolean; catalog?: AgentModelCatalog; disabled: boolean; active: boolean;
  onChange: (model: string) => void; onRefresh: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [manual, setManual] = useState(false);
  const [manualId, setManualId] = useState('');
  const [highlight, setHighlight] = useState(0);
  const [position, setPosition] = useState({ left: 0, top: 0, width: 360, maxHeight: 350 });
  const trigger = useRef<HTMLButtonElement>(null), menu = useRef<HTMLDivElement>(null), search = useRef<HTMLInputElement>(null);
  const id = useId();
  const choices = catalog?.models.filter(model => `${model.label} ${model.id}`.toLowerCase().includes(query.toLowerCase())) ?? [];
  const chosen = catalog?.models.find(model => model.id === value);
  const emptyLabel = usesSessionModel ? uiText('沿用会话模型', 'Keep session model') : uiText('选择或输入模型', 'Choose or enter a model');
  const choose = (model: string) => { setOpen(false); trigger.current?.focus(); if (model !== value) onChange(model); };
  const expand = () => { setQuery(''); setManualId(value); setManual(false); setHighlight(0); setOpen(true); };
  useEffect(() => { if (disabled || !active) setOpen(false); }, [disabled, active]);
  useEffect(() => { setHighlight(0); }, [query, catalog?.models]);
  useLayoutEffect(() => {
    if (!open) return;
    const place = () => {
      const bounds = trigger.current?.getBoundingClientRect(); if (!bounds) return;
      const width = Math.min(360, innerWidth - 24), above = bounds.top - 18, below = innerHeight - bounds.bottom - 18;
      const showAbove = above >= below;
      const maxHeight = Math.max(0, Math.min(350, showAbove ? above : below));
      const height = Math.min(menu.current?.getBoundingClientRect().height || 300, maxHeight);
      setPosition({ left: Math.max(12, Math.min(bounds.left, innerWidth - width - 12)),
        top: showAbove ? Math.max(12, bounds.top - height - 6) : bounds.bottom + 6, width, maxHeight });
    };
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !menu.current?.contains(event.target) && !trigger.current?.contains(event.target)) setOpen(false);
    };
    place(); search.current?.focus({ preventScroll: true });
    const observer = new ResizeObserver(place); if (menu.current) observer.observe(menu.current);
    document.addEventListener('pointerdown', outside); window.addEventListener('resize', place); window.addEventListener('scroll', place, true);
    return () => { observer.disconnect(); document.removeEventListener('pointerdown', outside); window.removeEventListener('resize', place); window.removeEventListener('scroll', place, true); };
  }, [open]);
  const keyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); setOpen(false); trigger.current?.focus(); }
    else if (event.target !== search.current || event.nativeEvent.isComposing) return;
    else if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && choices.length) {
      event.preventDefault(); setHighlight(index => (index + (event.key === 'ArrowDown' ? 1 : -1) + choices.length) % choices.length);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (choices[highlight]) choose(choices[highlight].id);
      else if (validModelId(query.trim())) choose(query.trim());
    }
  };
  useEffect(() => { if (open) document.getElementById(`${id}-option-${highlight}`)?.scrollIntoView({ block: 'nearest' }); }, [open, highlight, id]);
  return <>
    <button type="button" ref={trigger} className="creation-select-trigger creation-model-trigger" data-testid="simple-model" data-model-value={value}
      aria-label={`${uiText('模型', 'Model')}: ${value || emptyLabel}`} aria-haspopup="dialog" aria-expanded={open} aria-controls={open ? id : undefined}
      title={value || undefined} disabled={disabled} onClick={() => open ? setOpen(false) : expand()}>
      <span>{chosen?.label || value || emptyLabel}</span><ChevronDown size={13} aria-hidden="true" />
    </button>
    {open && createPortal(<div ref={menu} id={id} role="dialog" aria-label={uiText('选择模型', 'Choose a model')} className="creation-select-menu creation-model-menu" style={position}
      onKeyDown={keyDown} onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget as Node | null) && event.relatedTarget !== trigger.current) setOpen(false); }}>
      <div className="creation-model-search"><Search size={15} aria-hidden="true" /><input ref={search} value={query} maxLength={200}
        aria-label={uiText('搜索或输入模型 ID', 'Search or enter a model ID')} placeholder={uiText('搜索或输入模型 ID', 'Search or enter a model ID')}
        role="combobox" aria-expanded="true" aria-controls={`${id}-list`} aria-activedescendant={choices[highlight] ? `${id}-option-${highlight}` : undefined}
        onChange={event => setQuery(event.target.value)} />
        <button type="button" aria-label={uiText('刷新模型列表', 'Refresh model list')} title={uiText('刷新模型列表', 'Refresh model list')} disabled={catalog?.state === 'loading'} onClick={onRefresh}><RefreshCw size={15} className={catalog?.state === 'loading' ? 'creation-spinner' : ''} /></button></div>
      <button type="button" className="creation-model-default" aria-expanded={manual} onClick={() => setManual(value => !value)}>{uiText('手动输入模型 ID', 'Enter a model ID manually')}</button>
      {manual && <div className="creation-model-manual-entry"><label htmlFor={`${id}-manual`}>{uiText('想使用的模型 ID', 'Model ID to use')}</label>
        <div><input id={`${id}-manual`} autoFocus value={manualId} maxLength={200} placeholder={uiText('例如：provider/model', 'e.g. provider/model')} onChange={event => setManualId(event.target.value)}
          onKeyDown={event => { if (event.key === 'Enter' && !event.nativeEvent.isComposing) { event.preventDefault(); event.stopPropagation(); if (validModelId(manualId.trim())) choose(manualId.trim()); } }} />
          <button type="button" disabled={!validModelId(manualId.trim())} onClick={() => choose(manualId.trim())}>{uiText('使用模型', 'Use model')}</button></div>
        <p>{uiText('填写当前源提供的完整模型 ID，无需等待列表加载。', 'Enter the full model ID from your service without waiting for the list.')}</p></div>}
      {catalog?.state === 'loading' && <p role="status">{uiText('正在获取模型列表…', 'Loading models…')}</p>}
      {catalog?.error && <p role="alert" className="creation-model-error">{agentText(catalog.error, catalog.errorText)}</p>}
      <div id={`${id}-list`} role="listbox" aria-label={uiText('可用模型', 'Available models')} className="creation-select-options">
        {choices.map((model, index) => <div id={`${id}-option-${index}`} key={model.id} role="option" aria-selected={model.id === value}
          data-highlighted={index === highlight} data-testid="model-option" data-model-id={model.id} onMouseEnter={() => setHighlight(index)}
          onMouseDown={event => event.preventDefault()} onClick={() => choose(model.id)}><span><strong>{model.label}</strong>{model.id !== model.label && <small>{model.id}</small>}</span>{model.id === value && <Check size={14} aria-hidden="true" />}</div>)}
      </div>
      {!choices.length && catalog?.state !== 'loading' && <p>{uiText('没有匹配的模型，可输入服务提供的模型 ID。', 'No matching models. Enter a model ID from your service.')}</p>}
      {validModelId(query.trim()) && !choices.some(model => model.id === query.trim()) && <button type="button" className="creation-model-manual" onClick={() => choose(query.trim())}>{uiText('使用此模型 ID', 'Use this model ID')}: <code>{query.trim()}</code></button>}
      <p className="creation-model-scope">{catalog?.source === 'service' ? uiText('当前服务的模型列表', 'Configured service models') : uiText('CLI 模型列表', 'CLI models')}
        {usesSessionModel && <span>{uiText('继续使用原 CLI 会话的模型', 'Continue with the original CLI session model')}</span>}
        {(value || usesSessionModel) && <span>{uiText('切换模型会新建对话', 'Changing models starts a new conversation')}</span>}</p>
    </div>, document.body)}
  </>;
}
