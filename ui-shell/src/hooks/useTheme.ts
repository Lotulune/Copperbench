import { useCallback, useEffect, useLayoutEffect, useState } from 'react';

export type ThemePreference = 'system' | 'light' | 'dark';
const STORAGE_KEY = 'copperbench.theme';
const SYSTEM_QUERY = '(prefers-color-scheme: dark)';

function readPreference(): ThemePreference {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch { /* Restricted storage must not prevent the editor from opening. */ }
  return 'system';
}

export function useTheme() {
  const [themePreference, setThemePreference] = useState<ThemePreference>(readPreference);
  const [systemDark, setSystemDark] = useState(() => window.matchMedia(SYSTEM_QUERY).matches);
  const theme = themePreference === 'system' ? (systemDark ? 'dark' : 'light') : themePreference;

  useLayoutEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.dataset.themePreference = themePreference;
    document.documentElement.style.colorScheme = theme;
  }, [theme, themePreference]);

  useEffect(() => {
    const query = window.matchMedia(SYSTEM_QUERY);
    const update = () => setSystemDark(query.matches);
    update();
    query.addEventListener('change', update);
    const sync = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY || event.key === null) setThemePreference(readPreference());
    };
    window.addEventListener('storage', sync);
    return () => {
      query.removeEventListener('change', update);
      window.removeEventListener('storage', sync);
    };
  }, []);

  const toggleTheme = useCallback(() => {
    const next: ThemePreference = themePreference === 'system' ? 'light' : themePreference === 'light' ? 'dark' : 'system';
    setThemePreference(next);
    try { localStorage.setItem(STORAGE_KEY, next); } catch { /* Keep the session choice if storage is unavailable. */ }
  }, [themePreference]);

  return { theme, themePreference, toggleTheme };
}
