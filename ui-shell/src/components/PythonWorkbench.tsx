import React, { useEffect, useRef, useState } from 'react';
import { Play, Square, RotateCcw, FolderOpen, Save, Terminal, FileCode } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { pythonBridge, PythonStatus } from '../bridge/pythonBridge';
import './pythonWorkbench.css';

const examples = {
  inspect: `import copperbench as cb\n\nprint(cb.context.workspace.workspace_id)\nelement = cb.context.active_element\nif element:\n    print(element.name, element.fields)\nelse:\n    print("请先在模组元素页选中一个元素")\n`,
  operator: `import copperbench as cb\n\nclass RenameSelected(cb.types.Operator):\n    idname = "demo.rename_selected"\n    label = "重命名选中元素的显示名称"\n\n    @classmethod\n    def poll(cls, context):\n        return context.active_element is not None\n\n    def execute(self, context):\n        context.active_element.display_name = "脚本修改的名称"\n\ncb.utils.register_class(RenameSelected)\nprint("已注册；点击右侧操作，或执行 cb.ops.demo.rename_selected()")\n`,
  timer: `import copperbench as cb\n\ndef once():\n    print("当前选中：", cb.context.active_element)\n    return None  # 返回秒数可重复；None 表示结束\n\ncb.app.timers.register(once, first_interval=1)\n`,
  events: `import copperbench as cb\n\ndef on_change(context):\n    print("工作区已更新：", context.workspace.revision)\n\ncb.app.handlers.workspace_update.append(on_change)\nprint("已订阅；移除时使用 cb.app.handlers.workspace_update.remove(on_change)")\n`
};

const stateLabels = { stopped: '未启动', starting: '启动中', running: '执行中', ready: '就绪', failed: '解释器已退出' };
const resultLabels = { idle: '', running: '脚本执行中', succeeded: '脚本成功', failed: '脚本失败', incomplete: '等待后续输入', cancelled: '脚本已停止' };

