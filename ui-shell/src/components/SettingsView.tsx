import { useEffect, useMemo, useState } from 'react';
import { Check, ExternalLink, RotateCcw, Search, Settings2 } from 'lucide-react';
import { windowBridge, type AppPreferenceEntry, type AppPreferencesSnapshot, type AppPreferenceValue } from '../bridge/windowBridge';
import { uiText, useUiLocale } from '../i18n/locale';
import './settings.css';

type Section = AppPreferenceEntry['section'];
type Draft = Record<string, AppPreferenceValue>;
const nativeEditorKeys = new Set(['ide.editorTheme', 'ide.autocomplete', 'ide.autocompleteMode', 'ide.autocompleteDocWindow', 'ide.errorInfoEnable']);

function labels(): Record<string, [string, string]> {
  return {
    'ui.nativeFileChooser': [uiText('系统文件选择器', 'System file picker'), uiText('使用操作系统的文件打开与保存窗口。', 'Use the operating system’s file open and save dialogs.')],
    'ui.remindOfUnsavedChanges': [uiText('未保存更改提醒', 'Unsaved change reminders'), uiText('关闭原生编辑器前提醒保存更改。', 'Ask before closing native editors with unsaved changes.')],
    'ui.autoReloadTabs': [uiText('自动重新加载编辑器', 'Reload editors automatically'), uiText('工作区内容变化后重新加载原生编辑器标签页。', 'Reload native editor tabs when workspace content changes.')],
    'ui.expandSectionsByDefault': [uiText('默认展开编辑器分区', 'Expand editor sections'), uiText('打开原生编辑器时展开设置分区。', 'Expand settings sections when opening a native editor.')],
    'ide.editorTheme': [uiText('代码编辑器主题', 'Code editor theme'), uiText('应用于原生代码编辑器，重新打开编辑器后生效。', 'Applies to native code editors when they are reopened.')],
    'ide.fontSize': [uiText('代码字号', 'Code font size'), uiText('源代码和原生代码编辑器的字体大小。', 'Font size in source and native code editors.')],
    'ide.useLigatures': [uiText('字体连字', 'Font ligatures'), uiText('在支持的字体中将组合符号显示为连字。', 'Join supported character pairs in the code editor font.')],
    'ide.autocomplete': [uiText('代码自动补全', 'Code completion'), uiText('在原生代码编辑器中提供补全建议。', 'Offer completion suggestions in the native code editor.')],
    'ide.autocompleteMode': [uiText('补全触发方式', 'Completion mode'), uiText('选择何时显示代码补全建议。', 'Choose when code completion suggestions appear.')],
    'ide.autocompleteDocWindow': [uiText('补全文档', 'Completion documentation'), uiText('在补全建议旁显示文档。', 'Show documentation beside completion suggestions.')],
    'ide.lineNumbers': [uiText('显示行号', 'Show line numbers'), uiText('在源代码和原生代码编辑器中显示行号。', 'Show line numbers in source and native code editors.')],
    'ide.errorInfoEnable': [uiText('代码错误提示', 'Code error hints'), uiText('在原生代码编辑器中显示诊断信息。', 'Display diagnostics in the native code editor.')],
    'gradle.Xmx': [uiText('构建最大内存（MB）', 'Maximum build memory (MB)'), uiText('下一次 Gradle 构建使用的最大堆内存。', 'Maximum heap size for the next Gradle build.')],
    'gradle.offline': [uiText('离线构建', 'Offline builds'), uiText('仅使用已缓存的依赖；缺少依赖时构建会失败。', 'Use cached dependencies only. Builds fail if a dependency is missing.')],
    'gradle.buildOnSave': [uiText('保存后构建', 'Build on save'), uiText('保存模组元素后触发 Gradle 构建。', 'Start a Gradle build after saving a mod element.')],
    'gradle.passLangToMinecraft': [uiText('同步 Minecraft 语言', 'Match Minecraft language'), uiText('启动测试客户端时使用应用语言。', 'Use the application language when launching a test client.')],
    'gradle.enablePerformanceMonitor': [uiText('测试客户端性能监测', 'Test client performance monitor'), uiText('运行测试客户端时启用性能监测。', 'Enable performance monitoring while running a test client.')]
  };
}

function values(snapshot: AppPreferencesSnapshot): Draft {
  return Object.fromEntries(snapshot.entries.map(entry => [entry.key, entry.value]));
}

