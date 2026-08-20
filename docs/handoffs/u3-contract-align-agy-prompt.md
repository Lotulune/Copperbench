You are Gemini 3.7 Flash implementing Copperbench U3 contract alignment.

Work ONLY in ui-shell/ (and ui-core/fixtures only if a mock scenario is required). Do NOT edit Java, Gradle, plugins, or src/main.

Goal: make the existing U3 UI consume the REAL Java UI-Core 1.0 wire shapes, not the Mock-invented fields. Playwright must still pass. Do not mark 26.2 / 26.1 / 1.20.1 as golden-supported; they are preview generate-ready.

Read these first:
- ui-core/schemas/v1.0/command.schema.json
- ui-core/schemas/v1.0/query.schema.json
- ui-core/schemas/v1.0/event.schema.json
- ui-core/schemas/v1.0/tracks.schema.json
- ui-core/fixtures/v1.0/tracks/version-tracks.json
- src/main/java/dev/copperbench/tracks/VersionTrackCatalog.java (read-only, toJson)
- src/main/java/dev/copperbench/migration/MigrationReport.java (read-only, toJson)
- src/main/java/dev/copperbench/core/application/WorkspaceApplicationService.java methods versionTracks, previewLoaderMigration, listPublishBatches, copyOutcome, siblingOutput
- src/main/java/dev/copperbench/assets/AssetPublishBatchService.java PublishBatch.toJson
- src/main/java/dev/copperbench/assets/ResourcePackClientLoadService.java ClientLoadPreparation.toJson
- ui-shell/src/types/contract.ts
- ui-shell/src/mock/mockBridge.ts
- ui-shell/src/components/TracksAndMigrationView.tsx
- ui-shell/src/context/WorkbenchContext.tsx
- ui-shell/e2e/u3-tracks-migration.spec.ts

Java wire facts you MUST match:

1. get_version_tracks data:
```
{ schemaVersion, latestMinecraftVersion, previousMinecraftVersion, tracks: [...], currentWorkspace: { generator: {id, loader, minecraftVersion, displayName, state}, status, reasonCode, generatable } }
```
Do NOT use currentWorkspace.generatorId. Use currentWorkspace.generator.id.

2. preview_loader_migration / preview_upstream_import data is MigrationReport.toJson():
```
{ kind, sourceGeneratorId, targetGeneratorId, sourceHash, targetDirectory|null, sourceUnchanged, complete, items: [{ path, name, type, disposition, reasonCode, nextStep }], blockedCount, lostCount, manualCount }
```
There is NO item.id, item.reason, item.replacement, summary, notes, detectedGeneratorId, canImport, sourceWorkspacePath on the Java report.
Render path/name/type/disposition/reasonCode/nextStep. Group by disposition. Empty groups may be omitted.

3. execute_loader_migration command payload:
{ clientMutationId, targetGeneratorId, outputName, userApproved }
outputName MUST match ^[a-z][a-z0-9_-]{0,63}$  (no dots). Sanitize UI defaults: workspace_neoforge-1.21.1 is INVALID because of the dot. Use workspace_neoforge_1_21_1.

Java migrates same-version first-party Fabric↔NeoForge pairs: 26.2, 26.1, 1.21.1, and 1.20.1. Cross-version targets remain complete=false; execute must NOT be shown as success if status is rejected or complete is false. Mock execute for those targets should return rejected (or committed with complete=false AND UI must not show “迁移已完成”). Never claim source corruption; show sourceUnchanged.

4. import_upstream_workspace requires Full Access on Java. Mock must deny workspace and read_only with PERMISSION_DENIED + denial.requiredProfile=full_access. UI must render that denial, not a success banner. Browse stays disabled in browser/mock. Do not prefill a real Windows path as if a file picker ran. A mock preview path constant is OK only as a clearly labelled mock fixture, not an editable filesystem field pretending to be a host picker.

5. list_publish_batches Java data is { items: [PublishBatch, ...] } NOT { batches }.
PublishBatch JSON: { id, name, sourceDirectory, outputPath, sha256, assetCount, createdAt, assets: string[] }
NOT output/status/packFormat/clientStatus.
create_publish_batch result data contains batch with those fields.
prepare_resource_pack_client result data: { zipRelativePath, sha256, packFormat, optionsRelativePath, readyForClient, clientLaunched:false, complete:true }
UI must say “已就绪，尚未启动客户端” only when clientLaunched is false.

6. Keep operations, handshake, and existing pages working. Add/adjust zh.ts keys and use t() for new user-visible strings where practical; technical IDs stay English.

7. Update e2e/u3-tracks-migration.spec.ts to the real field names and real execute semantics (26.1 execute is not a success). Keep coverage: matrix 4 tracks + statuses + reason codes; preview groups; execute disabled until confirm; publish empty and success; hub link. Existing Playwright must stay green.

8. After edits run:
   cd ui-shell && npx tsc --noEmit
   cd ui-shell && npx playwright test
   cd ui-core && npm test
Fix failures you cause.

Do not commit. Do not add CDN.
