import { useSyncExternalStore } from 'react';
import { X } from 'lucide-react';
import { dismissNotice, feedbackSnapshot, resolveConfirmation, subscribeFeedback } from '../feedback/feedback';
import { renderUiMessage, uiText, useUiLocale } from '../i18n';
import { ConfirmDialog } from './Dialog';

export function FeedbackHost() {
  useUiLocale();
  const { confirmations, notices } = useSyncExternalStore(subscribeFeedback, feedbackSnapshot);
  const confirmation = confirmations[0];
  return <>
    {notices.length > 0 && <section className="feedback-notices" aria-label={uiText('操作通知', 'Notifications')}>
      {notices.map(notice => <div key={notice.id} className={`feedback-notice ${notice.tone}`} data-testid="feedback-notice">
        <p role={notice.tone === 'error' ? 'alert' : 'status'}>{renderUiMessage(notice.message)}</p>
        {notice.details?.length ? <details><summary>{uiText('详情', 'Details')}</summary>{notice.details.map((detail, index) => <p key={index}>{renderUiMessage(detail)}</p>)}</details> : null}
        {notice.onAction && <button type="button" className="btn-secondary" onClick={() => { notice.onAction?.(); dismissNotice(notice.id); }}>{renderUiMessage(notice.actionLabel ?? '')}</button>}
        <button type="button" aria-label={uiText('关闭通知', 'Dismiss notification')} onClick={() => dismissNotice(notice.id)}><X size={16} aria-hidden="true" /></button>
      </div>)}
    </section>}
    {confirmation && <ConfirmDialog key={confirmation.id} open title={renderUiMessage(confirmation.title)}
      confirmLabel={renderUiMessage(confirmation.confirmLabel)} danger={confirmation.danger} testId="confirm-dialog"
      onConfirm={() => resolveConfirmation(confirmation.id, true)} onCancel={() => resolveConfirmation(confirmation.id, false)}>
      <p>{renderUiMessage(confirmation.message)}</p>
    </ConfirmDialog>}
  </>;
}
