import { useLayoutEffect, useRef } from 'react';
import { registerDialog } from './dialogStack';

/** Attach to a role=dialog/alertdialog card; null close makes the dialog blocking. */
export function useDialogA11y(open: boolean, onRequestClose: (() => void) | null, options: {
  initialFocus?: () => HTMLElement | null;
  returnFocus?: () => HTMLElement | null;
} = {}) {
  const ref = useRef<HTMLDivElement | null>(null);
  const callbacks = useRef({ onRequestClose, ...options });
  callbacks.current = { onRequestClose, ...options };
  const invoker = useRef<HTMLElement | null>(null);
  const wasOpen = useRef(false);
  if (open && !wasOpen.current) invoker.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  wasOpen.current = open;
  useLayoutEffect(() => {
    if (!open || !ref.current) return;
    const previous = invoker.current;
    return registerDialog(ref.current, {
      close: () => callbacks.current.onRequestClose,
      initialFocus: () => callbacks.current.initialFocus?.() ?? null,
      returnFocus: () => callbacks.current.returnFocus?.() ?? previous
    });
  }, [open]);
  return ref;
}
