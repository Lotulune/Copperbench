import type { AgentContextAttachment } from '../bridge/agentBridge';

/** Review the same bounded, redacted evidence that is submitted to the native bridge. */
export function agentAttachment(source: string, kind: string, content: string): AgentContextAttachment {
  const redacted = content.replace(/(authorization\s*["']?\s*[:=]\s*["']?)(?:Bearer|Basic|Token)\s+[^\s"',}]+/gi, '$1[redacted]')
    .replace(/((?:api[_-]?key|access[_-]?token|refresh[_-]?token|password|client[_-]?secret)\s*["']?\s*[:=]\s*["']?)[^\s"',}]+/gi, '$1[redacted]')
    .replace(/\bBearer\s+[A-Za-z0-9._~+/=-]+/gi, 'Bearer [redacted]')
    .replace(/\b(?:sk-|xai-)[A-Za-z0-9_-]{12,}/g, '[redacted]');
  const limit = 64 * 1024;
  return { source, kind, text: redacted.slice(0, limit), truncated: redacted.length > limit };
}
