/** One keyboard/focus owner for all product dialogs, including blocking dialogs. */
type DialogEntry = {
  element: HTMLElement;
  close: () => (() => void) | null;
  initialFocus: () => HTMLElement | null;
  returnFocus: () => HTMLElement | null;
  layer: HTMLElement;
  originalZIndex: string;
};

const stack: DialogEntry[] = [];
const isolated = new Map<HTMLElement, boolean>();
let observer: MutationObserver | null = null;
const selector = 'button,input,select,textarea,a[href],area[href],summary,[tabindex],[contenteditable="true"],audio[controls],video[controls],iframe';
const visible = (element: HTMLElement) => element.isConnected && !element.closest('[inert]')
  && element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden';
const tabbable = (dialog: HTMLElement) => Array.from(dialog.querySelectorAll<HTMLElement>(selector))
  .filter(element => element.tabIndex >= 0 && !element.matches(':disabled') && visible(element))
  .sort((a, b) => (a.tabIndex > 0 ? a.tabIndex : Infinity) - (b.tabIndex > 0 ? b.tabIndex : Infinity));
const top = () => stack.at(-1);

function focusInside(entry: DialogEntry) {
  const preferred = entry.initialFocus();
  const target = preferred && entry.element.contains(preferred) && visible(preferred)
    ? preferred : tabbable(entry.element)[0] ?? entry.element;
  target.focus({ preventScroll: true });
}

function isolateBackground() {
  for (const [element, wasInert] of isolated) element.inert = wasInert;
  isolated.clear();
  const active = top();
  if (!active) return;
  // Isolate sibling branches, never an ancestor of the active dialog.
  let branch: HTMLElement = active.element;
  while (branch.parentElement) {
    for (const sibling of branch.parentElement.children) {
      if (sibling === branch || !(sibling instanceof HTMLElement)) continue;
      isolated.set(sibling, sibling.inert);
      sibling.inert = true;
    }
    branch = branch.parentElement;
    if (branch === document.body) break;
  }
  stack.forEach((entry, index) => {
    entry.element.dataset.dialogActive = String(entry === active);
    entry.layer.style.zIndex = String(1000 + index * 2);
  });
}

function onKeyDown(event: KeyboardEvent) {
  const active = top();
  if (!active) return;
  if (event.key === 'Escape') {
    event.preventDefault(); event.stopPropagation();
    active.close()?.();
    return;
  }
  if (event.key !== 'Tab') return;
  const items = tabbable(active.element);
  const index = items.indexOf(document.activeElement as HTMLElement);
  if (!items.length || index === -1 || (event.shiftKey ? index === 0 : index === items.length - 1)) {
    event.preventDefault();
    (event.shiftKey ? items.at(-1) : items[0])?.focus();
    if (!items.length) active.element.focus();
  }
}

function onFocusIn(event: FocusEvent) {
  const active = top();
  if (active && event.target instanceof Node && !active.element.contains(event.target)) focusInside(active);
}

function restoreFocus(entry: DialogEntry) {
  const invoker = entry.returnFocus();
  const active = top();
  if (invoker && visible(invoker) && !invoker.matches(':disabled') && (!active || active.element.contains(invoker))) {
    invoker.focus({ preventScroll: true });
  } else if (active) focusInside(active);
  else {
    const fallback = document.querySelector<HTMLElement>('[data-dialog-fallback]')
      ?? document.querySelector<HTMLElement>('main') ?? document.body;
    if (!fallback.hasAttribute('tabindex')) fallback.setAttribute('tabindex', '-1');
    fallback.focus({ preventScroll: true });
  }
}

export function registerDialog(element: HTMLElement, options: {
  close: DialogEntry['close']; initialFocus: DialogEntry['initialFocus']; returnFocus: DialogEntry['returnFocus'];
}) {
  const layer = element.closest<HTMLElement>('.modal-overlay, [data-dialog-layer], [role="presentation"]') ?? element;
  const entry: DialogEntry = { element, ...options, layer, originalZIndex: layer.style.zIndex };
  if (!element.hasAttribute('tabindex')) element.tabIndex = -1;
  // React commits child effects first. Keep a nested child above its parent.
  const child = stack.findIndex(existing => element.contains(existing.element));
  if (child < 0) stack.push(entry); else stack.splice(child, 0, entry);
  if (stack.length === 1) {
    document.addEventListener('keydown', onKeyDown, true);
    document.addEventListener('focusin', onFocusIn, true);
    observer = new MutationObserver(isolateBackground);
    observer.observe(document.body, { childList: true, subtree: true });
  }
  isolateBackground();
  // React autoFocus has already run; preserve it unless explicitly overridden.
  if (top() === entry && (options.initialFocus() || !element.contains(document.activeElement))) focusInside(entry);
  return () => {
    const wasTop = top() === entry;
    const index = stack.indexOf(entry);
    if (index < 0) return;
    stack.splice(index, 1);
    delete element.dataset.dialogActive;
    layer.style.zIndex = entry.originalZIndex;
    isolateBackground();
    if (!stack.length) {
      document.removeEventListener('keydown', onKeyDown, true);
      document.removeEventListener('focusin', onFocusIn, true);
      observer?.disconnect(); observer = null;
    }
    if (wasTop) {
      restoreFocus(entry);
      requestAnimationFrame(() => { if (document.activeElement === document.body) restoreFocus(entry); });
    }
  };
}
