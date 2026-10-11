/** Minimal dependency-free Copperbench MCP client for Node.js 22+. */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

export type JsonObject = Record<string, unknown>;

export interface CopperbenchClientOptions {
  endpoint: string;
  token: string;
  workspaceId: string;
  taskAuthorizationId?: string;
  fetch?: typeof fetch;
  maxTransportRetries?: number;
  retryBackoffMs?: number;
}

export interface WorkspaceConnection {
  url: string;
  workspaceId: string;
}

export class CopperbenchError extends Error {
  public constructor(
    message: string,
    public readonly code?: string,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = 'CopperbenchError';
  }
}

export interface TaskSummary {
  id: string;
  kind: string;
  state: string;
  progress: number | null;
  cancellable: boolean;
  [key: string]: unknown;
}

export class CopperbenchClient {
  private readonly request: typeof fetch;
  private nextRequestId = 1;
  private sessionId: string | undefined;

  public constructor(private readonly options: CopperbenchClientOptions) {
    this.request = options.fetch ?? fetch;
  }

  public static fromWorkspace(
    workspacePath: string,
    token: string,
    options: Omit<CopperbenchClientOptions, 'endpoint' | 'token' | 'workspaceId'> = {}
  ): CopperbenchClient {
    const connection = readWorkspaceConnection(workspacePath);
    return new CopperbenchClient({ ...options, endpoint: connection.url, token, workspaceId: connection.workspaceId });
  }

  public async initialize(clientName = 'copperbench-typescript-sdk', version = '0.1.0'): Promise<JsonObject> {
    const response = await this.rpc('initialize', {
      protocolVersion: '2025-11-25',
      capabilities: {},
      clientInfo: { name: clientName, version }
    }, false);
    await this.rpc('notifications/initialized', {}, true);
    return response;
  }

  public getWorkspace(): Promise<JsonObject> {
    return this.callTool('get_workspace', {});
  }

  public getWorkspaceHealth(): Promise<JsonObject> {
    return this.callTool('get_workspace_health', {});
  }

  /** Read source safety without writes/downloads; ready never authorizes generation or proves a build. */
  public previewGeneration(): Promise<JsonObject> {
    return this.callTool('preview_generation', {});
  }

  /** Read environment findings without starting downloads, tasks or authorization. */
  public doctor(): Promise<JsonObject> {
    return this.callTool('get_workspace_doctor', {});
  }

  public getModElementFieldContract(elementType: string): Promise<JsonObject> {
    return this.callTool('get_mod_element_field_contract', { elementType });
  }

  public getFieldReferenceOptions(elementType: string, mappingSource: string, options: JsonObject = {}): Promise<JsonObject> {
    return this.callTool('get_field_reference_options', { ...options, elementType, mappingSource });
  }

  public async *listModElements(args: JsonObject = {}): AsyncGenerator<JsonObject, void, void> {
    let cursor: string | undefined;
    do {
      const page = await this.callTool('list_mod_elements', {
        ...args,
        limit: args.limit ?? 200,
        ...(cursor ? { cursor } : {})
      });
      const data = page.data && typeof page.data === 'object' ? page.data as JsonObject : {};
      const items = Array.isArray(data.items) ? data.items : [];
      for (const item of items) yield item as JsonObject;
      cursor = typeof data.nextCursor === 'string' ? data.nextCursor : undefined;
    } while (cursor);
  }

  public createModElement(args: JsonObject): Promise<JsonObject> {
    return this.callTool('create_mod_element', args);
  }

  public updateModElement(args: JsonObject): Promise<JsonObject> {
    return this.callTool('update_mod_element', args);
  }

  public updateProcedure(args: JsonObject): Promise<JsonObject> {
    return this.callTool('update_procedure', args);
  }

  public renameRegistryEntry(args: JsonObject): Promise<JsonObject> {
    return this.callTool('rename_registry_entry', args);
  }

  public previewRegistryRename(args: JsonObject): Promise<JsonObject> {
    return this.callTool('preview_registry_rename', args);
  }

  public createRegistryEntry(args: JsonObject): Promise<JsonObject> {
    return this.callTool('create_registry_entry', args);
  }

  public listWorkspaceRegistries(args: JsonObject = {}): Promise<JsonObject> {
    return this.callTool('list_workspace_registries', args);
  }

  public planWorkspaceChanges(args: JsonObject): Promise<JsonObject> {
    return this.callTool('plan_workspace_changes', args);
  }

  public planProcedureRefactor(args: JsonObject): Promise<JsonObject> {
    return this.callTool('plan_procedure_refactor', args);
  }

  public previewWorkspacePlan(plan: JsonObject): Promise<JsonObject> {
    return this.callTool('preview_workspace_plan', { plan });
  }

