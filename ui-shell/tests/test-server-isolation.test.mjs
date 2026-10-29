import assert from 'node:assert/strict';
import test from 'node:test';
import { execFile } from 'node:child_process';
import { randomBytes, randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { createServer as createHttpServer, request } from 'node:http';
import { Server } from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { loadSsrTestModules } from './helpers/vite-ssr-loader.mjs';

const uiShell = fileURLToPath(new URL('../', import.meta.url));
const configurations = [
  { name: 'UI', path: '../playwright.config.ts', variable: 'COPPERBENCH_E2E_PORT' },
  { name: 'performance', path: '../performance/playwright.config.ts', variable: 'COPPERBENCH_PERF_PORT' }
];

async function loadConfiguration(configuration, port) {
  const values = { [configuration.variable]: String(port), COPPERBENCH_PERF_OUTPUT: 'server-isolation-fixture' };
  const previous = Object.fromEntries(Object.keys(values).map(key => [key, process.env[key]]));
  Object.assign(process.env, values);
  try {
    const url = new URL(configuration.path, import.meta.url);
    url.searchParams.set('server-isolation', randomUUID());
    return (await import(url.href)).default;
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
  }
}

function runNode(arguments_, cwd = uiShell) {
  return new Promise(resolve => {
    execFile(process.execPath, arguments_, { cwd, timeout: 15000, maxBuffer: 1024 * 1024, windowsHide: true },
      (error, stdout, stderr) => resolve({ error, output: stdout + stderr }));
  });
}

async function foreignServer(t, status = 200) {
  let requests = 0;
  const server = createHttpServer((_request, response) => {
    requests++;
    response.writeHead(status);
    response.end('unrelated-worktree');
  });
  const listening = once(server, 'listening');
  server.listen(0, '127.0.0.1');
  await listening;
  t.after(() => new Promise((resolve, reject) => {
    server.close(error => error ? reject(error) : resolve());
    server.closeAllConnections();
  }));
  return { port: server.address().port, requestCount: () => requests };
}

function upgradeHmr(url) {
  return new Promise((resolve, reject) => {
    const req = request(url, { headers: {
      Connection: 'Upgrade', Upgrade: 'websocket',
      'Sec-WebSocket-Key': randomBytes(16).toString('base64'),
      'Sec-WebSocket-Version': '13', 'Sec-WebSocket-Protocol': 'vite-hmr'
    } });
    req.setTimeout(5000, () => req.destroy(new Error('HMR handshake timed out.')));
    req.once('error', reject);
    req.once('upgrade', (response, socket) => {
      socket.destroy();
      resolve(response);
    });
    req.once('response', response => {
      response.resume();
      reject(new Error(`HMR upgrade returned HTTP ${response.statusCode}.`));
    });
    req.end();
  });
}

test('SSR production-module loaders open no TCP or WebSocket listener', async t => {
  const realListen = Server.prototype.listen;
  const listen = t.mock.method(Server.prototype, 'listen', function (...arguments_) {
    return Reflect.apply(realListen, this, arguments_);
  });
  const modules = await Promise.all([1, 2].map(() => loadSsrTestModules(['/src/hooks/editorFieldChanges.ts'])));
  for (const [fields] of modules) assert.equal(fields.numericDraftValue('2.5'), 2.5);
  // Observe the real implementation, without reserving or assuming access to 24678.
  assert.equal(listen.mock.callCount(), 0);
});

test('normal Vite HMR shares its selected loopback HTTP listener', async t => {
  const realListen = Server.prototype.listen;
  const listen = t.mock.method(Server.prototype, 'listen', function (...arguments_) {
    return Reflect.apply(realListen, this, arguments_);
  });
  const server = await createServer({
    root: uiShell, configFile: join(uiShell, 'vite.config.ts'), logLevel: 'silent',
    server: { host: '127.0.0.1', port: 0, strictPort: true, watch: null },
    optimizeDeps: { noDiscovery: true }
  });
  try {
    await server.listen();
    const address = server.httpServer.address();
    assert.equal(address.address, '127.0.0.1');
    assert.ok(address.port > 0);
    const response = await upgradeHmr(`http://127.0.0.1:${address.port}/`);
    assert.equal(response.statusCode, 101);
    assert.equal(response.headers['sec-websocket-protocol'], 'vite-hmr');
    assert.equal(listen.mock.callCount(), 1);
  } finally {
    await server.close();
  }
});

test('Playwright configurations reject invalid port overrides', async () => {
  for (const configuration of configurations) {
    for (const port of ['', '0', '1023', '65536', '51.83', '1e4', 'NaN', '-1', '5183 --host 0.0.0.0']) {
      await assert.rejects(loadConfiguration(configuration, port), new RegExp(configuration.variable));
    }
  }
});

for (const configuration of configurations) {
  test(`${configuration.name} Vite startup fails on an occupied port instead of moving ports`, async t => {
    const foreign = await foreignServer(t, 404);
    const { webServer, use } = await loadConfiguration(configuration, foreign.port);
    assert.equal(use.baseURL, webServer.url);
    assert.equal(webServer.cwd, uiShell);
    const [executable, ...arguments_] = webServer.command.split(' ');
    assert.equal(executable, 'node');
    const result = await runNode(arguments_, webServer.cwd);
    assert.equal(result.error?.code, 1, result.output);
    assert.match(result.output, new RegExp(`Port ${foreign.port} is already in use`));
  });

  test(`${configuration.name} Playwright rejects an existing server from another source tree`, async t => {
    const foreign = await foreignServer(t);
    const { webServer } = await loadConfiguration(configuration, foreign.port);
    const directory = mkdtempSync(join(tmpdir(), 'copperbench-server-isolation-'));
    t.after(() => rmSync(directory, { recursive: true, force: true }));
    const configPath = join(directory, 'playwright.config.mjs');
    // Exercise Playwright's actual webServer plugin with the production config's
    // options. The single fixture uses no browser, even if reuse regresses.
    writeFileSync(configPath, `export default ${JSON.stringify({
      testDir: fileURLToPath(new URL('./fixtures', import.meta.url)),
      testMatch: 'server-startup.playwright.mjs',
      outputDir: join(directory, 'results'),
      reporter: 'list', workers: 1, retries: 0, webServer
    })};\n`, 'utf8');
    const result = await runNode(['node_modules/playwright/cli.js', 'test', '--config', configPath]);
    assert.equal(result.error?.code, 1, result.output);
    assert.match(result.output, /is already used/);
    assert.ok(result.output.includes(webServer.url), result.output);
    assert.ok(foreign.requestCount() > 0);
  });
}
