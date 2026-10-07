import { windowBridge } from '../bridge/windowBridge';

let draftCount = -1;
const counts = new Map<string, number>();
let pendingReport = Promise.resolve();

function beforeUnload(event: BeforeUnloadEvent) {
  event.preventDefault();
  event.returnValue = '';
}

/** Includes inactive workspace sessions; closing the shell discards all of them. */
export function hasUnsavedDrafts(): boolean { return draftCount > 0; }

export function setUnsavedDraftCount(owner: string, count: number): void {
  counts.set(owner, count);
  const value = [...counts.values()].reduce((sum, current) => sum + current, 0);
  if (draftCount === value) return;
  const hadDrafts = draftCount > 0;
  draftCount = value;
  if (typeof window === 'undefined') return;
  if (value > 0 && !hadDrafts) window.addEventListener('beforeunload', beforeUnload);
  else if (value === 0 && hadDrafts) window.removeEventListener('beforeunload', beforeUnload);
  if (windowBridge.canGuardUnsavedChanges) {
    // Keep reports in order so a delayed clean acknowledgement cannot erase a newer dirty count.
    pendingReport = pendingReport.then(() => windowBridge.reportUnsavedChanges(value))
      .catch(error => { console.warn('[Copperbench] Could not report unsaved drafts:', error); });
  }
}
