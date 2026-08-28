import { useEffect, useRef } from 'react';

const FOCUSABLE_SELECTOR =
  'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])';

/**
 * Dialog accessibility behaviour (NFR-UI-08):
 * - moves focus into the dialog when it opens and restores the previously
 *   focused element when it closes;
 * - traps Tab navigation inside the dialog;
 * - closes the dialog on Escape when `onRequestClose` is provided (blocking
 *   dialogs such as bridge recovery pass null and require an explicit action).
 *
 * Attach the returned ref to the dialog card and mark it
 * role="dialog" aria-modal="true".
 */
export function useDialogA11y(open: boolean, onRequestClose: (() => void) | null) {
  const ref = useRef<HTMLDivElement | null>(null);
  const closeRef = useRef<(() => void) | null>(null);
  const previouslyFocusedRef = useRef<HTMLElement | null>(null);

  closeRef.current = onRequestClose;

  // Capture the invoker during render, before React commits descendant
  // autoFocus. Waiting until the effect runs can otherwise remember an input
  // inside the dialog and leave focus on <body> after that input unmounts.
  if (open && previouslyFocusedRef.current === null) {
    previouslyFocusedRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
  } else if (!open) {
    previouslyFocusedRef.current = null;
  }

  useEffect(() => {
    if (!open) return;
    const dialog = ref.current;
    if (!dialog) return;

    const previouslyFocused = previouslyFocusedRef.current;

    if (!dialog.hasAttribute('tabindex')) {
      dialog.setAttribute('tabindex', '-1');
    }
    dialog.focus();

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && closeRef.current) {
        e.preventDefault();
        e.stopPropagation();
        closeRef.current();
        return;
      }
      if (e.key !== 'Tab') return;

      const items = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
        (el) => el.offsetParent !== null
      );
      if (items.length === 0) {
        e.preventDefault();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown, true);
    return () => {
      document.removeEventListener('keydown', onKeyDown, true);
      previouslyFocused?.focus?.();
    };
  }, [open]);

  return ref;
}
