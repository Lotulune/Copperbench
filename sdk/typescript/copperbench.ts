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
  metrics?: ClientMetrics;
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

export interface WaitTaskOptions {
  timeoutMs?: number;
  pollIntervalMs?: number;
  afterLogSequence?: number;
  onUpdate?: (response: JsonObject) => void | Promise<void>;
}

/** Opt-in counters; no credentials, payloads, IDs or log text are retained. */
export class ClientMetrics {
  private readonly operations = new Map<string, { logicalCalls: number; attempts: number; responseBytes: number; elapsedSeconds: number }>();

  public add(operation: string, field: 'logicalCalls' | 'attempts' | 'responseBytes' | 'elapsedSeconds', value = 1): void {
    const row = this.operations.get(operation) ?? { logicalCalls: 0, attempts: 0, responseBytes: 0, elapsedSeconds: 0 };
    row[field] += value;
    this.operations.set(operation, row);
  }

  public snapshot(): JsonObject {
    return { operations: Object.fromEntries([...this.operations.entries()].sort(([left], [right]) => left.localeCompare(right))
      .map(([name, row]) => [name, { ...row }])),
    byteScope: 'complete response bodies; transport headers and interrupted bodies excluded' };
  }
}

const MCP_PROTOCOL_VERSION = '2025-11-25';

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
      protocolVersion: MCP_PROTOCOL_VERSION,
      capabilities: {},
      clientInfo: { name: clientName, version }
    }, false);
    if (typeof response.protocolVersion !== 'string') {
      throw new CopperbenchError('MCP initialization has no protocol version', 'MCP_RESPONSE_INVALID', response);
    }
    if (response.protocolVersion !== MCP_PROTOCOL_VERSION) {
      throw new CopperbenchError('The host selected an unsupported MCP protocol', 'MCP_PROTOCOL_INCOMPATIBLE', response);
    }
    await this.rpc('notifications/initialized', {}, true);
    return response;
  }

  public async close(): Promise<void> {
    if (!this.sessionId) return;
    const started = performance.now();
    this.options.metrics?.add('session/close', 'logicalCalls');
    this.options.metrics?.add('session/close', 'attempts');
    try {
      const response = await this.request(this.options.endpoint, { method: 'DELETE', headers: {
        Authorization: `Bearer ${this.options.token}`, 'X-Copperbench-Workspace': this.options.workspaceId,
        'mcp-session-id': this.sessionId
      } });
      let raw = 'HTTP error response body was unavailable';
      try {
        const received = await response.arrayBuffer();
        this.options.metrics?.add('session/close', 'responseBytes', received.byteLength);
        raw = new TextDecoder().decode(received);
      } catch (error) {
        if (response.ok) throw error;
      }
      if (!response.ok && response.status !== 404) {
        throw new CopperbenchError(`MCP HTTP ${response.status}`, `HTTP_${response.status}`, raw);
      }
      this.sessionId = undefined;
    } catch (error) {
      if (error instanceof CopperbenchError) throw error;
      throw new CopperbenchError('MCP session close failed; request was not retried', 'MCP_TRANSPORT_FAILED', error);
    } finally {
      this.options.metrics?.add('session/close', 'elapsedSeconds', (performance.now() - started) / 1000);
    }
  }

  public async *listTools(): AsyncGenerator<JsonObject, void, void> {
    let cursor: string | undefined;
    const seen = new Set<string>();
    do {
      const result = await this.rpc('tools/list', cursor ? { cursor } : {});
      if (!Array.isArray(result.tools) || result.tools.some((tool) => !isJsonObject(tool)
        || typeof tool.name !== 'string' || !tool.name || !isJsonObject(tool.inputSchema))) {
        throw new CopperbenchError('MCP tool catalog is malformed', 'MCP_RESPONSE_INVALID', result);
      }
      const next = result.nextCursor;
      if (next !== undefined && next !== null && (typeof next !== 'string' || !next || seen.has(next))) {
        throw new CopperbenchError('MCP tool catalog has an invalid or repeated cursor', 'MCP_RESPONSE_INVALID', result);
      }
      cursor = typeof next === 'string' ? next : undefined;
      if (cursor) seen.add(cursor);
      for (const tool of result.tools) yield tool as JsonObject;
    } while (cursor !== undefined);
  }

  public getWorkspace(): Promise<JsonObject> {
    return this.callTool('get_workspace', {});
  }

  public getWorkspaceHealth(): Promise<JsonObject> {
    return this.callTool('get_workspace_health', {});
  }

  /** Explicit editor opt-in; 1.0 remains the frozen legacy projection. */
  public async readModElement(elementId: string, schemaVersion: '1.0' | '1.1' = '1.1'): Promise<JsonObject> {
    const result = await this.callTool('read_mod_element', { elementId, schemaVersion });
    if (result.schemaVersion !== schemaVersion)
      throw new CopperbenchError('Core did not return the requested editor schema', 'MCP_TOOL_RESULT_INVALID', result);
    return result;
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

  public getWorkspaceEnvironment(): Promise<JsonObject> {
    return this.callTool('get_workspace_environment', {});
  }

  public async fieldContract(elementType = 'block', environment?: JsonObject): Promise<JsonObject> {
    const result = environment ?? await this.getWorkspaceEnvironment();
    if (!isJsonObject(result.data)) {
      throw new CopperbenchError('Environment response must contain object data', 'MCP_TOOL_RESULT_INVALID', result);
    }
    const contracts = result.data.fieldContracts;
    if (contracts === undefined) {
      throw new CopperbenchError('This host does not expose field contracts', 'FIELD_CONTRACT_NOT_EXPOSED', result);
    }
    if (!isJsonObject(contracts)) {
      throw new CopperbenchError('Environment fieldContracts must be an object', 'MCP_TOOL_RESULT_INVALID', result);
    }
    if (!(elementType in contracts)) {
      throw new CopperbenchError(`No field contract is exposed for ${elementType}`, 'FIELD_CONTRACT_NOT_EXPOSED', result);
    }
    if (!isJsonObject(contracts[elementType])) {
      throw new CopperbenchError('The requested field contract is malformed', 'MCP_TOOL_RESULT_INVALID', result);
    }
    return contracts[elementType];
  }

  public async *listModElements(args: JsonObject = {}): AsyncGenerator<JsonObject, void, void> {
    let cursor = typeof args.cursor === 'string' ? args.cursor : undefined;
    const seen = new Set<string>(cursor ? [cursor] : []);
    do {
      const page = await this.callTool('list_mod_elements', {
        ...args,
        limit: args.limit ?? 200,
        ...(cursor ? { cursor } : {})
      });
      const data = page.data;
      if (!isJsonObject(data) || !Array.isArray(data.items) || data.items.some((item) => !isJsonObject(item))
        || !('nextCursor' in data)) {
        throw new CopperbenchError('List response must contain object items and nextCursor', 'MCP_TOOL_RESULT_INVALID', page);
      }
      const next = data.nextCursor;
      if (next !== null && (typeof next !== 'string' || !next || seen.has(next))) {
        throw new CopperbenchError('List response has an invalid or repeated cursor', 'MCP_TOOL_RESULT_INVALID', page);
      }
      cursor = typeof next === 'string' ? next : undefined;
      if (cursor) seen.add(cursor);
      for (const item of data.items) yield item as JsonObject;
    } while (cursor !== undefined);
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

  public async waitTask(taskId: string, options: WaitTaskOptions = {}): Promise<JsonObject> {
    const timeout = options.timeoutMs ?? 2_700_000;
    const interval = options.pollIntervalMs ?? 200;
    let sequence = options.afterLogSequence ?? 0;
    if (!Number.isFinite(timeout) || !Number.isFinite(interval) || timeout <= 0 || interval <= 0
      || !Number.isSafeInteger(sequence) || sequence < 0) {
      throw new RangeError('Timeouts must be positive and finite; afterLogSequence must be a nonnegative safe integer');
    }
    const deadline = performance.now() + timeout;
    const logs: JsonObject[] = [];
    for (;;) {
      const result = await this.getTask(taskId, sequence);
      const data = result.data;
      const task = isJsonObject(data) ? data.task : undefined;
      if (!isJsonObject(data) || !isJsonObject(task) || task.id !== taskId
        || typeof task.state !== 'string' || !TASK_STATES.has(task.state)
        || !Array.isArray(data.logs) || !Array.isArray(data.diagnostics)) {
        throw new CopperbenchError('Task response has an invalid identity, state, logs or diagnostics', 'MCP_TOOL_RESULT_INVALID', result);
      }
      let nextSequence = sequence;
      for (const entry of data.logs) {
        if (!isJsonObject(entry) || typeof entry.sequence !== 'number' || !Number.isSafeInteger(entry.sequence)
          || entry.sequence <= nextSequence) {
          throw new CopperbenchError('Task logs must have strictly increasing new sequences', 'MCP_TOOL_RESULT_INVALID', result);
        }
        nextSequence = entry.sequence;
      }
      sequence = nextSequence;
      for (const entry of data.logs) logs.push(entry as JsonObject);
      await options.onUpdate?.(result);
      if (['succeeded', 'failed', 'cancelled'].includes(task.state)) return { ...result, data: { ...data, logs } };
      const remaining = deadline - performance.now();
      if (remaining <= 0) {
        throw new CopperbenchError(`Task ${taskId} is still running; resume after log sequence ${sequence}`, 'MCP_TASK_TIMEOUT', { ...result, data: { ...data, logs } });
      }
      await new Promise((resolve) => setTimeout(resolve, Math.min(interval, remaining)));
    }
  }

  public exportVerifiedArtifact(taskId: string, expectedRevision: number, allowHistorical = false): Promise<JsonObject> {
    return this.callTool('export_verified_artifact', { verifiedTaskId: taskId, expectedRevision, allowHistorical });
  }

  public prepareGameTests(expectedRevision: number): Promise<JsonObject> {
    return this.callTool('prepare_game_tests', { expectedRevision });
  }

  public runGameTest(expectedRevision: number): Promise<JsonObject> {
    return this.callTool('run_gametest', { expectedRevision });
  }

  /** Version 1 is read-only. Explicit version 2 executes a reviewed plan once with target authority. */
  public async versionCopyUpgrade(operation: 'negotiate' | 'preview' | 'execute', targetGeneratorId?: string,
    supportedVersions: string[] = ['1'], options: { outputName?: string; planId?: string;
      expectedRevision?: number; taskAuthorizationId?: string } = {}): Promise<JsonObject> {
    const result = await this.callTool('version_copy_upgrade', { operation, supportedVersions,
      ...(targetGeneratorId !== undefined ? { targetGeneratorId } : {}), ...options });
    if (result.contract !== 'version_copy_upgrade' || !supportedVersions.includes(result.selectedVersion as string))
      throw new CopperbenchError('Version copy upgrade contract was not negotiated', 'INVALID_RESPONSE', result);
    return result;
  }

  /** Preview choice never changes task authority, approval or EULA state. */
  public async preview(operation: 'negotiate' | 'catalog' | 'opt_in' | 'revoke' | 'command' | 'acceptance',
    options: { generatorId?: string; command?: string; workspaceFolderPath?: string; expectedRevision?: number;
      payload?: JsonObject; supportedVersions?: string[] } = {}): Promise<JsonObject> {
    const { supportedVersions = ['1'], ...request } = options;
    if (['opt_in', 'revoke', 'command'].includes(operation) &&
      (!Number.isSafeInteger(request.expectedRevision) || request.expectedRevision! < 0))
      throw new TypeError('expectedRevision must be a nonnegative safe integer');
    const args: JsonObject = { ...request, operation, supportedVersions };
    if (operation === 'command') {
      args.payload = { ...options.payload };
      if (this.options.taskAuthorizationId && TASK_AUTHORIZED_TOOLS.has(request.command ?? '') && !('taskAuthorizationId' in (args.payload as JsonObject)))
        (args.payload as JsonObject).taskAuthorizationId = this.options.taskAuthorizationId;
    }
    const result = await this.callTool('preview', args);
    if (result.contract !== 'preview_targets' || !supportedVersions.includes(result.selectedVersion as string))
      throw new CopperbenchError('Preview contract was not negotiated', 'INVALID_RESPONSE', result);
    return result;
  }

  /** Per-request negotiation, preserving existing authorization and revision checks. */
  public async acceptance(operation: 'negotiate' | 'catalog' | 'run' | 'get' | 'export', payload: JsonObject = {},
    expectedRevision?: number, supportedVersions: string[] = ['1'], diagnosticVersions?: string[]): Promise<JsonObject> {
    const argumentsValue: JsonObject = { operation, supportedVersions, payload: { ...payload } };
    if (diagnosticVersions !== undefined) argumentsValue.diagnosticVersions = diagnosticVersions;
    if (operation === 'run' || operation === 'export') {
      if (!Number.isSafeInteger(expectedRevision) || expectedRevision! < 0)
        throw new TypeError('expectedRevision must be a nonnegative safe integer');
      argumentsValue.expectedRevision = expectedRevision;
      if (operation === 'run' && this.options.taskAuthorizationId && !('taskAuthorizationId' in payload))
        (argumentsValue.payload as JsonObject).taskAuthorizationId = this.options.taskAuthorizationId;
    }
    const result = await this.callTool('acceptance', argumentsValue);
    if (result.contract !== 'required_cases' || !supportedVersions.includes(result.selectedVersion as string))
      throw new CopperbenchError('Required-case contract was not negotiated', 'INVALID_RESPONSE', result);
    if (diagnosticVersions !== undefined && !diagnosticVersions.includes((result.coverageDiagnostics as JsonObject | undefined)?.schemaVersion as string))
      throw new CopperbenchError('Acceptance diagnostic contract was not negotiated', 'INVALID_RESPONSE', result);
    return result;
  }

  public async getAcceptanceDiagnostics(taskId: string): Promise<JsonObject> {
    const response = await this.acceptance('get', { taskId }, undefined, ['1'], ['1']);
    return response.coverageDiagnostics as JsonObject;
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
    if (!this.options.metrics) return this.rpcOnce(method, params, notification);
    const operation = method === 'tools/call' ? String(params.name) : method;
    const started = performance.now();
    this.options.metrics?.add(operation, 'logicalCalls');
    try {
      return await this.rpcOnce(method, params, notification);
    } finally {
      this.options.metrics?.add(operation, 'elapsedSeconds', (performance.now() - started) / 1000);
    }
  }

  private async rpcOnce(method: string, params: JsonObject, notification = false): Promise<JsonObject> {
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
    const operation = method === 'tools/call' ? String(params.name) : method;
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      this.options.metrics?.add(operation, 'attempts');
      try {
        response = await this.request(this.options.endpoint, { method: 'POST', headers, body: JSON.stringify(body) });
        if (response.ok) {
          const received = await response.arrayBuffer();
          this.options.metrics?.add(operation, 'responseBytes', received.byteLength);
          try {
            raw = new TextDecoder('utf-8', { fatal: true }).decode(received);
          } catch {
            throw new CopperbenchError('MCP response is not valid UTF-8', 'MCP_RESPONSE_INVALID');
          }
        } else {
          try {
            const received = await response.arrayBuffer();
            this.options.metrics?.add(operation, 'responseBytes', received.byteLength);
            raw = new TextDecoder().decode(received);
          } catch {
            // A broken error body must not turn a 4xx denial into a retryable
            // transport failure, or hide the status of an uncertain write.
            raw = 'HTTP error response body was unavailable';
          }
        }
      } catch (error) {
        if (error instanceof CopperbenchError) throw error;
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
  'create_workspace', 'create_mod_element', 'update_mod_element', 'delete_mod_element', 'update_procedure', 'update_workspace_file',
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
const TASK_STATES = new Set(['queued', 'running', 'succeeded', 'failed', 'cancelled']);

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
  return template.replace(/(?<!\{)\{([A-Za-z_][A-Za-z0-9_]*)\}(?!\})/g, (match, name: string) => {
    if (!Object.hasOwn(args, name)) return match;
    const value = args[name];
    if (typeof value === 'string') return value;
    if (value === null || typeof value === 'boolean' || (typeof value === 'number' && Number.isFinite(value))) {
      return JSON.stringify(value);
    }
    return match;
  });
}
