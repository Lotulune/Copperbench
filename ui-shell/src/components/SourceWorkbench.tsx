import React, { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { ChevronRight, FileCode2, Folder, RefreshCw, Save, Search, X } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { sourceBridge } from '../bridge/sourceBridge';
import { windowBridge, type AppPreferencesSnapshot } from '../bridge/windowBridge';
import { setUnsavedDraftCount } from '../hooks/unsavedDraftGuard';
import { t, uiText } from '../i18n';
import type { WorkspaceSourceContent, WorkspaceSourceFile, WorkspaceSourceFiles, WorkspaceSourceIndex } from '../types/contract';
import './sourceWorkbench.css';

export interface SourceFocusRequest { path: string; line?: number; requestId?: string | number }
interface SourceTab {
  path: string;
  file?: WorkspaceSourceContent;
  content: string;
  revision: number;
  loading: boolean;
  readToken?: number;
  saving: boolean;
  error?: string;
  conflict?: boolean;
  latest?: { data: WorkspaceSourceContent; revision: number };
}
interface SourceSession { tabs: SourceTab[]; activePath: string | null; focusKey?: string }
// Session drafts survive route unmounts. Workspace IDs isolate unrelated files
// with identical paths. Original content/hash remain attached to each draft.
const sessions = new Map<string, SourceSession>();
const listeners = new Set<() => void>();
let readSequence = 0;
function session(id: string): SourceSession {
  if (!sessions.has(id)) sessions.set(id, { tabs: [], activePath: null });
  return sessions.get(id)!;
}
function update(id: string, change: (state: SourceSession) => SourceSession) {
  sessions.set(id, change(session(id)));
  syncDraftGuard();
  listeners.forEach(listener => listener());
}
function changeTab(id: string, path: string, change: (tab: SourceTab) => SourceTab) {
  update(id, state => ({ ...state, tabs: state.tabs.map(tab => tab.path === path ? change(tab) : tab) }));
}
// Textareas normalize CRLF/CR to LF. Keep the Core baseline byte-for-byte and
// translate only at the editor/save boundary so offsets and hashes stay honest.
function editorText(content: string) { return content.replace(/\r\n?/g, '\n'); }
function sourceText(content: string, original: string) {
  const newline = original.match(/\r\n|\r|\n/)?.[0] ?? '\n';
  return editorText(content).replace(/\n/g, newline);
}
function dirty(tab: SourceTab) { return Boolean(tab.file && tab.content !== editorText(tab.file.content)); }
function syncDraftGuard() { setUnsavedDraftCount('sources', [...sessions.values()].reduce((count, state) => count + state.tabs.filter(dirty).length, 0)); }
function message(error: unknown) { return error instanceof Error ? error.message : String(error); }

type Tree = { folders: Map<string, Tree>; files: WorkspaceSourceFile[] };
function fileTree(files: WorkspaceSourceFile[]): Tree {
  const root: Tree = { folders: new Map(), files: [] };
  for (const file of files) {
    let branch = root;
    for (const part of file.relativePath.split('/').slice(0, -1)) {
      if (!branch.folders.has(part)) branch.folders.set(part, { folders: new Map(), files: [] });
      branch = branch.folders.get(part)!;
    }
    branch.files.push(file);
  }
  return root;
}
function SourceTree({ tree, selected, open }: { tree: Tree; selected: string | null; open: (path: string) => void }) {
  return <ul className="source-tree">
    {[...tree.folders].map(([name, branch]) => <li key={`folder:${name}`}><details open>
      <summary><ChevronRight size={12} aria-hidden="true" /><Folder size={14} aria-hidden="true" /><span>{name}</span></summary>
      <SourceTree tree={branch} selected={selected} open={open} />
    </details></li>)}
    {tree.files.map(file => <li key={file.relativePath}><button type="button" className={selected === file.relativePath ? 'is-selected' : ''}
      title={file.relativePath} aria-pressed={selected === file.relativePath} onClick={() => open(file.relativePath)} data-source-path={file.relativePath}>
      <FileCode2 size={14} aria-hidden="true" /><span>{file.name}</span>{!file.editable && <small>{uiText('只读', 'Read only')}</small>}
    </button></li>)}
  </ul>;
}

export const SourceWorkbench: React.FC<{ active?: boolean; focusRequest?: SourceFocusRequest | null }> = ({ active = true, focusRequest }) => {
  const { state } = useWorkbench();
  useEffect(syncDraftGuard, []);
  const workspaceId = state.workbench?.workspace.id;
  return workspaceId ? <SourceWorkspace key={workspaceId} workspaceId={workspaceId} active={active} focusRequest={focusRequest} />
    : <section hidden={!active} className="source-workbench"><p>{uiText('请先打开工作区。', 'Open a workspace first.')}</p></section>;
};

function SourceWorkspace({ workspaceId, active, focusRequest }: { workspaceId: string; active: boolean; focusRequest?: SourceFocusRequest | null }) {
  const current = useSyncExternalStore(listener => { listeners.add(listener); return () => { listeners.delete(listener); }; }, () => session(workspaceId));
  const tab = current.tabs.find(item => item.path === current.activePath);
  const [mobilePanel, setMobilePanel] = useState<'files' | 'editor'>(current.activePath ? 'editor' : 'files');
  const [listing, setListing] = useState<WorkspaceSourceFiles | null>(null);
  const [index, setIndex] = useState<WorkspaceSourceIndex | null>(null);
  const [indexLimit, setIndexLimit] = useState(30);
  const [search, setSearch] = useState('');
  const [listBusy, setListBusy] = useState(false);
  const [listError, setListError] = useState('');
  const [indexError, setIndexError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [find, setFind] = useState('');
  const [findIndex, setFindIndex] = useState(-1);
  const [line, setLine] = useState('1');
  const [closeCandidate, setCloseCandidate] = useState<string | null>(null);
  const [editorPreferences, setEditorPreferences] = useState({ fontSize: 13, lineNumbers: true, ligatures: false });
  const [preferencesError, setPreferencesError] = useState('');
  const textarea = useRef<HTMLTextAreaElement>(null);
  const gutter = useRef<HTMLPreElement>(null);
  const findInput = useRef<HTMLInputElement>(null);
  const lineInput = useRef<HTMLInputElement>(null);
  const fileSearchInput = useRef<HTMLInputElement>(null);
  const pendingLine = useRef<{ path: string; line: number } | null>(null);
  const listSequence = useRef(0);
  const listSearch = useRef('');
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; listSequence.current++; }; }, []);

  useEffect(() => {
    if (!windowBridge.canManagePreferences) return;
    let valid = true;
    let savedSinceRead = false;
    const accept = (snapshot: AppPreferencesSnapshot) => {
      if (!valid) return;
      const values = new Map(snapshot.entries.map(entry => [entry.key, entry.value]));
      setEditorPreferences(previous => ({
        fontSize: typeof values.get('ide.fontSize') === 'number' && Number.isFinite(values.get('ide.fontSize'))
          ? Math.max(8, Math.min(96, Number(values.get('ide.fontSize')))) : previous.fontSize,
        lineNumbers: typeof values.get('ide.lineNumbers') === 'boolean' ? Boolean(values.get('ide.lineNumbers')) : previous.lineNumbers,
        ligatures: typeof values.get('ide.useLigatures') === 'boolean' ? Boolean(values.get('ide.useLigatures')) : previous.ligatures
      }));
      setPreferencesError('');
    };
    const changed = (event: Event) => { savedSinceRead = true; accept((event as CustomEvent<AppPreferencesSnapshot>).detail); };
    window.addEventListener('copperbench:preferences-saved', changed);
    void windowBridge.getPreferences().then(snapshot => { if (!savedSinceRead) accept(snapshot); })
      .catch(error => { if (valid && !savedSinceRead) setPreferencesError(message(error)); });
    return () => { valid = false; window.removeEventListener('copperbench:preferences-saved', changed); };
  }, []);

  useEffect(() => {
    if (!active) return;
    const sequence = ++listSequence.current;
    setListBusy(true); setListError('');
    const timer = window.setTimeout(() => {
      void sourceBridge.list(workspaceId, search).then(result => {
        if (!alive.current || sequence !== listSequence.current) return;
        listSearch.current = search; setListing(result.data);
      }).catch(error => { if (alive.current && sequence === listSequence.current) setListError(message(error)); })
        .finally(() => { if (alive.current && sequence === listSequence.current) setListBusy(false); });
    }, search ? 200 : 0);
    return () => { clearTimeout(timer); listSequence.current++; };
  }, [workspaceId, search, refresh, active]);

  useEffect(() => {
    if (!active) return;
    let valid = true;
    void sourceBridge.index(workspaceId).then(result => { if (valid) { setIndex(result.data); setIndexError(''); } })
      .catch(error => { if (valid) setIndexError(message(error)); });
    return () => { valid = false; };
  }, [workspaceId, refresh, active]);

  const loadMore = async () => {
    if (!listing || listing.nextOffset === null || listBusy) return;
    const sequence = ++listSequence.current;
    setListBusy(true);
    try {
      const result = await sourceBridge.list(workspaceId, listSearch.current, listing.nextOffset);
      if (!alive.current || sequence !== listSequence.current) return;
      setListing(previous => previous ? { ...result.data, files: [...previous.files, ...result.data.files.filter(file => !previous.files.some(old => old.relativePath === file.relativePath))] } : result.data);
    } catch (error) { if (alive.current && sequence === listSequence.current) setListError(message(error)); }
    finally { if (alive.current && sequence === listSequence.current) setListBusy(false); }
  };

  const read = async (path: string) => {
    const readToken = ++readSequence;
    changeTab(workspaceId, path, old => ({ ...old, loading: true, readToken }));
    try {
      const result = await sourceBridge.read(workspaceId, path);
      if (result.data.relativePath !== path) throw new Error(uiText('返回的文件路径不匹配。', 'The returned file path does not match.'));
      changeTab(workspaceId, path, old => {
        if (old.readToken !== readToken) return old;
        if (dirty(old) && old.file?.sha256 !== result.data.sha256) return { ...old, loading: false, conflict: true, latest: result, error: undefined };
        return { ...old, file: result.data, content: dirty(old) ? old.content : editorText(result.data.content),
          revision: result.revision, loading: false, conflict: false, latest: undefined, error: undefined };
      });
    } catch (error) { changeTab(workspaceId, path, old => old.readToken !== readToken ? old : ({ ...old, loading: false, error: message(error) })); }
  };

  const open = (path: string, atLine?: number) => {
    setMobilePanel('editor');
    if (atLine) pendingLine.current = { path, line: atLine };
    const existing = session(workspaceId).tabs.find(item => item.path === path);
    update(workspaceId, state => ({ ...state, activePath: path, tabs: existing ? state.tabs
      : [...state.tabs, { path, content: '', revision: 0, loading: true, saving: false }] }));
    if (!existing) void read(path);
  };
  useEffect(() => {
    if (!active || !focusRequest?.path) return;
    const key = JSON.stringify([focusRequest.path, focusRequest.line, focusRequest.requestId]);
    if (session(workspaceId).focusKey === key) return;
    update(workspaceId, state => ({ ...state, focusKey: key }));
    open(focusRequest.path, focusRequest.line);
  }, [active, focusRequest?.path, focusRequest?.line, focusRequest?.requestId]);

  const selectRange = (start: number, end: number) => {
    const input = textarea.current;
    if (!input) return;
    input.focus(); input.setSelectionRange(start, end);
    const row = input.value.slice(0, start).split('\n').length;
    input.scrollTop = Math.max(0, (row - 3) * (parseFloat(getComputedStyle(input).lineHeight) || 21));
    if (gutter.current) gutter.current.scrollTop = input.scrollTop;
  };
  const goToLine = (atLine: number) => {
    if (!tab?.file) return;
    const lines = tab.content.split('\n');
    const target = Math.max(1, Math.min(lines.length, Math.trunc(atLine) || 1));
    const start = lines.slice(0, target - 1).reduce((total, value) => total + value.length + 1, 0);
    setLine(String(target)); selectRange(start, start + lines[target - 1].length);
  };
  useEffect(() => {
    if (!active || !tab?.file || tab.loading || pendingLine.current?.path !== tab.path) return;
    const target = pendingLine.current.line; pendingLine.current = null; goToLine(target);
  }, [active, tab]);
  useEffect(() => { setFindIndex(-1); if (gutter.current) gutter.current.scrollTop = 0; }, [current.activePath, find]);
  useEffect(() => {
    if (active && mobilePanel === 'editor' && tab?.file && !tab.loading && window.matchMedia('(max-width: 760px)').matches) textarea.current?.focus();
  }, [active, mobilePanel, tab?.path, tab?.loading]);

  const switchMobilePanel = () => {
    const next = mobilePanel === 'files' ? 'editor' : 'files';
    setMobilePanel(next);
    window.requestAnimationFrame(() => { (next === 'files' ? fileSearchInput.current : textarea.current)?.focus(); });
  };

  const save = async () => {
    const selected = session(workspaceId).tabs.find(item => item.path === session(workspaceId).activePath);
    if (!selected?.file?.editable || !dirty(selected) || selected.saving || selected.loading || selected.conflict) return;
    const path = selected.path;
    const submittedContent = sourceText(selected.content, selected.file.content);
    changeTab(workspaceId, path, old => ({ ...old, saving: true, error: undefined }));
    try {
      const result = await sourceBridge.save(workspaceId, path, submittedContent, selected.revision, selected.file.sha256);
      if (result.workspaceId !== workspaceId) throw new Error(uiText('工作区已切换，未接受此保存回执。', 'The workspace changed; this save receipt was not accepted.'));
      if ((result.status === 'committed' || result.status === 'completed') && result.data?.sha256 && result.data.relativePath === path) {
        changeTab(workspaceId, path, old => ({ ...old, file: { ...selected.file!, content: submittedContent,
          sha256: result.data!.sha256!, size: result.data!.size ?? selected.file!.size }, revision: result.newRevision,
          saving: false, error: undefined, conflict: false, latest: undefined }));
        if (alive.current) setRefresh(value => value + 1);
      } else {
        const conflict = Boolean(result.conflict) || result.diagnostics.some(item => item.code === 'WORKSPACE_SOURCE_CONFLICT' || item.code === 'WORKSPACE_REVISION_CONFLICT');
        changeTab(workspaceId, path, old => ({ ...old, saving: false, conflict,
          error: result.diagnostics.map(item => t(item.message)).join('\n') || uiText('保存失败，请重试。', 'Save failed. Please retry.') }));
        if (conflict) await read(path);
      }
    } catch (error) { changeTab(workspaceId, path, old => ({ ...old, saving: false, error: message(error) })); }
  };

  const close = (path: string) => {
    if (session(workspaceId).tabs.some(item => item.path === path && (item.saving || item.loading))) return;
    update(workspaceId, state => {
      const remaining = state.tabs.filter(item => item.path !== path);
      return { ...state, tabs: remaining, activePath: state.activePath === path ? remaining.at(-1)?.path ?? null : state.activePath };
    });
    setCloseCandidate(null);
  };
  const adoptLatest = (keepDraft: boolean) => {
    if (!tab?.latest) return;
    changeTab(workspaceId, tab.path, old => ({ ...old, file: old.latest!.data,
      content: keepDraft ? old.content : editorText(old.latest!.data.content), revision: old.latest!.revision,
      latest: undefined, conflict: false, error: undefined }));
  };
  const matches = useMemo(() => {
    if (!find || !tab?.content) return [];
    const found: number[] = [];
    let offset = 0;
    while (offset <= tab.content.length) {
      const next = tab.content.indexOf(find, offset);
      if (next < 0) break;
      found.push(next); offset = next + Math.max(find.length, 1);
    }
    return found;
  }, [find, tab?.content]);
  const nextMatch = (direction: number) => {
    if (!matches.length) return;
    const selected = findIndex < 0 ? (direction > 0 ? 0 : matches.length - 1) : (findIndex + direction + matches.length) % matches.length;
    setFindIndex(selected); selectRange(matches[selected], matches[selected] + find.length);
  };
  const tree = useMemo(() => fileTree(listing?.files ?? []), [listing]);
  const lineNumbers = useMemo(() => Array.from({ length: (tab?.content.match(/\n/g)?.length ?? 0) + 1 }, (_, value) => value + 1).join('\n'), [tab?.content]);

  return <section hidden={!active} className="source-workbench" data-testid="source-workbench" data-mobile-panel={mobilePanel} onKeyDown={event => {
    if (!(event.ctrlKey || event.metaKey)) return;
    if (event.key.toLowerCase() === 's') { event.preventDefault(); void save(); }
    if (event.key.toLowerCase() === 'f') { event.preventDefault(); findInput.current?.focus(); }
    if (event.key.toLowerCase() === 'g') { event.preventDefault(); lineInput.current?.focus(); lineInput.current?.select(); }
  }}>
    <div className="source-mobile-navigation">
      {mobilePanel === 'editor' ? <><button type="button" onClick={switchMobilePanel} data-testid="source-files-toggle"><Folder size={15} aria-hidden="true" />{uiText('文件', 'Files')}</button><span>{tab?.file?.name}</span></>
        : <><span>{uiText('项目文件', 'Project files')}</span>{tab && <button type="button" onClick={switchMobilePanel} data-testid="source-editor-toggle">{uiText('返回编辑器', 'Back to editor')}<ChevronRight size={15} aria-hidden="true" /></button>}</>}
    </div>
    <aside className="source-explorer" aria-label={uiText('源码文件', 'Source files')}>
      <header><h2>{uiText('源码', 'Source')}</h2><button type="button" aria-label={uiText('刷新源码文件', 'Refresh source files')}
        onClick={() => setRefresh(value => value + 1)} disabled={listBusy}><RefreshCw size={15} aria-hidden="true" /></button></header>
      <label className="source-file-search"><Search size={14} aria-hidden="true" /><input ref={fileSearchInput} value={search} onChange={event => setSearch(event.target.value)}
        aria-label={uiText('搜索文件路径', 'Search file paths')} placeholder={uiText('搜索文件', 'Find files')} /></label>
      <div className="source-tree-scroll" aria-busy={listBusy}>
        {listError && <p role="alert">{listError}</p>}
        <SourceTree tree={tree} selected={current.activePath} open={open} />
        {!listBusy && listing?.files.length === 0 && <p>{uiText('没有匹配的文件。', 'No matching files.')}</p>}
        {listing?.nextOffset !== null && listing?.nextOffset !== undefined && <button type="button" onClick={() => void loadMore()} disabled={listBusy}>{uiText('加载更多文件', 'Load more files')}</button>}
        {listing?.truncated && listing.nextOffset === null && <p>{uiText('文件列表已达到扫描上限。', 'The file scan limit was reached.')}</p>}
        <details className="source-entrypoints"><summary>{uiText('代码入口与引用', 'Code entrypoints and references')}</summary>
          {indexError && <p role="alert">{indexError}</p>}
          {index?.entries.slice(0, indexLimit).map(entry => <button type="button" key={entry.id} title={entry.evidence} onClick={() => open(entry.relativePath, entry.line)}>
            <strong>{entry.symbol}</strong><span>{entry.relativePath}:{entry.line}</span><small>{entry.evidence}</small>
          </button>)}
          {index && index.entries.length === 0 && <p>{uiText('未发现代码入口。', 'No code entrypoints found.')}</p>}
          {index && index.entries.length > indexLimit && <button type="button" onClick={() => setIndexLimit(value => value + 30)}>{uiText('显示更多入口', 'Show more entrypoints')}</button>}
          {index?.truncated && <p>{uiText('代码索引已达到扫描上限。', 'The source index scan limit was reached.')}</p>}
        </details>
      </div>
      <footer>{listing ? `${listing.files.length} / ${listing.total}` : uiText('正在读取文件…', 'Loading files…')}</footer>
    </aside>
    <div className="source-editor-pane">
      <div className="source-tabs" role="tablist" aria-label={uiText('已打开的源码', 'Open source files')}>
        {current.tabs.map(item => <div key={item.path} className={item.path === current.activePath ? 'is-active' : ''}>
          <button type="button" role="tab" aria-selected={item.path === current.activePath} title={item.path} onClick={() => open(item.path)}>
            <FileCode2 size={14} aria-hidden="true" />{item.file?.name ?? item.path.split('/').at(-1)}{dirty(item) && <span aria-label={uiText('未保存', 'Unsaved')}>●</span>}
          </button><button type="button" disabled={item.loading || item.saving} aria-label={`${uiText('关闭', 'Close')} ${item.path}`}
            onClick={() => dirty(item) ? setCloseCandidate(item.path) : close(item.path)}><X size={13} aria-hidden="true" /></button>
        </div>)}
      </div>
      {closeCandidate && <div className="source-notice" role="alert"><span>{uiText('关闭此文件将放弃未保存的草稿。', 'Closing this file discards its unsaved draft.')}</span>
        <button type="button" onClick={() => close(closeCandidate)}>{uiText('关闭并放弃草稿', 'Close and discard draft')}</button>
        <button type="button" onClick={() => setCloseCandidate(null)}>{uiText('继续编辑', 'Keep editing')}</button></div>}
      {tab ? <>
        <header className="source-editor-toolbar"><span title={tab.path}>{tab.path}</span>
          <button type="button" onClick={() => { changeTab(workspaceId, tab.path, old => ({ ...old, loading: true })); void read(tab.path); }} disabled={tab.loading || tab.saving} data-testid="source-reload">
            <RefreshCw size={14} aria-hidden="true" />{uiText('读取最新版本', 'Read latest version')}</button>
          <button type="button" onClick={() => void save()} disabled={!tab.file?.editable || !dirty(tab) || tab.loading || tab.saving || tab.conflict} data-testid="source-save">
            <Save size={14} aria-hidden="true" />{tab.saving ? uiText('保存中…', 'Saving…') : uiText('保存', 'Save')}</button></header>
        {tab.error && <p className="source-notice" role="alert">{tab.error}</p>}
        {preferencesError && <p className="source-notice" role="alert">{uiText('无法读取编辑器设置：', 'Could not read editor preferences: ')}{preferencesError}</p>}
        {tab.latest && <div className="source-conflict" role="alert" data-testid="source-conflict">
          <p>{uiText('文件已更新；草稿已保留。', 'The file changed; your draft is preserved.')}</p>
          <details><summary>{uiText('查看磁盘上的最新内容', 'View latest content on disk')}</summary><pre>{tab.latest.data.content}</pre></details>
          <button type="button" onClick={() => adoptLatest(false)}>{uiText('放弃草稿并加载最新', 'Discard draft and load latest')}</button>
          <button type="button" onClick={() => adoptLatest(true)}>{uiText('基于最新版本保留草稿', 'Keep draft against latest version')}</button>
        </div>}
        {tab.file && !tab.file.editable && <p className="source-readonly" role="status" title={tab.file.reasonCode ?? undefined}>
          {tab.file.ownership === 'generated' ? uiText('生成文件 · 只读', 'Generated file · Read only') : uiText('此文件只读', 'This file is read only')}</p>}
        <div className="source-findbar"><label>{uiText('查找', 'Find')}<input ref={findInput} value={find} onChange={event => setFind(event.target.value)}
          aria-label={uiText('在文件中查找', 'Find in file')} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); nextMatch(event.shiftKey ? -1 : 1); } }} /></label>
          <span role="status">{find ? `${findIndex >= 0 && findIndex < matches.length ? findIndex + 1 : 0} / ${matches.length}` : ''}</span>
          <button type="button" onClick={() => nextMatch(-1)} disabled={!matches.length}>{uiText('上一处', 'Previous')}</button><button type="button" onClick={() => nextMatch(1)} disabled={!matches.length}>{uiText('下一处', 'Next')}</button>
          <label>{uiText('行', 'Line')}<input ref={lineInput} type="number" min="1" value={line} onChange={event => setLine(event.target.value)} aria-label={uiText('跳转到行', 'Go to line')}
            onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); goToLine(Number(line)); } }} /></label><button type="button" onClick={() => goToLine(Number(line))}>{uiText('跳转', 'Go')}</button>
        </div>
        <div className="source-code" aria-busy={tab.loading} style={{ '--source-font-size': `${editorPreferences.fontSize}px`,
          '--source-line-height': `${Math.round(editorPreferences.fontSize * 1.6)}px`,
          fontVariantLigatures: editorPreferences.ligatures ? 'normal' : 'none' } as React.CSSProperties}>
          <pre ref={gutter} hidden={!editorPreferences.lineNumbers} aria-hidden="true" className="source-line-numbers">{lineNumbers}</pre>
          <textarea ref={textarea} value={tab.content} readOnly={!tab.file?.editable || tab.loading || tab.saving} spellCheck={false} wrap="off" autoCapitalize="off" autoCorrect="off"
            aria-label={uiText('源码编辑器', 'Source editor')} data-testid="source-editor" onScroll={event => { if (gutter.current) gutter.current.scrollTop = event.currentTarget.scrollTop; }}
            onChange={event => { const content = event.target.value; changeTab(workspaceId, tab.path, old => ({ ...old, content })); }} />
        </div>
        <footer className="source-editor-status" role="status"><span>{tab.loading ? uiText('正在读取…', 'Loading…') : tab.file?.language}</span>
          <span title={`${uiText('修订', 'Revision')} ${tab.revision}`}>{dirty(tab) ? uiText('未保存', 'Unsaved') : uiText('已保存', 'Saved')}</span></footer>
      </> : <div className="source-empty"><FileCode2 size={28} aria-hidden="true" /><h2>{uiText('打开项目文件', 'Open a project file')}</h2></div>}
    </div>
  </section>;
}