  public applyWorkspacePlan(args: JsonObject): Promise<JsonObject> {
    return this.callTool('apply_workspace_plan', args);
  }

  public listAssets(args: JsonObject = {}): Promise<JsonObject> {
    return this.callTool('list_assets', args);
  }

  public previewAssetMove(args: JsonObject): Promise<JsonObject> {
    return this.callTool('preview_asset_move', args);
  }

  public moveAsset(args: JsonObject): Promise<JsonObject> {
    return this.callTool('move_asset', args);
  }

  public buildWorkspace(expectedRevision: number): Promise<JsonObject> {
    return this.callTool('build_workspace', { expectedRevision });
  }

  public runDatagen(expectedRevision: number): Promise<JsonObject> {
    return this.callTool('run_datagen', { expectedRevision });
  }

  public previewDatagenOutput(taskId: string): Promise<JsonObject> {
    return this.callTool('preview_datagen_output', { taskId });
  }

  public publishDatagenOutput(taskId: string, manifestHash: string, expectedRevision: number): Promise<JsonObject> {
    return this.callTool('publish_datagen_output', { taskId, manifestHash, expectedRevision });
  }

  public getTask(taskId: string, afterLogSequence = 0): Promise<JsonObject> {
    return this.callTool('get_task', { taskId, afterLogSequence });
  }

  public prepareGameTests(expectedRevision: number): Promise<JsonObject> {
    return this.callTool('prepare_game_tests', { expectedRevision });
  }

  public runGameTest(expectedRevision: number): Promise<JsonObject> {
    return this.callTool('run_gametest', { expectedRevision });
  }

  public listTaskAuthorizations(): Promise<JsonObject> {
    return this.callTool('list_task_authorizations', {});
  }

  public revokeTaskAuthorization(authorizationId: string, expectedRevision: number): Promise<JsonObject> {
    return this.callTool('revoke_task_authorization', { authorizationId, expectedRevision });
  }

  public cancelTask(taskId: string, expectedRevision: number): Promise<JsonObject> {
    return this.callTool('cancel_task', { taskId, expectedRevision });
  }

  public createRecoveryPoint(label: string, expectedRevision: number): Promise<JsonObject> {
    return this.callTool('create_recovery_point', { label, expectedRevision });
  }

  public previewRecoveryRestore(recoveryPointId: string): Promise<JsonObject> {
    return this.callTool('preview_recovery_restore', { recoveryPointId });
  }

  public restoreRecoveryPoint(recoveryPointId: string, expectedRevision: number): Promise<JsonObject> {
    return this.callTool('restore_recovery_point', {
      recoveryPointId,
      expectedRevision
    });
  }

  public callTool(name: string, argumentsValue: JsonObject): Promise<JsonObject> {
    if (this.options.taskAuthorizationId && TASK_AUTHORIZED_TOOLS.has(name)) {
      argumentsValue = { taskAuthorizationId: this.options.taskAuthorizationId, ...argumentsValue };
    }
    return this.rpc('tools/call', { name, arguments: argumentsValue }).then((result) => {
      if ('isError' in result && typeof result.isError !== 'boolean') {
        throw new CopperbenchError(`Tool ${name} returned an invalid isError flag`, 'MCP_TOOL_RESULT_INVALID', result);
      }
      const content = Array.isArray(result.content) ? result.content : [];
      const textItem = content.find((item) => item && typeof item === 'object' && (item as JsonObject).type === 'text') as JsonObject | undefined;
      const text = textItem?.text;
      if (typeof text !== 'string') {
        throw new CopperbenchError(`Tool ${name} returned no JSON text content`, 'MCP_TOOL_RESULT_INVALID', result);
      }
      let value: unknown;
      try {
        value = JSON.parse(text);
      } catch {
        if (result.isError === true) throw new CopperbenchError(text || 'MCP_TOOL_RESULT_INVALID', 'MCP_TOOL_RESULT_INVALID', result);
        throw new CopperbenchError(`Tool ${name} returned invalid JSON content`, 'MCP_TOOL_RESULT_INVALID', result);
      }
      if (!isJsonObject(value)) {
        if (result.isError === true) throw new CopperbenchError(text || 'MCP_TOOL_RESULT_INVALID', 'MCP_TOOL_RESULT_INVALID', result);
        throw new CopperbenchError(`Tool ${name} returned a non-object result`, 'MCP_TOOL_RESULT_INVALID', result);
      }
      if (result.isError === true || value.status === 'rejected' || value.status === 'failed'
        || (typeof value.code === 'string' && value.code
          && (typeof value.status !== 'string' || !SUCCESS_STATUSES.has(value.status)))) {
        const diagnostic = Array.isArray(value.diagnostics)
          ? value.diagnostics.find((item) => isJsonObject(item) && typeof item.code === 'string' && item.code) as JsonObject | undefined
          : undefined;
        const code = value.conflict ? 'REVISION_CONFLICT' : typeof value.code === 'string' && value.code ? value.code
          : typeof diagnostic?.code === 'string' ? diagnostic.code : 'MCP_TOOL_ERROR';
        throw new CopperbenchError(renderDiagnosticMessage(diagnostic?.message ?? value.message, code), code, value);
      }
      if (typeof value.status !== 'string' || !SUCCESS_STATUSES.has(value.status)) {
        throw new CopperbenchError(`Tool ${name} returned no recognized result status`, 'MCP_TOOL_RESULT_INVALID', value);
      }
      return value;
    });
  }

