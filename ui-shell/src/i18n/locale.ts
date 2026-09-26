import { useSyncExternalStore } from 'react';

export type UiLocale = 'zh' | 'en';
const storageKey = 'copperbench.ui.locale';
const subscribers = new Set<() => void>();

function readLocale(): UiLocale {
  try { return window.localStorage.getItem(storageKey) === 'en' ? 'en' : 'zh'; }
  catch { return 'zh'; }
}

export let UI_LOCALE: UiLocale = readLocale();
const snapshot = () => UI_LOCALE;
const subscribe = (listener: () => void) => {
  subscribers.add(listener);
  return () => { subscribers.delete(listener); };
};

function updateDocumentLanguage() {
  if (typeof document !== 'undefined') document.documentElement.lang = UI_LOCALE === 'zh' ? 'zh-CN' : 'en';
}
updateDocumentLanguage();

/** A language change rerenders subscribers without remounting editors or losing drafts. */
export function setUiLocale(locale: UiLocale): void {
  if (locale !== 'zh' && locale !== 'en') return;
  try { window.localStorage.setItem(storageKey, locale); } catch { /* Session-only preference when storage is unavailable. */ }
  if (UI_LOCALE === locale) return;
  UI_LOCALE = locale;
  updateDocumentLanguage();
  subscribers.forEach(listener => listener());
}

export function useUiLocale(): UiLocale {
  return useSyncExternalStore(subscribe, snapshot, () => 'zh');
}

/** Static shell text; user content and wire identifiers are never passed through this helper. */
export function uiText(chinese: string, english: string): string {
  return UI_LOCALE === 'en' ? english : chinese;
}

export function englishCount(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}
