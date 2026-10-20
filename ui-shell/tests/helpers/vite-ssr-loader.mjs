import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

// Vite 6 still creates its middleware WebSocket server with hmr:false. ws:false
// prevents the default 24678 listener; these loaders only need SSR transforms.
export async function loadSsrTestModules(paths) {
  const loader = await createServer({
    configFile: false,
    root: fileURLToPath(new URL('../../', import.meta.url)),
    server: { middlewareMode: true, hmr: false, ws: false, watch: null },
    appType: 'custom',
    optimizeDeps: { noDiscovery: true }
  });
  try {
    return await Promise.all(paths.map(path => loader.ssrLoadModule(path)));
  } finally {
    await loader.close();
  }
}
