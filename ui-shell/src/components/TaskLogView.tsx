import { useLayoutEffect, useRef, useState } from 'react';
import { ArrowDown, Copy, Pause } from 'lucide-react';
import { uiMessage, uiText } from '../i18n';
import { formatDateTime, formatTime, localTimeZone } from '../i18n/dateTime';
import { showNotice } from '../feedback/feedback';
import type { TaskLogEntry } from '../types/contract';

export interface LogReadingState {
  following: boolean; scrollTop: number; seenSequence: number; query: string; level: string;
}
export const newLogReadingState = (): LogReadingState => ({ following: true, scrollTop: 0, seenSequence: 0, query: '', level: 'all' });

/** The owner keeps one reading state per workspace/task, including while closed. */
export function TaskLogView({ logs, reading }: { logs: TaskLogEntry[]; reading: LogReadingState }) {
  const container = useRef<HTMLDivElement>(null);
  const [, update] = useState(0);
  const [copied, setCopied] = useState(false);
  const change = (values: Partial<LogReadingState>) => { Object.assign(reading, values); update(value => value + 1); };
  const latest = logs.at(-1)?.sequence ?? 0;
  const timeZone = localTimeZone();
  const filtered = logs.filter(entry => (reading.level === 'all' || entry.level === reading.level)
    && entry.text.toLocaleLowerCase().includes(reading.query.toLocaleLowerCase()));
  const unseen = reading.following ? 0 : logs.filter(entry => entry.sequence > reading.seenSequence).length;
  useLayoutEffect(() => {
    const element = container.current;
    if (!element) return;
    element.scrollTop = reading.following ? element.scrollHeight : reading.scrollTop;
    reading.scrollTop = element.scrollTop;
    if (reading.following) reading.seenSequence = latest;
  }, [logs, latest, reading, reading.following, reading.query, reading.level]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(filtered.map(entry => `${entry.timestamp} [${entry.level.toUpperCase()}] ${entry.text}`).join('\n'));
      setCopied(true);
    } catch {
      showNotice({ tone: 'error', message: uiMessage('无法复制日志。可以在日志区选中文字后复制。', 'Could not copy the logs. Select text in the log area and copy it.') });
    }
  };
  return <div className="task-log-view">
    <div className="task-log-toolbar">
      <label><span className="sr-only">{uiText('查找日志', 'Find in logs')}</span><input type="search" value={reading.query}
        placeholder={uiText('查找日志', 'Find in logs')} data-testid="task-log-search"
        onChange={event => { setCopied(false); change({ query: event.target.value }); }} /></label>
      <select aria-label={uiText('日志级别', 'Log level')} value={reading.level} onChange={event => { setCopied(false); change({ level: event.target.value }); }}>
        <option value="all">{uiText('全部级别', 'All levels')}</option><option value="info">{uiText('信息', 'Info')}</option>
        <option value="warning">{uiText('警告', 'Warning')}</option><option value="error">{uiText('错误', 'Error')}</option>
      </select>
      <span className="task-log-timezone">{timeZone}</span>
      <button type="button" className="btn-secondary" data-testid="task-log-copy" disabled={!filtered.length} onClick={() => void copy()}><Copy size={13} aria-hidden="true" />{copied ? uiText('已复制', 'Copied') : uiText('复制结果', 'Copy results')}</button>
      {reading.following ? <button type="button" className="btn-secondary" data-testid="task-log-pause" onClick={() => change({ following: false, seenSequence: latest })}><Pause size={13} aria-hidden="true" />{uiText('暂停跟随', 'Pause following')}</button>
        : <button type="button" className="btn-secondary" data-testid="task-log-latest" onClick={() => change({ following: true, seenSequence: latest })}><ArrowDown size={13} aria-hidden="true" />{uiText('回到最新', 'Jump to latest')}{unseen > 0 && <span> (+{unseen})</span>}</button>}
      <span className="sr-only" role="status">{unseen > 0 ? uiText(`${unseen} 行新日志`, `${unseen} new log lines`) : ''}</span>
    </div>
    <div ref={container} data-testid="task-log-stream" className="task-log-stream" role="log" tabIndex={0}
      aria-live={reading.following ? 'polite' : 'off'} aria-label={uiText('任务日志', 'Task logs')}
      onScroll={event => {
        const element = event.currentTarget;
        const following = element.scrollHeight - element.clientHeight - element.scrollTop <= 24;
        const changed = following !== reading.following;
        reading.scrollTop = element.scrollTop;
        reading.following = following;
        if (following) reading.seenSequence = latest;
        if (changed) update(value => value + 1);
      }}>
      {!filtered.length ? <p className="task-log-empty">{logs.length ? uiText('没有匹配的日志。', 'No matching log lines.') : uiText('暂无任务输出日志。', 'No task output yet.')}</p>
        : filtered.map(entry => <div key={entry.sequence} className={`task-log-line level-${entry.level}`}>
          <time dateTime={entry.timestamp} title={`${formatDateTime(entry.timestamp, undefined, timeZone)} · ${entry.timestamp}`}>[{formatTime(entry.timestamp, undefined, timeZone)}]</time>
          <span className="task-log-level">[{entry.level.toUpperCase()}]</span><span>{entry.text}</span>
        </div>)}
    </div>
  </div>;
}
