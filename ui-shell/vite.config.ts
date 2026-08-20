import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  base: './',
  plugins: [react()],
  server: {
    port: 5173,
    host: 'localhost',
    // Allow serving the ui-core contract fixtures from the sibling workspace
    // directory; scenario data is imported directly from there.
    fs: {
      allow: ['..']
    }
  }
});
