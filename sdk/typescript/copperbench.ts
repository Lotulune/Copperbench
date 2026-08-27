/** Minimal dependency-free Copperbench MCP client for Node.js 22+. */

export type JsonObject = Record<string, unknown>;

export interface CopperbenchClientOptions {
  endpoint: string;
  token: string;
  workspaceId: string;
  fetch?: typeof fetch;
  maxTransportRetries?: number;
  retryBackoffMs?: number;
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

  public createRegistryEntry(args: JsonObject): Promise<JsonObject> {
    return this.callTool('create_registry_entry', args);
  }

  public listWorkspaceRegistries(args: JsonObject = {}): Promise<JsonObject> {
    return this.callTool('list_workspace_registries', args);
  }

  public planWorkspaceChanges(args: JsonObject): Promise<JsonObject> {
    return this.callTool('plan_workspace_changes', args);
  }

  public previewWorkspacePlan(plan: JsonObject): Promise<JsonObject> {
    return this.callTool('preview_workspace_plan', { plan });
  }

  public applyWorkspacePlan(args: JsonObject): Promise<JsonObject> {
    return this.callTool('apply_workspace_plan', args);
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

  public cancelTask(taskId: string, expectedRevision: number): Promise<JsonObject> {
    return this.callTool('cancel_task', { taskId, expectedRevision });
  }

  public createRecoveryPoint(label: string, expectedRevision: number): Promise<JsonObject> {
    return this.callTool('create_recovery_point', { label, expectedRevision });
  }

  public restoreRecoveryPoint(recoveryPointId: string, expectedRevision: number): Promise<JsonObject> {
    return this.callTool('restore_recovery_point', {
      recoveryPointId,
      expectedRevision
    });
  }

  public callTool(name: string, argumentsValue: JsonObject): Promise<JsonObject> {
    return this.rpc('tools/call', { name, arguments: argumentsValue }).then((result) => {
      const content = Array.isArray(result.content) ? result.content : [];
      const textItem = content.find((item) => item && typeof item === 'object' && (item as JsonObject).type === 'text') as JsonObject | undefined;
      const text = textItem?.text;
      if (typeof text !== 'string') throw new CopperbenchError(`Tool ${name} returned no JSON content`);
      const value = JSON.parse(text) as JsonObject;
      if (value.status === 'rejected' || value.status === 'failed') {
        const diagnostic = Array.isArray(value.diagnostics) ? value.diagnostics[0] as JsonObject | undefined : undefined;
        throw new CopperbenchError(`Tool ${name} was rejected`, typeof diagnostic?.code === 'string' ? diagnostic.code : undefined, value);
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
    const maxRetries = Math.max(0, Math.min(2, this.options.maxTransportRetries ?? 2));
    const backoffMs = Math.max(0, this.options.retryBackoffMs ?? 100);
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        response = await this.request(this.options.endpoint, { method: 'POST', headers, body: JSON.stringify(body) });
      } catch (error) {
        if (attempt >= maxRetries) throw new CopperbenchError('MCP transport failed', 'MCP_TRANSPORT_FAILED', error);
        await new Promise((resolve) => setTimeout(resolve, backoffMs * 2 ** attempt));
        continue;
      }
      if (response.ok || ![502, 503, 504].includes(response.status) || attempt >= maxRetries) break;
      await response.arrayBuffer();
      await new Promise((resolve) => setTimeout(resolve, backoffMs * 2 ** attempt));
    }
    if (!response) throw new CopperbenchError('MCP transport failed', 'MCP_TRANSPORT_FAILED');
    if (!response.ok) throw new CopperbenchError(`MCP HTTP ${response.status}`, `HTTP_${response.status}`, await response.text());
    this.sessionId ??= response.headers.get('mcp-session-id') ?? undefined;
    if (notification) return {};
    const envelope = parseRpcEnvelope(await response.text());
    if (envelope.error) {
      const error = envelope.error as JsonObject;
      throw new CopperbenchError(String(error.message ?? 'MCP request failed'), String(error.code ?? 'MCP_ERROR'), error);
    }
    return (envelope.result ?? {}) as JsonObject;
  }
}

function parseRpcEnvelope(body: string): JsonObject {
  const trimmed = body.trim();
  if (trimmed.startsWith('{')) return JSON.parse(trimmed) as JsonObject;
  const line = trimmed.split(/\r?\n/).find((value) => value.startsWith('data: '));
  if (!line) throw new CopperbenchError('MCP response did not contain a JSON-RPC envelope', 'MCP_RESPONSE_INVALID', body);
  return JSON.parse(line.slice(6)) as JsonObject;
}
