import { LocalizedText } from '../types/contract';
import { zh } from './zh';
import { en } from './en';
import { valueLabel, fieldLabel } from './labels';
import { UI_LOCALE, uiText, tr } from './locale';
export { UI_LOCALE, setUiLocale, useUiLocale, uiText, englishCount } from './locale';

/** Store messages without choosing a language until they are rendered. */
export type UiMessage = string | LocalizedText | { zh: string; en: string };
export const uiMessage = (zh: string, en: string): UiMessage => ({ zh, en });
export function renderUiMessage(message: UiMessage): string {
  return typeof message === 'string' ? message : 'key' in message ? t(message) : uiText(message.zh, message.en);
}

/**
 * 中文为默认界面语言；用户可切换到英文，保留当前编辑草稿。
 * 合同数据（诊断、字段标签、阶段文案）经 LocalizedText.key 查询词典渲染；
 * 缺失词条时回退 fallback（当前 fixtures 为英文）。用户数据、代码和日志保留原文；已知类型、枚举和权限档位由显示层翻译。
 */
export function formatTemplate(template: string, args?: Record<string, unknown>): string {
  if (!args) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in args ? String(args[name]) : match
  );
}

/** Render a contract LocalizedText in the UI locale, falling back to `fallback`. */
export function t(localized: LocalizedText | null | undefined): string {
  if (!localized) return '';
  if (localized.key === 'field.option' && typeof localized.args?.label === 'string') {
    return valueLabel(localized.args.label);
  }
  if (UI_LOCALE === 'zh') {
    const entry = zh[localized.key];
    if (entry) return formatTemplate(entry, localized.args);
  } else {
    const entry = en[localized.key];
    if (entry) return formatTemplate(entry, localized.args);
  }
  if (UI_LOCALE === 'en' && localized.key.startsWith('field.') && /[\u3400-\u9fff]/.test(localized.fallback)) return fieldLabel(localized.key.slice(6));
  return formatTemplate(UI_LOCALE === 'en' ? tr(localized.fallback) : localized.fallback, localized.args);
}
