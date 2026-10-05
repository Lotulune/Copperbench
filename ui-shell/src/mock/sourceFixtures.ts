import type { WorkspaceSourceContent, WorkspaceSourceIndex } from '../types/contract';

export function sourceFixtures(): Map<string, WorkspaceSourceContent> {
  return new Map([
    ['src/main/java/example/ExampleMod.java', 'java', 'package example;\n\npublic final class ExampleMod {\n    public static final String MOD_ID = "example";\n\n    public void onInitialize() {\n        registerItems();\n    }\n\n    private void registerItems() {}\n}\n', false],
    ['src/main/resources/fabric.mod.json', 'json', '{\n  "schemaVersion": 1,\n  "id": "example",\n  "entrypoints": { "main": ["example.ExampleMod"] }\n}\n', false],
    ['gradle.properties', 'properties', 'mod_version=1.0.0\nmod_id=example\n', false],
    ['src/main/java/example/GeneratedRegistry.java', 'java', 'package example;\n\n// Generated registry\npublic final class GeneratedRegistry {}\n', true]
  ].map(([path, language, content, generated]) => {
    const relativePath = String(path);
    return [relativePath, { relativePath, name: relativePath.split('/').at(-1)!, language: String(language),
      content: String(content), size: new TextEncoder().encode(String(content)).length, sha256: '',
      ownership: generated ? 'generated' : 'manual', editable: !generated, reasonCode: generated ? 'WORKSPACE_SOURCE_GENERATED' : null }];
  }));
}

export async function sourceHash(content: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(content));
  return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('');
}

export const sourceIndexFixture: WorkspaceSourceIndex = {
  entries: [{ id: 'entrypoint:example', kind: 'entrypoint', relativePath: 'src/main/java/example/ExampleMod.java', line: 6,
    symbol: 'example.ExampleMod', evidence: 'fabric.mod.json:4 — entrypoints.main: example.ExampleMod' }],
  scannedFiles: 4, truncated: false
};