export function PythonWorkbench({ visible }: { visible: boolean }) {
  const { state, selectedElement } = useWorkbench();
  const storageKey = `copperbench.python.${state.workbench!.workspace.id}`;
  const [source, setSource] = useState(() => { try { return localStorage.getItem(storageKey) ?? examples.inspect; } catch { return examples.inspect; } });
  const [savedSource, setSavedSource] = useState(() => { try { return localStorage.getItem(storageKey + '.saved') ?? (localStorage.getItem(storageKey) === null ? source : ''); } catch { return source; } });
  const [filename, setFilename] = useState(() => { try { return localStorage.getItem(storageKey + '.filename') ?? 'script.py'; } catch { return 'script.py'; } });
  const [python, setPython] = useState(() => { try { return localStorage.getItem('copperbench.python.executable') ?? ''; } catch { return ''; } });
  const [status, setStatus] = useState<PythonStatus | null>(null);
  const [output, setOutput] = useState<PythonStatus['output']>([]);
  const [truncated, setTruncated] = useState(false);
  const [consoleInput, setConsoleInput] = useState('');
  const [error, setError] = useState('');
  const [history, setHistory] = useState<string[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const sequence = useRef(0);
  const retainedEntries = useRef(0);
  const editor = useRef<HTMLTextAreaElement>(null);
  const consoleEditor = useRef<HTMLTextAreaElement>(null);
  const log = useRef<HTMLDivElement>(null);
  const completionTarget = useRef<{ target: 'script' | 'console'; start: number; end: number; value: string } | null>(null);
  const completionRequest = useRef('');
  const submitted = useRef(false);
  const requestEpoch = useRef(0);
  const [sending, setSending] = useState(false);
  const busy = sending || status?.state === 'starting' || status?.state === 'running';
  const lastMode = useRef('script');

  useEffect(() => { try { localStorage.setItem(storageKey, source); } catch { /* Keep the in-memory draft. */ } }, [source, storageKey]);
  useEffect(() => { try { localStorage.setItem(storageKey + '.saved', savedSource); localStorage.setItem(storageKey + '.filename', filename); } catch { } }, [savedSource, filename, storageKey]);
  useEffect(() => { try { localStorage.setItem('copperbench.python.executable', python); } catch { } }, [python]);

  const apply = (snapshot: PythonStatus) => {
    setStatus(snapshot);
    if (snapshot.truncated) setTruncated(true);
    const entries = snapshot.output.filter(entry => entry.sequence > sequence.current);
    if (entries.length) {
      if (retainedEntries.current + entries.length > 500) setTruncated(true);
      retainedEntries.current = Math.min(500, retainedEntries.current + entries.length);
      setOutput(current => [...current, ...entries].slice(-500));
    }
    sequence.current = Math.max(sequence.current, snapshot.lastSequence);
    if (snapshot.requestId === completionRequest.current && snapshot.state === 'ready') {
      const target = completionTarget.current;
      const field = target?.target === 'script' ? editor.current : consoleEditor.current;
      if (target && field?.value === target.value && field.selectionStart === target.end)
        setSuggestions(snapshot.completions);
      completionRequest.current = '';
    }
  };
  const applyRef = useRef(apply);
  applyRef.current = apply;
  useEffect(() => {
    let disposed = false;
    let polling = false;
    const poll = async () => {
      if (!pythonBridge.available || polling) return;
      polling = true;
      const epoch = requestEpoch.current;
      try {
        const snapshot = await pythonBridge.invoke<PythonStatus>({ operation: 'status', afterSequence: sequence.current });
        if (!disposed && epoch === requestEpoch.current) applyRef.current(snapshot);
      } catch (cause) { if (!disposed) setError(String(cause)); }
      finally { polling = false; }
    };
    void poll();
    const timer = window.setInterval(poll, 300);
    return () => { disposed = true; window.clearInterval(timer); };
  }, []);
  useEffect(() => { if (log.current) log.current.scrollTop = log.current.scrollHeight; }, [output]);

  const invoke = async (payload: Record<string, unknown>) => {
    if (submitted.current) return;
    submitted.current = true;
    requestEpoch.current++;
    setSending(true);
    setError('');
    setSuggestions([]);
    try {
      const snapshot = await pythonBridge.invoke<PythonStatus>({ ...payload, python });
      if (payload.operation === 'complete') completionRequest.current = snapshot.requestId;
      apply(snapshot);
    } catch (cause) { setError(String(cause)); }
    finally { submitted.current = false; setSending(false); }
  };
  const runScript = () => { lastMode.current = 'script'; void invoke({ operation: 'execute', source, mode: 'script', filename }); };
  const runConsole = (text = consoleInput) => {
    if (!text.trim() || busy) return;
    lastMode.current = 'console';
    setHistory(current => [...current, text].slice(-100));
    setHistoryIndex(-1);
    setConsoleInput('');
    void invoke({ operation: 'execute', source: text, mode: 'console', filename: '<console>' });
  };
  const complete = (target: 'script' | 'console') => {
    const field = target === 'script' ? editor.current : consoleEditor.current;
    if (!field || busy) return;
    const end = field.selectionStart;
    const prefix = /[\w.]+$/.exec(field.value.slice(0, end))?.[0] ?? '';
    completionTarget.current = { target, start: end - prefix.length, end, value: field.value };
    void invoke({ operation: 'complete', text: prefix });
  };
  const acceptCompletion = (text: string) => {
    const target = completionTarget.current;
    if (!target) return;
    const field = target.target === 'script' ? editor.current : consoleEditor.current;
    if (!field) return;
    const value = field.value.slice(0, target.start) + text + field.value.slice(target.end);
    (target.target === 'script' ? setSource : setConsoleInput)(value);
    setSuggestions([]);
    requestAnimationFrame(() => { field.focus(); field.setSelectionRange(target.start + text.length, target.start + text.length); });
  };
  const chooseFile = async (operation: 'open_script' | 'save_script' | 'select_python') => {
    if (operation === 'open_script' && source !== savedSource && !window.confirm('打开脚本会替换当前未保存的草稿，是否继续？')) return;
    try {
      const result = await pythonBridge.invoke<{ cancelled: boolean; path: string; source?: string }>({ operation, source });
      if (result.cancelled) return;
      if (operation === 'select_python') setPython(result.path);
      else {
        setFilename(result.path);
        if (operation === 'open_script') { setSource(result.source!); setSavedSource(result.source!); }
        else setSavedSource(source);
      }
    } catch (cause) { setError(String(cause)); }
  };
  const keyboard = (event: React.KeyboardEvent<HTMLTextAreaElement>, target: 'script' | 'console') => {
    if (event.ctrlKey && event.code === 'Space') { event.preventDefault(); complete(target); }
    else if (event.ctrlKey && event.key === 's') { event.preventDefault(); void chooseFile('save_script'); }
    else if (event.key === 'Escape') setSuggestions([]);
    else if (event.key === 'Enter' && !busy && (target === 'script' ? event.ctrlKey : !event.shiftKey)) {
      event.preventDefault(); target === 'script' ? runScript() : runConsole();
    } else if (target === 'console' && ['ArrowUp', 'ArrowDown'].includes(event.key) && !consoleInput.includes('\n')) {
      event.preventDefault();
      const next = event.key === 'ArrowUp' ? (historyIndex < 0 ? history.length - 1 : Math.max(0, historyIndex - 1))
        : historyIndex >= history.length - 1 ? -1 : historyIndex + 1;
      setHistoryIndex(next); setConsoleInput(next < 0 ? '' : history[next] ?? '');
    } else if (event.key === 'Tab' && !event.shiftKey) {
      // Tab remains normal focus navigation; indentation uses an explicit shortcut.
      if (event.ctrlKey) {
        event.preventDefault();
        const field = event.currentTarget;
        const position = field.selectionStart;
        (target === 'script' ? setSource : setConsoleInput)(field.value.slice(0, position) + '    ' + field.value.slice(field.selectionEnd));
        requestAnimationFrame(() => field.setSelectionRange(position + 4, position + 4));
      }
    }
  };

  return <section className="python-workbench" hidden={!visible} data-testid="python-workbench">
    <header className="python-heading"><div><h1><Terminal size={22} /> Python 工作台</h1><p>当前工程直接可用 · 变量跨运行保留 · MCP 为可选入口</p></div>
      <span role="status">{stateLabels[status?.state ?? 'stopped']}{status?.lastResult && status.lastResult !== 'idle' ? ` · ${resultLabels[status.lastResult]}` : ''}</span></header>
    {!pythonBridge.available && <p role="alert">请在支持 Python 工作台的桌面版本中打开工程；浏览器预览不会执行脚本。</p>}
    {error && <p role="alert" className="python-error">{error}。无法启动时，请选择 Python 3.11 或更新版本的解释器。</p>}
    <div className="python-runtime"><label>Python 解释器<input aria-label="Python 解释器路径" value={python} onChange={event => setPython(event.target.value)} placeholder="留空自动查找，也可选择解释器路径" disabled={busy} /></label>
      <button onClick={() => void chooseFile('select_python')} disabled={!pythonBridge.available || busy}>选择解释器</button>
      <button onClick={() => void invoke({ operation: 'start' })} disabled={!pythonBridge.available || busy || status?.state === 'ready'}>启动会话</button>
      <button onClick={() => void invoke({ operation: 'stop' })} disabled={!pythonBridge.available} title="清空解释器变量，保留已提交的工程修改"><RotateCcw size={14} />重置会话</button></div>
    <div className="python-grid"><div className="python-main">
      <div className="python-toolbar"><span><FileCode size={15} /> {filename.split(/[\\/]/).pop()}{source !== savedSource ? ' · 未保存' : ''}</span>
        <button onClick={() => void chooseFile('open_script')} disabled={!pythonBridge.available}><FolderOpen size={14} />打开</button>
        <button onClick={() => void chooseFile('save_script')} disabled={!pythonBridge.available}><Save size={14} />另存脚本</button>
        <button className="btn-primary" onClick={runScript} disabled={!pythonBridge.available || busy || !source.trim()} data-testid="python-run"><Play size={14} />运行脚本</button>
        <button onClick={() => void invoke({ operation: 'stop' })} disabled={!pythonBridge.available || (!busy && (!status || status.state === 'stopped'))} title="停止解释器及其定时回调" data-testid="python-stop"><Square size={14} />停止</button></div>
      <textarea ref={editor} className="python-source" aria-label="Python 脚本编辑器" spellCheck={false} value={source}
        onChange={event => { setSource(event.target.value); setSuggestions([]); }} onKeyDown={event => keyboard(event, 'script')} data-testid="python-source" />
      <div className="python-editor-footer"><span>共 {source.split('\n').length} 行 · Ctrl+Enter 运行 · Ctrl+空格补全 · Ctrl+Tab 缩进</span>
        {status?.errorLine && lastMode.current === 'script' && <button onClick={() => {
          const offset = source.split('\n').slice(0, status.errorLine! - 1).reduce((sum, line) => sum + line.length + 1, 0);
          editor.current?.focus(); editor.current?.setSelectionRange(offset, offset + (source.split('\n')[status.errorLine! - 1]?.length ?? 0));
        }}>定位第 {status.errorLine} 行异常</button>}</div>
      {suggestions.length > 0 && <div className="python-completions" aria-label="代码补全候选">{suggestions.map(text => <button key={text} onClick={() => acceptCompletion(text)}>{text}</button>)}</div>}
      <div className="python-console-title"><strong>交互控制台</strong>{truncated && <span>较早输出已截断</span>}<button onClick={() => { setOutput([]); setTruncated(false); retainedEntries.current = 0; }}>清空输出</button></div>
      <div ref={log} role="log" aria-live="polite" className="python-output" data-testid="python-output"><pre>{output.map(entry => <span key={entry.sequence} className={`python-output-${entry.channel}`}>{entry.channel === 'input' ? '>>> ' : ''}{entry.text}{entry.channel === 'input' && !entry.text.endsWith('\n') ? '\n' : ''}</span>)}</pre></div>
      <div className="python-console-input"><span aria-hidden="true">&gt;&gt;&gt;</span><textarea ref={consoleEditor} value={consoleInput} aria-label="Python 控制台输入" spellCheck={false}
        onChange={event => { setConsoleInput(event.target.value); setSuggestions([]); }} onKeyDown={event => keyboard(event, 'console')} placeholder="Enter 执行，Shift+Enter 换行，上下键查看历史" data-testid="python-console-input" />
        <button onClick={() => runConsole()} disabled={!pythonBridge.available || busy || !consoleInput.trim()}>执行输入</button></div>
    </div><aside className="python-sidebar"><h2>当前上下文</h2><p>{state.workbench?.workspace.name}</p><p>选中元素：{selectedElement?.name ?? '无'}</p>
      <p className="python-hint">在模组元素页选择对象后回来，或通过 cb.context.active_element 设置选择。</p>
      <h2>脚本示例</h2>{([['inspect', '读取选中元素'], ['operator', '注册自定义操作'], ['timer', '注册定时器'], ['events', '订阅工程变化']] as const).map(([key, label]) =>
        <button key={key} onClick={() => { if (source !== savedSource && !window.confirm('替换当前未保存的草稿？')) return; setSource(examples[key]); }}>{label}</button>)}
      <h2>已注册操作</h2>{status?.operators.length ? status.operators.map(operator => <button key={operator.idname} disabled={busy} onClick={() => runConsole(`cb.ops.${operator.idname}()`)}>{operator.label}</button>) : <p>运行注册脚本后，操作会显示在这里。</p>}
      <h2>接口帮助</h2><button disabled={!pythonBridge.available || busy} onClick={() => runConsole('print(cb.api_help())')}>显示接口概览</button>
      <p className="python-hint">cb.context：当前上下文<br />cb.data：工程对象<br />cb.ops：核心及自定义操作<br />cb.app：定时器与回调</p>
      <p className="python-hint">每次写入会立即提交；脚本报错不会撤销此前修改。历史恢复仍通过产品的审批流程。</p>
      <p className="python-version">{status?.pythonVersion}</p></aside></div>
  </section>;
}
