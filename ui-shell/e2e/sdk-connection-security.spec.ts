import { expect, test } from '@playwright/test';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { CopperbenchError, readWorkspaceConnection } from '../../sdk/typescript/copperbench';

const withConnection = (url: string, callback: (root: string) => void) => {
  const root = mkdtempSync(join(tmpdir(), 'copperbench-sdk-'));
  try {
    const metadataRoot = join(root, '.copperbench');
    mkdirSync(metadataRoot, { recursive: true });
    writeFileSync(join(metadataRoot, 'mcp-connection.json'), JSON.stringify({
      schemaVersion: '1.0',
      status: 'listening',
      url,
      workspaceId: '00000000-0000-4000-8000-000000000091'
    }), 'utf8');
    callback(root);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
};

test('SDK accepts only the exact local MCP endpoint shape', () => {
  withConnection('http://127.0.0.1:61999/mcp', (root) => {
    expect(readWorkspaceConnection(root).url).toBe('http://127.0.0.1:61999/mcp');
  });

  const malicious = [
    'http://127.0.0.1:80@attacker.example/mcp',
    'http://user@127.0.0.1:61999/mcp',
    'https://127.0.0.1:61999/mcp',
    'http://127.0.0.1/mcp',
    'http://127.0.0.1:99999/mcp',
    'http://127.0.0.1:61999/mcp?redirect=attacker.example',
    'http://127.0.0.1:61999/mcp#fragment',
    'http://127.0.0.1:61999/mcp/extra'
  ];

  for (const url of malicious) {
    withConnection(url, (root) => {
      expect(() => readWorkspaceConnection(root)).toThrow(CopperbenchError);
      try {
        readWorkspaceConnection(root);
      } catch (error) {
        expect((error as CopperbenchError).code).toBe('MCP_CONNECTION_FILE_INVALID');
      }
    });
  }
});
