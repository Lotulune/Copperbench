import { CopperbenchClient, readWorkspaceConnection } from '../../sdk/typescript/copperbench.js';

const workspacePath = process.env.COPPERBENCH_WORKSPACE ?? '';
if (!workspacePath) throw new Error('COPPERBENCH_WORKSPACE must point to the workspace folder or .mcreator file');
const connection = readWorkspaceConnection(workspacePath);
const client = new CopperbenchClient({
  endpoint: process.env.COPPERBENCH_MCP_URL ?? connection.url,
  token: process.env.COPPERBENCH_TOKEN ?? '',
  workspaceId: process.env.COPPERBENCH_WORKSPACE_ID ?? connection.workspaceId
});

await client.initialize();
const workspace = await client.getWorkspace();
const revision = Number((workspace.data as { workspace?: { revision?: number } })?.workspace?.revision ?? 0);
for await (const element of client.listModElements({ fields: ['id', 'name', 'type'] })) {
  console.log(element);
}
console.log(await client.buildWorkspace(revision));