  private async rpc(method: string, params: JsonObject, notification = false): Promise<JsonObject> {
    const headers: Record<string, string> = {
      Accept: 'application/json, text/event-stream',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${this.options.token}`,
      'X-Copperbench-Workspace': this.options.workspaceId
    };
    if (this.sessionId) headers['mcp-session-id'] = this.sessionId;
    const body: JsonObject = { jsonrpc: '2.0', method, params };
    if (!notification) body.id = this.nextRequestId++;
    let response: Response | undefined;
    let raw = '';
    const retrySafe = method === 'tools/call' && typeof params.name === 'string' && RETRY_SAFE_TOOLS.has(params.name);
    const maxRetries = retrySafe ? Math.max(0, Math.min(2, this.options.maxTransportRetries ?? 2)) : 0;
    const backoffMs = Math.max(0, this.options.retryBackoffMs ?? 100);
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        response = await this.request(this.options.endpoint, { method: 'POST', headers, body: JSON.stringify(body) });
        if (response.ok) {
          raw = await response.text();
        } else {
          try {
            raw = await response.text();
          } catch {
            // A broken error body must not turn a 4xx denial into a retryable
            // transport failure, or hide the status of an uncertain write.
            raw = 'HTTP error response body was unavailable';
          }
        }
      } catch (error) {
        if (attempt >= maxRetries) throw new CopperbenchError('MCP transport failed', 'MCP_TRANSPORT_FAILED', error);
        await new Promise((resolve) => setTimeout(resolve, backoffMs * 2 ** attempt));
        continue;
      }
      if (response.ok || ![502, 503, 504].includes(response.status) || attempt >= maxRetries) break;
      await new Promise((resolve) => setTimeout(resolve, backoffMs * 2 ** attempt));
    }
    if (!response) throw new CopperbenchError('MCP transport failed', 'MCP_TRANSPORT_FAILED');
    if (!response.ok) throw new CopperbenchError(`MCP HTTP ${response.status}`, `HTTP_${response.status}`, raw);
    this.sessionId ??= response.headers.get('mcp-session-id') ?? undefined;
    if (notification) return {};
    const envelope = parseRpcEnvelope(raw);
    if (envelope.jsonrpc !== '2.0' || envelope.id !== body.id) {
      throw new CopperbenchError('MCP response has an invalid version or request ID', 'MCP_RESPONSE_INVALID', envelope);
    }
    if (('error' in envelope) === ('result' in envelope)) {
      throw new CopperbenchError('MCP response must contain exactly one result or error', 'MCP_RESPONSE_INVALID', envelope);
    }
    if ('error' in envelope) {
      const error = envelope.error;
      if (!isJsonObject(error) || typeof error.message !== 'string'
        || !((typeof error.code === 'number' && Number.isInteger(error.code))
          || (typeof error.code === 'string' && error.code.length > 0))) {
        throw new CopperbenchError('MCP response has an invalid JSON-RPC error', 'MCP_RESPONSE_INVALID', envelope);
      }
      throw new CopperbenchError(error.message, String(error.code), error);
    }
    if (!isJsonObject(envelope.result)) {
      throw new CopperbenchError('MCP response result must be an object', 'MCP_RESPONSE_INVALID', envelope);
    }
    return envelope.result;
  }
}

const TASK_AUTHORIZED_TOOLS = new Set([
  'create_workspace', 'create_mod_element', 'update_mod_element', 'delete_mod_element', 'update_procedure',
  'set_mod_element_source_management', 'create_registry_entry', 'update_registry_entry', 'delete_registry_entry',
  'rename_registry_entry', 'apply_workspace_plan', 'import_asset', 'import_asset_batch', 'move_asset',
  'create_recovery_point', 'restore_recovery_point', 'publish_datagen_output', 'prepare_game_tests',
  'build_workspace', 'generate_workspace', 'validate_workspace', 'run_gametest', 'run_datagen', 'run_client', 'run_server'
]);

// Audited reads only. Plans, previews, external probes and unknown/new tools
// remain single-attempt. Shared transport fixtures exercise the Python parity.
const RETRY_SAFE_TOOLS = new Set([
  'get_workspace', 'get_workspace_environment', 'get_workspace_doctor', 'preview_generation', 'get_mod_element_field_contract', 'get_field_reference_options', 'get_workspace_health', 'get_task',
  'list_mod_elements', 'read_mod_element', 'get_procedure', 'list_workspace_registries',
  'get_workspace_references', 'list_recovery_points', 'list_task_authorizations'
]);

const SUCCESS_STATUSES = new Set(['succeeded', 'committed', 'accepted', 'completed', 'cancelled']);

function isJsonObject(value: unknown): value is JsonObject {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function parseRpcEnvelope(body: string): JsonObject {
  const trimmed = body.trim();
  const encoded = trimmed.startsWith('{') ? trimmed
    : trimmed.split(/\r?\n/).find((line) => line.startsWith('data:'))?.slice(5).trimStart();
  if (encoded === undefined) {
    throw new CopperbenchError('MCP response did not contain a JSON-RPC envelope', 'MCP_RESPONSE_INVALID', body);
  }
  let envelope: unknown;
  try {
    envelope = JSON.parse(encoded);
  } catch {
    throw new CopperbenchError('MCP response contained invalid JSON', 'MCP_RESPONSE_INVALID', body);
  }
  if (!isJsonObject(envelope)) {
    throw new CopperbenchError('MCP response envelope must be an object', 'MCP_RESPONSE_INVALID', body);
  }
  return envelope;
}

export function readWorkspaceConnection(workspacePath: string): WorkspaceConnection {
  const absolute = resolve(workspacePath);
  const root = absolute.toLowerCase().endsWith('.mcreator') ? dirname(absolute) : absolute;
  const path = resolve(root, '.copperbench', 'mcp-connection.json');
  let value: unknown;
  try {
    value = JSON.parse(readFileSync(path, 'utf8'));
  } catch (error) {
    throw new CopperbenchError(`Could not read Copperbench MCP connection metadata: ${path}`, 'MCP_CONNECTION_FILE_UNAVAILABLE', error);
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new CopperbenchError('MCP connection metadata must be a JSON object', 'MCP_CONNECTION_FILE_INVALID', value);
  }
  const connection = value as JsonObject;
  if ('token' in connection || 'authorization' in connection) {
    throw new CopperbenchError('MCP connection metadata must not contain credentials', 'MCP_CONNECTION_FILE_INVALID');
  }
  if (connection.schemaVersion !== '1.0' || connection.status !== 'listening') {
    throw new CopperbenchError('MCP connection metadata is not a listening v1.0 endpoint', 'MCP_CONNECTION_FILE_INVALID', connection);
  }
  let parsedUrl: URL | null = null;
  if (typeof connection.url === 'string') {
    try {
      parsedUrl = new URL(connection.url);
    } catch {
      parsedUrl = null;
    }
  }
  const port = parsedUrl?.port ? Number.parseInt(parsedUrl.port, 10) : Number.NaN;
  if (
    !parsedUrl
    || parsedUrl.protocol !== 'http:'
    || parsedUrl.hostname !== '127.0.0.1'
    || !Number.isInteger(port)
    || port < 1
    || port > 65535
    || parsedUrl.username !== ''
    || parsedUrl.password !== ''
    || parsedUrl.pathname !== '/mcp'
    || parsedUrl.search !== ''
    || parsedUrl.hash !== ''
  ) {
    throw new CopperbenchError('MCP connection URL must be a loopback /mcp endpoint', 'MCP_CONNECTION_FILE_INVALID', connection);
  }
  if (typeof connection.workspaceId !== 'string' || !connection.workspaceId) {
    throw new CopperbenchError('MCP connection metadata has no workspaceId', 'MCP_CONNECTION_FILE_INVALID', connection);
  }
  return { url: connection.url as string, workspaceId: connection.workspaceId };
}
/** Single-pass named placeholders; raw diagnostic objects remain untouched. */
export function renderDiagnosticMessage(message: unknown, fallbackCode: string): string {
  if (typeof message === 'string') return message || fallbackCode;
  if (!isJsonObject(message)) return fallbackCode;
  const template = typeof message.fallback === 'string' && message.fallback ? message.fallback : fallbackCode;
  const args = message.args;
  if (!isJsonObject(args)) return template;
  return template.replace(/\{([A-Za-z_][A-Za-z0-9_]*)\}/g, (match, name: string) => {
    if (!Object.hasOwn(args, name)) return match;
    const value = args[name];
    if (typeof value === 'string') return value;
    if (value === null || typeof value === 'boolean' || (typeof value === 'number' && Number.isFinite(value))) {
      return JSON.stringify(value);
    }
    return match;
  });
}
