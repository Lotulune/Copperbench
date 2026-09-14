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

export const UI_LOCALE: UiLocale = browserLocale();

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

/** Reload only after the user has explicitly accepted the draft-loss warning. */
export async function changeLocale(locale: UiLocale): Promise<boolean> {
  if (locale !== 'zh' && locale !== 'en') return false;
  if (locale === UI_LOCALE) return false;
  const warning = UI_LOCALE === 'zh'
    ? '切换语言需要重新加载界面，未保存的编辑内容会丢失。请先保存。现在重新加载？'
    : 'Changing language reloads the interface. Unsaved edits will be lost. Save your work first. Reload now?';
  if (!window.confirm(warning)) return false;
  try {
    if (window.__COPPERBENCH_SET_LOCALE__) await window.__COPPERBENCH_SET_LOCALE__(locale);
    else window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    window.alert(UI_LOCALE === 'zh' ? '无法保存语言设置，界面未重新加载。' : 'Could not save the language preference. The interface has not reloaded.');
    return false;
  }
  window.location.reload();
  return true;
}
