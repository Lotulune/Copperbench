import { Diagnostic, LocalizedText } from '../types/contract';
import { zh } from './zh';
import { en } from './en';
import { valueLabel, fieldLabel } from './labels';
import { UI_LOCALE, uiText, tr } from './locale';
export { UI_LOCALE, setUiLocale, useUiLocale, uiText, englishCount } from './locale';

/** Store messages without choosing a language until they are rendered. */
export type UiMessage = string | LocalizedText | { zh: string; en: string }
  | { diagnostics: ReadonlyArray<Pick<Diagnostic, 'code' | 'message'>>; separator: string };
export const uiMessage = (zh: string, en: string): UiMessage => ({ zh, en });
/** Keep Core failure arguments in editor state until the current locale renders them. */
export function diagnosticMessages(diagnostics: ReadonlyArray<Pick<Diagnostic, 'code' | 'message'>>,
  fallback: UiMessage, separator = '；'): UiMessage {
  return diagnostics.length ? { diagnostics, separator } : fallback;
}
export function renderUiMessage(message: UiMessage): string {
  if (typeof message === 'object' && 'diagnostics' in message) {
    return message.diagnostics.map(diagnostic => t(diagnostic.message, diagnostic.code)).join(message.separator);
  }
  return typeof message === 'string' ? message : 'key' in message ? t(message) : uiText(message.zh, message.en);
}

/**
 * 中文为默认界面语言；用户可切换到英文，保留当前编辑草稿。
 * 合同数据（诊断、字段标签、阶段文案）经 LocalizedText.key 查询词典渲染；
 * 缺失词条时回退 fallback（当前 fixtures 为英文）。用户数据、代码和日志保留原文；已知类型、枚举和权限档位由显示层翻译。
 */
export function formatTemplate(template: string, args?: Record<string, unknown>): string {
  if (!args || typeof args !== 'object' || Array.isArray(args)) return template;
  return template.replace(/\{([A-Za-z_][A-Za-z0-9_]*)\}/g, (match, name: string) => {
    if (!Object.hasOwn(args, name)) return match;
    const value = args[name];
    if (typeof value === 'string') return value;
    if (value === null || typeof value === 'boolean' || (typeof value === 'number' && Number.isFinite(value))) {
      return JSON.stringify(value);
    }
    return match;
  });
}

/** Render a contract LocalizedText in the UI locale, falling back to `fallback`. */
export function t(localized: LocalizedText | string | null | undefined, fallbackCode = ''): string {
  if (typeof localized === 'string') return localized || fallbackCode;
  if (!localized || typeof localized !== 'object' || Array.isArray(localized)) return fallbackCode;
  const key = typeof localized.key === 'string' ? localized.key : '';
  const fallback = typeof localized.fallback === 'string' && localized.fallback ? localized.fallback : fallbackCode;
  if (localized.key === 'field.option' && typeof localized.args?.label === 'string') {
    return valueLabel(localized.args.label);
  }
  if (UI_LOCALE === 'zh') {
    const entry = zh[key];
    if (typeof entry === 'string' && entry) return formatTemplate(entry, localized.args);
  } else {
    const entry = en[key];
    if (typeof entry === 'string' && entry) return formatTemplate(entry, localized.args);
  }
  if (UI_LOCALE === 'en' && key.startsWith('field.') && /[\u3400-\u9fff]/.test(fallback)) return fieldLabel(key.slice(6));
  return formatTemplate(UI_LOCALE === 'en' ? tr(fallback) : fallback, localized.args);
}
