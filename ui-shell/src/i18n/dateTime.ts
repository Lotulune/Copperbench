import { UI_LOCALE, type UiLocale } from './locale';

export const localTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone;
const formatters = new Map<string, Intl.DateTimeFormat>();
function format(value: string, timeOnly: boolean, locale: UiLocale, timeZone: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const key = `${locale}:${timeZone}:${timeOnly}`;
  let formatter = formatters.get(key);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(locale === 'zh' ? 'zh-CN' : 'en-US', {
    ...(timeOnly ? {} : { year: 'numeric', month: '2-digit', day: '2-digit' } as const),
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
    timeZone, ...(timeOnly ? {} : { timeZoneName: 'shortOffset' } as const)
    });
    formatters.set(key, formatter);
  }
  return formatter.format(date);
}
export const formatDateTime = (value: string, locale = UI_LOCALE, timeZone = localTimeZone()) => format(value, false, locale, timeZone);
export const formatTime = (value: string, locale = UI_LOCALE, timeZone = localTimeZone()) => format(value, true, locale, timeZone);
