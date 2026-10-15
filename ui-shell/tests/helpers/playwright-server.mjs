import { fileURLToPath } from 'node:url';

export function playwrightTestServer(portVariable, defaultPort, timeout = 15000) {
  const rawPort = process.env[portVariable] ?? String(defaultPort);
  const port = Number(rawPort);
  if (!/^\d+$/.test(rawPort) || !Number.isInteger(port) || port < 1024 || port > 65535) {
    throw new Error(`${portVariable} must be an integer port between 1024 and 65535.`);
  }
  return {
    command: `node node_modules/vite/bin/vite.js --host 127.0.0.1 --port ${port} --strictPort`,
    cwd: fileURLToPath(new URL('../../', import.meta.url)),
    url: `http://127.0.0.1:${port}`,
    reuseExistingServer: false,
    timeout
  };
}
