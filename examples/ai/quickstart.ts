import { CopperbenchClient } from '../../sdk/typescript/copperbench.js';

const client = new CopperbenchClient({
  endpoint: process.env.COPPERBENCH_MCP_URL ?? 'http://127.0.0.1:8787/mcp',
  token: process.env.COPPERBENCH_TOKEN ?? '',
  workspaceId: process.env.COPPERBENCH_WORKSPACE_ID ?? ''
});

await client.initialize();
const workspace = await client.getWorkspace();
const revision = Number((workspace.data as { workspace?: { revision?: number } })?.workspace?.revision ?? 0);
for await (const element of client.listModElements({ fields: ['id', 'name', 'type'] })) {
  console.log(element);
}
console.log(await client.buildWorkspace(revision));
