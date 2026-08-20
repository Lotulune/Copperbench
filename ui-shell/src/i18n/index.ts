import { LocalizedText } from '../types/contract';
import { zh } from './zh';

/**
 * 产品决策（2026-08-17）：界面主语言为中文。
 * 合同数据（诊断、字段标签、阶段文案）经 LocalizedText.key 查询词典渲染；
 * 缺失词条时回退 fallback（当前 fixtures 为英文）。技术标识（元素名、枚举、
 * 日志原文、MCP 档位名）保留英文原文，不做翻译。
 */
export const UI_LOCALE = 'zh' as const;

export function formatTemplate(template: string, args?: Record<string, unknown>): string {
  if (!args) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in args ? String(args[name]) : match
  );
}

/** Render a contract LocalizedText in the UI locale, falling back to `fallback`. */
export function t(localized: LocalizedText | null | undefined): string {
  if (!localized) return '';
  if (UI_LOCALE === 'zh') {
    const entry = zh[localized.key];
    if (entry) return formatTemplate(entry, localized.args);
  }
  return formatTemplate(localized.fallback, localized.args);
}
