import { useEffect, useRef, useState } from 'react';
import { FileText, ImageIcon, LoaderCircle, X } from 'lucide-react';
import { attachmentRequest, type AgentAttachment } from '../bridge/agentBridge';
import type { DraftAttachment } from '../hooks/useAgentAttachments';
import { renderUiMessage, uiText } from '../i18n';
import './agentAttachments.css';

function Thumbnail({ item }: { item: AgentAttachment }) {
  const [source, setSource] = useState<string>();
  const [failed, setFailed] = useState(false);
  const target = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    let cancelled = false;
    const observer = new IntersectionObserver(entries => {
      if (!entries.some(entry => entry.isIntersecting)) return;
      observer.disconnect();
      void attachmentRequest({ operation: 'attachmentPreview', id: item.id }).then(result => {
        if (!cancelled) { setSource(result.dataUrl); setFailed(!result.dataUrl); }
      }).catch(() => { if (!cancelled) setFailed(true); });
    });
    if (target.current) observer.observe(target.current);
    return () => { cancelled = true; observer.disconnect(); };
  }, [item.id]);
  return <span ref={target} className="creation-attachment-thumbnail" title={failed ? uiText('预览不可用，附件名称仍保留。', 'Preview unavailable; the attachment name is retained.') : item.name}>
    {source ? <img src={source} alt={item.name} /> : <ImageIcon size={22} aria-hidden="true" />}
  </span>;
}

export function AgentAttachments({ files, onRemove }: { files: (DraftAttachment | AgentAttachment)[]; onRemove?: (key: string) => void }) {
  if (!files.length) return null;
  return <ul className="creation-attachments" aria-label={uiText('消息附件', 'Message attachments')}>
    {files.map(file => {
      const draft = 'state' in file ? file : undefined, item = draft ? draft.item : file as AgentAttachment;
      const key = draft?.key ?? item!.id;
      return <li key={key} className={`creation-attachment${draft?.state === 'failed' ? ' has-error' : ''}`} data-testid="agent-attachment" data-state={draft?.state ?? 'sent'}>
        {draft?.state === 'uploading' ? <LoaderCircle size={20} className="creation-spinner" aria-hidden="true" />
          : item?.mediaType.startsWith('image/') ? <Thumbnail item={item} /> : <FileText size={21} aria-hidden="true" />}
        <span className="creation-attachment-label"><strong title={file.name}>{file.name}</strong><small>{draft?.state === 'uploading'
          ? uiText('正在添加…', 'Adding…') : draft?.error ? renderUiMessage(draft.error) : `${Math.max(1, Math.ceil(file.size / 1024))} KiB`}</small></span>
        {onRemove && <button type="button" onClick={() => onRemove(key)} aria-label={uiText(`移除 ${file.name}`, `Remove ${file.name}`)}><X size={14} aria-hidden="true" /></button>}
      </li>;
    })}
  </ul>;
}
