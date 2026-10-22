import { useState } from 'react';
import { Download, Trash2 } from 'lucide-react';
import { agentText, type AgentRequest, type AgentSnapshot } from '../bridge/agentBridge';
import { confirmAction } from '../feedback/feedback';
import { formatTemplate, renderUiMessage, uiMessage, uiText, type UiMessage } from '../i18n';
import { Dialog } from './Dialog';
import { AgentProviderIcon } from './AgentProviderIcon';

export function AgentHistoryDialog({ snapshot, busy, pending, invoke, onClose, draftConversationIds = [], onDeleted }: {
  snapshot: AgentSnapshot | null; busy: boolean; pending: boolean; onClose: () => void;
  draftConversationIds?: string[]; onDeleted?: (ids: string[]) => void;
  invoke: (request: AgentRequest) => Promise<AgentSnapshot | null>;
}) {
  const [feedback, setFeedback] = useState<UiMessage | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const capacity = snapshot?.history;
  const act = async (request: AgentRequest) => {
    setFeedback(null);
    const result = await invoke(request);
    const failure = result?.errorText ? uiMessage(formatTemplate(result.errorText.zh, result.errorText.args), formatTemplate(result.errorText.en, result.errorText.args)) : result?.error;
    if (result?.historyResult?.operation === request.operation) {
      if (request.operation === 'deleteConversation') onDeleted?.([request.conversationId]);
      else if (request.operation === 'deleteConversations') onDeleted?.(request.conversationIds);
      setFeedback(result.historyResult.path ? uiMessage(`已保存到：${result.historyResult.path}`, `Saved to: ${result.historyResult.path}`)
        : request.operation === 'retryHistory' ? uiMessage('历史记录已保存，可以继续对话。', 'History saved. You can continue the conversation.')
          : failure || uiMessage('会话记录已删除。', 'Conversation history deleted.'));
    }
    else if (failure) setFeedback(failure);
    else setFeedback(uiMessage('操作未完成，请重试。原始记录保留。', 'The operation did not complete. Try again. The original history is retained.'));
  };
  const deletionMessage = (ids: string[]) => ids.some(id => draftConversationIds.includes(id))
    ? uiMessage('删除所选 Copperbench 对话记录及其中未发送的文字和附件。工作区作品和 CLI 原生历史保持不变。', 'Deletes the selected Copperbench conversations and their unsent text and attachments. Your creation and native CLI history remain unchanged.')
    : uiMessage('只删除选中的 Copperbench 对话记录。工作区作品和 CLI 原生历史保持不变。', 'Deletes only the selected Copperbench conversations. Your creation and native CLI history remain unchanged.');
  return <Dialog open title={uiText('整理对话记录', 'Manage conversation history')} testId="agent-history-dialog" onClose={onClose}
    footer={<button type="button" className="btn-secondary" onClick={onClose}>{uiText('完成', 'Done')}</button>}>
    <p>{uiText('导出包含对话原文、助手名称和时间。分享前请检查其中的个人内容。', 'Exports include conversation text, assistant and timestamps. Review personal content before sharing.')}</p>
    {capacity && <p className="agent-history-capacity" role="status">{capacity.count} / {capacity.maxCount} {uiText('个会话', 'conversations')} · {(capacity.bytes / 1048576).toFixed(1)} / {(capacity.maxBytes / 1048576).toFixed(0)} {uiText('MiB', 'MiB')}
      {capacity.level !== 'ok' && <strong>{capacity.level === 'unreadable' ? uiText('历史无法加载，原文件已保留。', 'History could not be loaded. The original file is retained.')
        : uiText('请导出并删除不再需要的会话以释放空间。', 'Export and delete conversations you no longer need to free space.')}</strong>}</p>}
    {capacity?.level === 'unreadable' ? <button type="button" className="btn-danger" disabled={busy || pending} onClick={async () => {
      if (await confirmAction({ title: uiMessage('备份并重新开始对话', 'Back up and restart history'),
        message: uiMessage('原历史将完整备份到当前工作区，然后创建空的 Copperbench 对话记录。备份可能含原始敏感信息；工作区作品和 CLI 原生记录不会改变。', 'The original history will be backed up in this workspace, then Copperbench will start an empty history. The backup may contain sensitive original content. Your creation and native CLI history remain unchanged.'),
        confirmLabel: uiMessage('备份并重新开始', 'Back up and restart'), danger: true })) await act({ operation: 'recoverHistory', confirmed: true });
    }}>{uiText('备份并重新开始', 'Back up and restart')}</button> : <>
      {snapshot?.storageBlocked && <button type="button" className="btn-secondary" disabled={busy || pending} onClick={() => void act({ operation: 'retryHistory' })}>{uiText('重试保存历史', 'Retry saving history')}</button>}
      <button type="button" className="btn-danger" disabled={pending || !selected.length} onClick={async () => {
        if (await confirmAction({ title: uiMessage('删除选中的对话？', 'Delete selected conversations?'),
          message: deletionMessage(selected),
          confirmLabel: uiMessage('删除所选', 'Delete selected'), danger: true })) {
          await act({ operation: 'deleteConversations', conversationIds: selected, confirmed: true }); setSelected([]);
        }
      }}>{uiText('删除所选', 'Delete selected')} ({selected.length})</button>
      <ul className="agent-history-list">{snapshot?.sessions.map(session => <li key={session.id}>
        <input type="checkbox" aria-label={`${uiText('选择', 'Select')} ${agentText(session.title, session.titleText)}`} checked={selected.includes(session.id)} disabled={pending || busy && snapshot.conversationId === session.id}
          onChange={event => setSelected(current => event.target.checked ? [...current, session.id] : current.filter(id => id !== session.id))} />
        <AgentProviderIcon provider={session.provider} /><div><strong>{agentText(session.title, session.titleText)}</strong><small>{new Date(session.updatedAt).toLocaleString()}</small></div>
        <button type="button" className="btn-secondary" disabled={pending} aria-label={`${uiText('导出', 'Export')} ${agentText(session.title, session.titleText)}`} onClick={() => void act({ operation: 'exportConversation', conversationId: session.id })}><Download size={15} aria-hidden="true" /></button>
        <button type="button" className="btn-danger" disabled={pending || busy && snapshot.conversationId === session.id} aria-label={`${uiText('删除', 'Delete')} ${agentText(session.title, session.titleText)}`} onClick={async () => {
          if (await confirmAction({ title: uiMessage('删除此对话？', 'Delete this conversation?'),
            message: deletionMessage([session.id]),
            confirmLabel: uiMessage('删除对话', 'Delete conversation'), danger: true })) await act({ operation: 'deleteConversation', conversationId: session.id, confirmed: true });
        }}><Trash2 size={15} aria-hidden="true" /></button>
      </li>)}</ul>
      {!snapshot?.sessions.length && <p>{uiText('暂无对话记录。', 'No conversation history yet.')}</p>}
    </>}
    {feedback && <p role="status" className="agent-history-feedback">{renderUiMessage(feedback)}</p>}
  </Dialog>;
}
