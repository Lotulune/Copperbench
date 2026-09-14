import { LocalizedText } from '../types/contract';
import { zh } from './zh';
import { UI_LOCALE, tr } from './locale';
import { valueLabel, fieldLabel } from './labels';

/**
 * 界面默认中文；用户可在标题栏选择英文，保存偏好并确认重载后生效。
 * 合同数据（诊断、字段标签、阶段文案）经 LocalizedText.key 查询词典渲染；
 * 缺失词条时回退 fallback（当前 fixtures 为英文）。用户数据、代码和日志保留原文；已知类型、枚举和权限档位由显示层翻译。
 */
export { UI_LOCALE } from './locale';

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
  }
  if (UI_LOCALE === 'en' && localized.key.startsWith('field.') && /[\u3400-\u9fff]/.test(localized.fallback)) return fieldLabel(localized.key.slice(6));
  return formatTemplate(UI_LOCALE === 'en' ? tr(localized.fallback) : localized.fallback, localized.args);
}
