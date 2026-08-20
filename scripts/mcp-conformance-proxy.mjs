import { readFileSync } from 'node:fs';
import http from 'node:http';

const [connectionPath, rawPort = '61999'] = process.argv.slice(2);
if (!connectionPath) {
  throw new Error('Usage: node scripts/mcp-conformance-proxy.mjs <connection.json> [port]');
}

const connection = JSON.parse(readFileSync(connectionPath, 'utf8'));
const listenPort = Number.parseInt(rawPort, 10);

const server = http.createServer((request, response) => {
  const headers = {
    ...request.headers,
    authorization: `Bearer ${connection.token}`,
    'x-copperbench-workspace': connection.workspaceId
  };
  const upstream = http.request({
    hostname: '127.0.0.1',
    port: connection.port,
    path: request.url,
    method: request.method,
    headers
  }, (upstreamResponse) => {
    response.writeHead(upstreamResponse.statusCode ?? 502, upstreamResponse.headers);
    upstreamResponse.pipe(response);
  });
  upstream.on('error', (error) => {
    if (!response.headersSent) response.writeHead(502, { 'content-type': 'text/plain' });
    response.end(error.message);
  });
  request.pipe(upstream);
});

server.listen(listenPort, '127.0.0.1', () => {
  process.stdout.write(`READY http://127.0.0.1:${listenPort}/mcp\n`);
});
