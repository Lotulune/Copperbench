import { coreBridge } from './index';
import { safeRandomUUID } from './JcefCoreBridge';
import { t, uiText } from '../i18n';
import type { QueryOperation, WorkspaceSourceContent, WorkspaceSourceFiles, WorkspaceSourceIndex } from '../types/contract';

async function query<T>(workspaceId: string, operation: QueryOperation, payload: Record<string, unknown>) {
  const result = await coreBridge.sendQuery<T>({ messageType: 'query', schemaVersion: '1.0',
    requestId: safeRandomUUID(), workspaceId, operation, payload });
  if (result.workspaceId !== workspaceId || result.status !== 'succeeded' || !result.data) {
    throw new Error(result.diagnostics.map(diagnostic => t(diagnostic.message)).join('\n')
      || uiText('无法读取工作区源码。', 'Could not read workspace source.'));
  }
  return { data: result.data, revision: result.revision };
}

export const sourceBridge = {
  list(workspaceId: string, search = '', offset = 0) {
    return query<WorkspaceSourceFiles>(workspaceId, 'list_workspace_files', { search, offset, limit: 200 });
  },
  read(workspaceId: string, relativePath: string) {
    return query<WorkspaceSourceContent>(workspaceId, 'read_workspace_file', { relativePath });
  },
  index(workspaceId: string) {
    return query<WorkspaceSourceIndex>(workspaceId, 'get_workspace_source_index', {});
  },
  save(workspaceId: string, relativePath: string, content: string, expectedRevision: number, expectedSha256: string) {
    return coreBridge.sendCommand({ messageType: 'command', schemaVersion: '1.0', requestId: safeRandomUUID(),
      workspaceId, expectedRevision, operation: 'update_workspace_file',
      payload: { clientMutationId: safeRandomUUID(), relativePath, content, expectedSha256 } });
  }
};
