import { useState, type ReactNode } from 'react';
import Markdown from 'react-markdown';
import { Check, Copy } from 'lucide-react';
import { mcpRuntimeBridge } from '../bridge/mcpRuntimeBridge';
import { agentBridge } from '../bridge/agentBridge';
import { uiText } from '../i18n';

export function CopyTextButton({ text, code = false }: { text: string; code?: boolean }) {
  const [result, setResult] = useState<{ text: string; status: 'copied' | 'failed' } | null>(null);
  const status = result?.text === text ? result.status : 'idle';
  return <span className="agent-copy"><button type="button" className="btn-secondary" onClick={() => {
    void mcpRuntimeBridge.copyText(text).then(() => setResult({ text, status: 'copied' }), () => setResult({ text, status: 'failed' }));
  }} aria-label={code ? uiText('复制代码', 'Copy code') : uiText('复制消息', 'Copy message')}>
    {status === 'copied' ? <Check size={13} aria-hidden="true" /> : <Copy size={13} aria-hidden="true" />}
    {status === 'copied' ? uiText('已复制', 'Copied') : code ? uiText('复制代码', 'Copy code') : uiText('复制', 'Copy')}
  </button><span role="status" className={status === 'failed' ? 'agent-copy-error' : 'sr-only'}>{status === 'copied' ? uiText('复制成功', 'Copied successfully')
    : status === 'failed' ? uiText('复制失败，请选中文本复制。', 'Copy failed. Select and copy the text.') : ''}</span></span>;
}

function CodeBlock({ children }: { children?: ReactNode }) {
  return <div className="agent-code-block"><pre>{children}</pre></div>;
}

export function AgentMessageBody({ text }: { text: string }) {
  const [linkError, setLinkError] = useState(false);
  return <div className="agent-markdown">
    <Markdown skipHtml urlTransform={url => /^https?:\/\//i.test(url) ? url : ''} components={{
      a: ({ href, children }) => href ? <a href={href} target="_blank" rel="noreferrer noopener" onClick={event => {
        if (!agentBridge.available) return;
        event.preventDefault(); setLinkError(false);
        void agentBridge.invoke({ operation: 'openLink', url: href }).then(result => setLinkError(result.linkOpened !== true), () => setLinkError(true));
      }}>{children}</a> : <span>{children}</span>,
      img: ({ alt }) => <span>{alt}</span>,
      pre: CodeBlock,
      code: ({ children, className, node }) => {
        const value = String(children ?? '');
        const block = Boolean(className) || value.includes('\n') || (node?.position?.start.line !== node?.position?.end.line);
        return <><code className={className}>{children}</code>{block && <CopyTextButton text={value} code />}</>;
      }
    }}>{text}</Markdown>
    {linkError && <p role="alert">{uiText('链接无法打开，请复制地址重试。', 'Could not open the link. Copy the address and try again.')}</p>}
    <CopyTextButton text={text} />
  </div>;
}