function valid(entry: AppPreferenceEntry, value: AppPreferenceValue | undefined): boolean {
  if (entry.type === 'boolean') return typeof value === 'boolean';
  if (entry.type === 'choice') return typeof value === 'string' && !!entry.options?.includes(value);
  return value !== '' && Number.isInteger(Number(value)) && Number(value) >= (entry.min ?? -Infinity)
    && Number(value) <= (entry.max ?? Infinity);
}

export function SettingsView() {
  useUiLocale();
  const [snapshot, setSnapshot] = useState<AppPreferencesSnapshot | null>(null);
  const [draft, setDraft] = useState<Draft>({});
  const [section, setSection] = useState<Section>('ui');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(windowBridge.canManagePreferences);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const copy = labels();
  const groups: [Section, string][] = [['ui', uiText('工作区', 'Workspace')], ['ide', uiText('代码编辑器', 'Code editor')], ['gradle', uiText('构建与运行', 'Build & run')]];
  const changes = useMemo(() => Object.fromEntries((snapshot?.entries ?? [])
    .filter(entry => String(draft[entry.key]) !== String(entry.value))
    .map(entry => [entry.key, entry.type === 'integer' ? Number(draft[entry.key]) : draft[entry.key]])), [snapshot, draft]);
  const dirty = Object.keys(changes).length > 0;
  const invalid = (snapshot?.entries ?? []).some(entry => !valid(entry, draft[entry.key]));

  function accept(next: AppPreferencesSnapshot) {
    if (next.schemaVersion !== '1.0' || !Array.isArray(next.entries) || typeof next.revision !== 'string')
      throw new Error(uiText('设置响应格式不受支持，请更新应用。', 'This settings format is not supported. Update the application.'));
    setSnapshot(next); setDraft(values(next));
  }

  async function reload() {
    setLoading(true); setError(null); setSaved(false);
    try { accept(await windowBridge.getPreferences()); }
    catch (failure) { setError(failure instanceof Error ? failure.message : String(failure)); }
    finally { setLoading(false); }
  }

  useEffect(() => {
    if (!windowBridge.canManagePreferences) return;
    let cancelled = false;
    void windowBridge.getPreferences().then(next => { if (!cancelled) accept(next); })
      .catch(failure => { if (!cancelled) setError(failure instanceof Error ? failure.message : String(failure)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  async function save() {
    if (!snapshot || !dirty || invalid || saving) return;
    setSaving(true); setError(null); setSaved(false);
    try { accept(await windowBridge.savePreferences({ revision: snapshot.revision, changes })); setSaved(true); }
    catch (failure) { setError(failure instanceof Error ? failure.message : String(failure)); }
    finally { setSaving(false); }
  }

  async function openAdvanced() {
    setError(null);
    try { await windowBridge.openPreferences(); }
    catch (failure) { setError(String(failure)); }
  }

  function edit(key: string, value: AppPreferenceValue) {
    setDraft(current => ({ ...current, [key]: value })); setSaved(false);
  }

  const query = search.trim().toLocaleLowerCase();
  const visible = (snapshot?.entries ?? []).filter(entry => query
    ? [entry.key, ...(copy[entry.key] ?? [])].join(' ').toLocaleLowerCase().includes(query)
    : entry.section === section);
  function renderEntry(entry: AppPreferenceEntry) {
    const [name, hint] = copy[entry.key] ?? [entry.key, ''];
    const id = `preference-${entry.key}`;
    const fieldValid = valid(entry, draft[entry.key]);
    return <div className="settings-row" key={entry.key}>
      <div className="settings-label"><label htmlFor={id} title={hint}>{name}</label>
        {['ui.remindOfUnsavedChanges', 'ui.autoReloadTabs', 'ui.expandSectionsByDefault'].includes(entry.key) && <small>{uiText('原生编辑器', 'Native editors')}</small>}
        <p id={`${id}-hint`} className={entry.key === 'gradle.offline' ? undefined : 'settings-sr-only'}>{hint}</p>
        {!fieldValid && <p className="settings-field-error" id={`${id}-error`}>{uiText('请输入范围内的整数', 'Enter a whole number in range')}: {entry.min}–{entry.max}</p>}</div>
      {entry.type === 'boolean' ? <input id={id} type="checkbox" checked={draft[entry.key] === true}
        aria-describedby={`${id}-hint`} onChange={event => edit(entry.key, event.target.checked)} />
        : entry.type === 'choice' ? <select id={id} value={String(draft[entry.key] ?? '')} aria-describedby={`${id}-hint`}
          onChange={event => edit(entry.key, event.target.value)}>{entry.options?.map(option => <option key={option} value={option}>{option}</option>)}</select>
          : <input id={id} type="number" step="1" min={entry.min} max={entry.max} value={String(draft[entry.key] ?? '')}
            aria-invalid={!fieldValid} aria-describedby={`${id}-hint${fieldValid ? '' : ` ${id}-error`}`}
            onChange={event => edit(entry.key, event.target.value)} />}
    </div>;
  }

  return <section className="settings-view" data-testid="settings-view" aria-label={uiText('应用设置', 'Application settings')} aria-busy={loading || saving}>
    <div className="settings-content">
      <header className="settings-heading">
        <div><h1>{uiText('设置', 'Settings')}</h1></div>
        {windowBridge.canOpenPreferences && <button className="settings-secondary" type="button" disabled={saving || dirty}
          onClick={() => { void openAdvanced(); }}>
          <ExternalLink size={14} />{uiText('高级设置', 'Advanced settings')}</button>}
      </header>
      {error && <div className="settings-error" role="alert"><strong>{uiText('无法完成操作', 'Could not complete the operation')}</strong><p>{error}</p>
        {windowBridge.canManagePreferences && <span>{uiText('当前更改尚未保存。重新加载将丢弃页面中的更改。', 'Your changes have not been saved. Reloading discards changes on this page.')}</span>}</div>}
      {!windowBridge.canManagePreferences ? <div className="settings-unavailable">
        <Settings2 size={24} /><h2>{uiText('应用设置不可用', 'Application settings are unavailable')}</h2>
        <p>{uiText('请在桌面应用中打开。', 'Open this page in the desktop app.')}</p>
      </div> : <>
        <div className="settings-controls">
          <div className="settings-sections" role="group" aria-label={uiText('设置分类', 'Settings categories')}>
            {groups.map(([key, name]) => <button key={key} type="button" aria-pressed={!query && section === key}
              onClick={() => { setSection(key); setSearch(''); }}>{name}</button>)}
          </div>
          <label className="settings-search"><Search size={15} /><input type="search" value={search}
            placeholder={uiText('搜索设置', 'Search settings')} aria-label={uiText('搜索设置', 'Search settings')}
            onChange={event => setSearch(event.target.value)} /></label>
        </div>
        {loading ? <p className="settings-empty" role="status">{uiText('正在读取设置…', 'Loading settings…')}</p>
          : <form onSubmit={event => { event.preventDefault(); void save(); }}>
            <fieldset className="settings-fields" disabled={saving}>
              <legend className="settings-sr-only">{query ? uiText('搜索结果', 'Search results') : groups.find(([key]) => key === section)?.[1]}</legend>
              {visible.filter(entry => query || !nativeEditorKeys.has(entry.key)).map(renderEntry)}
              {!query && section === 'ide' && visible.some(entry => nativeEditorKeys.has(entry.key)) && <details className="settings-advanced-editor">
                <summary>{uiText('原生代码编辑器', 'Native code editor')}</summary>
                {visible.filter(entry => nativeEditorKeys.has(entry.key)).map(renderEntry)}
              </details>}
              {visible.length === 0 && <p className="settings-empty">{query ? uiText('没有匹配的设置。', 'No matching settings.') : uiText('此分类暂无可用设置。', 'No settings are available in this category.')}</p>}
            </fieldset>
            <footer className="settings-savebar">
              <span role="status">{saving ? uiText('正在保存…', 'Saving…') : dirty ? uiText('有未保存的更改', 'Unsaved changes')
                : saved ? <><Check size={14} />{uiText('设置已保存', 'Settings saved')}</> : null}</span>
              <div><button type="button" className="settings-secondary" disabled={loading || saving} onClick={() => { void reload(); }}>
                <RotateCcw size={14} />{dirty ? uiText('放弃并重新加载', 'Discard & reload') : uiText('重新加载', 'Reload')}</button>
                <button type="submit" className="settings-primary" disabled={!dirty || invalid || loading || saving}>{uiText('保存更改', 'Save changes')}</button></div>
            </footer>
          </form>}
      </>}
    </div>
  </section>;
}
