import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { uiText } from '../i18n';

export function Dialog({ open, title, children, footer, onClose, testId, initialFocus }: {
  open: boolean; title: string; children: ReactNode; footer: ReactNode;
  onClose: (() => void) | null; testId?: string; initialFocus?: () => HTMLElement | null;
}) {
  const titleId = useId();
  const ref = useDialogA11y(open, onClose);
  const initialFocusRef = useRef(initialFocus);
  initialFocusRef.current = initialFocus;
  useEffect(() => {
    const element = open ? initialFocusRef.current?.() : null;
    if (element && ref.current?.contains(element)) element.focus();
  }, [open]);
  if (!open) return null;
  return createPortal(<div className="modal-overlay">
    <div ref={ref} role="dialog" aria-modal="true" aria-labelledby={titleId} className="modal-card" data-testid={testId}>
      <div className="modal-header"><h2 id={titleId}>{title}</h2>
        {onClose && <button type="button" aria-label={uiText('关闭', 'Close')} onClick={onClose}><X size={16} aria-hidden="true" /></button>}
      </div>
      <div className="modal-body">{children}</div>
      <div className="modal-footer">{footer}</div>
    </div>
  </div>, document.body);
}

export function ConfirmDialog({ open, title, children, confirmLabel, onConfirm, onCancel, danger = false, testId, confirmTestId }: {
  open: boolean; title: string; children: ReactNode; confirmLabel: string;
  onConfirm: () => void; onCancel: () => void; danger?: boolean; testId?: string; confirmTestId?: string;
}) {
  const cancel = useRef<HTMLButtonElement>(null);
  return <Dialog open={open} title={title} onClose={onCancel} testId={testId} initialFocus={() => cancel.current}
    footer={<>
      <button ref={cancel} type="button" className="btn-secondary" onClick={onCancel}>{uiText('取消', 'Cancel')}</button>
      <button type="button" className={danger ? 'btn-danger' : 'btn-primary'} onClick={onConfirm} data-testid={confirmTestId}>{confirmLabel}</button>
    </>}>{children}</Dialog>;
}
