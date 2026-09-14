import React from 'react';
import { UI_LOCALE, changeLocale, type UiLocale } from '../i18n/locale';

export const LanguageSelector: React.FC = () => (
  <select
    aria-label={UI_LOCALE === 'zh' ? '界面语言' : 'Interface language'}
    title={UI_LOCALE === 'zh' ? '切换语言（需要重新加载）' : 'Change language (reload required)'}
    value={UI_LOCALE}
    onChange={async event => {
      const select = event.currentTarget;
      select.disabled = true;
      const changed = await changeLocale(select.value as UiLocale);
      if (!changed) { select.value = UI_LOCALE; select.disabled = false; }
    }}
    className="btn-secondary titlebar-tool"
    style={{ width: 'auto', minWidth: 94, padding: '4px 8px', flexShrink: 0 }}
    data-testid="language-select"
    data-window-chrome-kind="client"
    data-window-chrome-id="language"
  >
    <option value="zh">简体中文</option>
    <option value="en">English</option>
  </select>
);
