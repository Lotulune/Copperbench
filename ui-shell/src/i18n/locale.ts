import { useSyncExternalStore } from 'react';
import { enUi } from './enUi';

export type UiLocale = 'zh' | 'en';
export const LOCALE_STORAGE_KEY = 'copperbench.ui.locale';

declare global {
  interface Window {
    __COPPERBENCH_UI_LOCALE__?: UiLocale;
    __COPPERBENCH_SET_LOCALE__?: (locale: UiLocale) => Promise<unknown>;
  }
}

/** Keep the existing Chinese default; an explicit local preference takes priority. */
export function readLocale(storage?: Pick<Storage, 'getItem'>): UiLocale {
  try {
    return storage?.getItem(LOCALE_STORAGE_KEY) === 'en' ? 'en' : 'zh';
  } catch {
    return 'zh';
  }
}

function browserLocale(): UiLocale {
  try {
    const native = window.__COPPERBENCH_UI_LOCALE__;
    return native === 'en' || native === 'zh' ? native : readLocale(window.localStorage);
  } catch { return 'zh'; }
}

export let UI_LOCALE: UiLocale = browserLocale();
const subscribers = new Set<() => void>();
const snapshot = () => UI_LOCALE;
const subscribe = (listener: () => void) => {
  subscribers.add(listener);
  return () => { subscribers.delete(listener); };
};
function updateDocumentLanguage() {
  if (typeof document !== 'undefined') document.documentElement.lang = UI_LOCALE === 'zh' ? 'zh-CN' : 'en';
}
updateDocumentLanguage();
export function useUiLocale(): UiLocale {
  return useSyncExternalStore(subscribe, snapshot, () => 'zh');
}
export function uiText(chinese: string, english: string): string {
  return UI_LOCALE === 'en' ? english : chinese;
}
export function englishCount(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}

/** Only authored interface literals go through this catalog, never user data. */
export function tr(source: string, args: readonly unknown[] = []): string {
  const template = UI_LOCALE === 'en' ? enUi[source] ?? source : source;
  const decoded = Object.hasOwn(enUi, source) ? template.replace(/&(?:lt|gt|amp|quot|rarr);|&#(\d+);/g, (entity, code: string | undefined) =>
    code ? String.fromCodePoint(Number(code)) : ({'&lt;':'<','&gt;':'>','&amp;':'&','&quot;':'"','&rarr;':'→'}[entity] ?? entity)
  ) : template;
  return decoded.replace(/\{(\d+)\}/g, (placeholder, index: string) =>
    Number(index) < args.length ? String(args[Number(index)]) : placeholder
  );
}

/** Persist the native preference before notifying editors, without remounting or losing drafts. */
export async function setUiLocale(locale: UiLocale): Promise<boolean> {
  if (locale !== 'zh' && locale !== 'en') return false;
  if (locale === UI_LOCALE) return false;
  try {
    if (window.__COPPERBENCH_SET_LOCALE__) await window.__COPPERBENCH_SET_LOCALE__(locale);
    else {
      try { window.localStorage.setItem(LOCALE_STORAGE_KEY, locale); }
      catch { /* Browser-only fallback: keep the preference for this session. */ }
    }
  } catch {
    window.alert(uiText('无法保存语言设置，当前语言与草稿已保留。', 'Could not save the language preference. Your current language and drafts are unchanged.'));
    return false;
  }
  UI_LOCALE = locale;
  if (window.__COPPERBENCH_UI_LOCALE__ !== undefined) window.__COPPERBENCH_UI_LOCALE__ = locale;
  updateDocumentLanguage();
  subscribers.forEach(listener => listener());
  return true;
}

export const changeLocale = setUiLocale;
