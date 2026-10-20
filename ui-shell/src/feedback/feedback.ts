import type { UiMessage } from '../i18n';

export interface Confirmation {
  title: UiMessage; message: UiMessage; confirmLabel: UiMessage; danger?: boolean;
}
export interface Notice {
  id: number; message: UiMessage; details?: UiMessage[]; tone: 'info' | 'error'; actionLabel?: UiMessage; onAction?: () => void;
}
type PendingConfirmation = Confirmation & { id: number; resolve: (confirmed: boolean) => void };
let nextId = 0;
let snapshot: { confirmations: PendingConfirmation[]; notices: Notice[] } = { confirmations: [], notices: [] };
const listeners = new Set<() => void>();
const emit = () => listeners.forEach(listener => listener());
export const feedbackSnapshot = () => snapshot;
export const subscribeFeedback = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; };

export function confirmAction(confirmation: Confirmation): Promise<boolean> {
  return new Promise(resolve => {
    snapshot = { ...snapshot, confirmations: [...snapshot.confirmations, { ...confirmation, id: ++nextId, resolve }] };
    emit();
  });
}
export function resolveConfirmation(id: number, confirmed: boolean) {
  const pending = snapshot.confirmations.find(item => item.id === id);
  if (!pending) return;
  snapshot = { ...snapshot, confirmations: snapshot.confirmations.filter(item => item.id !== id) };
  emit(); pending.resolve(confirmed);
}
export function showNotice(notice: Omit<Notice, 'id' | 'tone'> & { tone?: Notice['tone'] }) {
  snapshot = { ...snapshot, notices: [...snapshot.notices, { tone: 'info', ...notice, id: ++nextId }] };
  emit();
}
export function dismissNotice(id: number) {
  snapshot = { ...snapshot, notices: snapshot.notices.filter(item => item.id !== id) }; emit();
}
