import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

import { UI_LOCALE } from './i18n/locale';

document.documentElement.lang = UI_LOCALE === 'en' ? 'en' : 'zh-CN';

const rootElement = document.getElementById('root');
if (rootElement) {
  ReactDOM.createRoot(rootElement).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}
