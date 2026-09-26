import React, { useState } from 'react';
import { setUiLocale, useUiLocale, uiText, type UiLocale } from '../i18n/locale';

export const LanguageSelector: React.FC = () => {
  const locale = useUiLocale();
  const [saving, setSaving] = useState(false);
  return <select
    aria-label={uiText('界面语言', 'Interface language')}
    title={uiText('切换语言并保留草稿', 'Change language and keep drafts')}
    value={locale}
    disabled={saving}
    onChange={async event => {
      const next = event.currentTarget.value as UiLocale;
      setSaving(true);
      try { await setUiLocale(next); } finally { setSaving(false); }
    }}
    style={{ width: '86px', minHeight: '28px', fontSize: '12px', WebkitAppRegion: 'no-drag' } as React.CSSProperties}
    data-testid="ui-language-select"
    data-window-chrome-kind="client"
    data-window-chrome-id="language"
  >
    <option value="zh">中文</option>
    <option value="en">English</option>
  </select>;
};
