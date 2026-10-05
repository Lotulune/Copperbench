import { windowBridge } from '../bridge/windowBridge';

let draftCount = -1;
let pendingReport = Promise.resolve();

function beforeUnload(event: BeforeUnloadEvent) {
  event.preventDefault();
  event.returnValue = '';
}

/** Includes inactive workspace sessions; closing the shell discards all of them. */
export function hasUnsavedSourceDrafts(): boolean { return draftCount > 0; }

export function setUnsavedSourceDraftCount(value: number): void {
  if (draftCount === value) return;
  const hadDrafts = draftCount > 0;
  draftCount = value;
  if (typeof window === 'undefined') return;
  if (value > 0 && !hadDrafts) window.addEventListener('beforeunload', beforeUnload);
  else if (value === 0 && hadDrafts) window.removeEventListener('beforeunload', beforeUnload);
  if (windowBridge.canGuardUnsavedChanges) {
    // Keep reports in order so a delayed clean acknowledgement cannot erase a newer dirty count.
    pendingReport = pendingReport.then(() => windowBridge.reportUnsavedChanges(value))
      .catch(error => { console.warn('[Copperbench Source] Could not report unsaved drafts:', error); });
  }
}
