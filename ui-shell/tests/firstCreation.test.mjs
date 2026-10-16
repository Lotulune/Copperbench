import assert from 'node:assert/strict';
import test from 'node:test';
import { bindingMatchesWorkspace, defaultDeliveryPath, firstCreationConfirmationKey, firstCreationElement,
  firstCreationTask, firstCreationInputsConfirmed, hasSavedFirstCreationFields, isRelativeDeliveryPath, parseFirstCreationBindings,
  parseInputObservation, receiptCompatibility } from '../src/onboarding/firstCreationModel.ts';

const workspace = { id: 'workspace-id', name: 'First Creation', revision: 4, kind: 'mod', generator: { id: 'fabric-1.21.1' } };
const element = { id: 'original-block', type: 'block', name: 'trail_block', displayName: 'Trail Block', updatedAt: '2026-10-15T00:00:00Z' };
const binding = { workspaceFile: 'D:/Creations/first_creation/first_creation.mcreator', workspaceId: workspace.id,
  elementId: element.id, modId: 'first_creation', showOnOpen: true, clients: [], build: { id: 'build-id', inputRevision: 4 } };
const task = { id: 'build-id', kind: 'build', state: 'succeeded', completedAt: '2026-10-15T00:02:00Z' };

test('restoring a deleted element keeps its original identity even when another object has the same name', () => {
  const replacement = { ...element, id: 'replacement-block' };
  assert.deepEqual(firstCreationElement(binding, [replacement]), { status: 'missing', conflict: replacement });
  assert.equal(firstCreationElement(binding, [{ ...element, type: 'item' }]).status, 'changed');
  assert.equal(firstCreationElement(binding, [element]).status, 'ready');
  assert.deepEqual(firstCreationElement({ ...binding, elementId: undefined }, [replacement]), { status: 'unbound', conflict: replacement });
});

test('workspace references match the real ID or an unbound created file, without rebinding an existing ID', () => {
  assert.equal(bindingMatchesWorkspace(binding, 'another-workspace', 'D:/Creations/first_creation'), false);
  const pending = { ...binding, workspaceId: undefined };
  assert.equal(bindingMatchesWorkspace(pending, workspace.id, 'd:\\creations\\FIRST_CREATION\\'), true);
  assert.equal(bindingMatchesWorkspace(pending, workspace.id, 'D:/Creations/first_creation_copy'), false);
  assert.equal(bindingMatchesWorkspace({ ...pending, workspaceFile: '/tmp/First/first.mcreator' }, workspace.id, '/tmp/first'), false);
});

test('saved field completion comes from the committed editor, and is invalidated by newer element facts', () => {
  const editor = { element, sections: [{ fields: [
    { path: '/fields/hardness', value: 3 }, { path: '/fields/resistance', value: 6 }, { path: '/fields/hasBlockItem', value: true }
  ] }] };
  assert.equal(hasSavedFirstCreationFields(editor, element), true);
  assert.equal(hasSavedFirstCreationFields(editor, { ...element, updatedAt: '2026-10-15T00:01:00Z' }), false);
  assert.equal(hasSavedFirstCreationFields(editor, { ...element, displayName: 'Unsaved-looking name' }), false);
  assert.equal(hasSavedFirstCreationFields({ ...editor, sections: [{ fields: [{ path: '/fields/hardness', value: '3' }] }] }, element), false);
  assert.equal(hasSavedFirstCreationFields(null, element), false);
});

test('task completion, missing status, cancellation and stale input stay separate', () => {
  assert.equal(firstCreationTask(undefined, { 'build-id': task }, 4).status, 'not_started');
  assert.equal(firstCreationTask(binding.build, {}, 4).status, 'unavailable');
  assert.equal(firstCreationTask(binding.build, { 'build-id': { ...task, state: 'running' } }, 4).status, 'running');
  assert.equal(firstCreationTask(binding.build, { 'build-id': { ...task, state: 'cancelled' } }, 4).status, 'cancelled');
  assert.equal(firstCreationTask(binding.build, { 'build-id': { ...task, state: 'failed' } }, 4).status, 'failed');
  assert.equal(firstCreationTask(binding.build, { 'build-id': task }, 5).freshness, 'unknown');
  assert.equal(firstCreationTask({ id: 'build-id' }, { 'build-id': task }, 4).freshness, 'unknown');
  assert.equal(firstCreationTask(binding.build, { 'build-id': { ...task, resultObservation: 'unconfirmed_after_session_loss' } }, 4).status, 'unconfirmed');
});

test('user observations expire on saved changes, different build/client references or element identity', () => {
  const initial = firstCreationConfirmationKey(workspace, element, binding);
  for (const [project, block, references] of [
    [{ ...workspace, revision: 5 }, element, binding],
    [workspace, { ...element, updatedAt: '2026-10-15T00:05:00Z' }, binding],
    [workspace, { ...element, id: 'replacement' }, binding],
    [workspace, element, { ...binding, build: { id: 'new-build', inputRevision: 4 } }],
    [workspace, element, { ...binding, clients: [{ id: 'reopened-client', inputRevision: 4 }] }]
  ]) assert.notEqual(firstCreationConfirmationKey(project, block, references), initial);
  assert.equal(firstCreationConfirmationKey(workspace, element, binding), initial);
});

test('persistence drops completion claims and rejects damaged identity or task references', () => {
  const [restored] = parseFirstCreationBindings(JSON.stringify({ version: 1, bindings: [{ ...binding,
    projectVerified: true, gameplayPassed: true, exportComplete: true, build: { ...binding.build, state: 'succeeded', verified: true } }] }));
  assert.equal('projectVerified' in restored, false);
  assert.equal('gameplayPassed' in restored, false);
  assert.equal('state' in restored.build, false);
  assert.deepEqual(restored.clients, []);
  for (const damaged of [{ ...binding, elementId: null }, { ...binding, clients: [{}] }, { ...binding, build: { id: 'build', inputRevision: -1 } }]) {
    assert.throws(() => parseFirstCreationBindings(JSON.stringify({ version: 1, bindings: [damaged] })));
  }
  assert.throws(() => parseFirstCreationBindings('{broken'));
});

test('ordinary export uses a new nested delivery path, never a top-level JAR candidate or an escaping path', () => {
  const path = defaultDeliveryPath('First Creation', 'mod', 'request-one', 'fabric-1.21.1');
  assert.equal(path, 'build/libs/copperbench-delivery-request-one/first_creation.jar');
  assert.notEqual(path, defaultDeliveryPath('First Creation', 'mod', 'request-two', 'fabric-1.21.1'));
  assert.equal(isRelativeDeliveryPath(path), true);
  assert.equal(isRelativeDeliveryPath('exports/custom.zip'), true);
  for (const invalid of ['../outside.jar', 'build/../old.jar', 'C:/outside.jar', '/tmp/out.jar', '\\\\server\\out.jar', 'build//out.jar', 'build/.hidden/../out.jar', 'out.txt']) assert.equal(isRelativeDeliveryPath(invalid), false, invalid);
});

test('same revision is unknown without a Core input identity and an explicit matching observation', () => {
  const sha = 'a'.repeat(64), snapshot = { sha256: sha, fileCount: 2, bytes: 20, manifestPath: 'D:/Creations/first_creation/.copperbench/task-inputs/build-id.json' };
  const identified = { ...task, sourceSnapshot: snapshot };
  const checked = { workspaceId: workspace.id, observedAt: '2026-10-15T00:03:00Z', currentSha256: sha,
    tasks: { 'build-id': { status: 'current', sourceSha256: sha } } };
  assert.equal(firstCreationTask(binding.build, { 'build-id': task }, 4, checked).freshness, 'unknown');
  assert.equal(firstCreationTask(binding.build, { 'build-id': identified }, 4).freshness, 'unknown');
  assert.equal(firstCreationTask(binding.build, { 'build-id': identified }, 4, checked).freshness, 'current');
  assert.equal(firstCreationTask(binding.build, { 'build-id': identified }, 5, checked).freshness, 'older_revision');
  const changed = { ...checked, currentSha256: 'b'.repeat(64), tasks: { 'build-id': { status: 'changed', sourceSha256: sha } } };
  assert.equal(firstCreationTask(binding.build, { 'build-id': identified }, 4, changed).freshness, 'changed_inputs');
  assert.notEqual(firstCreationConfirmationKey(workspace, element, binding, checked), firstCreationConfirmationKey(workspace, element, binding, changed));
  const during = { ...checked, tasks: { 'build-id': { status: 'changed_during_task', sourceSha256: sha } } };
  assert.equal(firstCreationTask(binding.build, { 'build-id': identified }, 4, during).freshness, 'changed_during_task');
  assert.equal(firstCreationTask(binding.build, { 'build-id': identified }, 4, { ...checked, tasks: { 'build-id': { status: 'current', sourceSha256: 'c'.repeat(64) } } }).freshness, 'unknown');
});

test('user confirmation requires matching genuine input identities for the build and both last clients', () => {
  const sha = 'a'.repeat(64), references = { ...binding, clients: [{ id: 'client-one', inputRevision: 4 }, { id: 'client-two', inputRevision: 4 }] };
  const tasks = Object.fromEntries(['build-id', 'client-one', 'client-two'].map(id => [id,
    { ...task, id, sourceSnapshot: { sha256: sha, fileCount: 1, bytes: 5, manifestPath: `/tmp/${id}.json` } }]));
  const inputs = { workspaceId: workspace.id, observedAt: '2026-10-15T00:03:00Z', currentSha256: sha,
    tasks: Object.fromEntries(Object.keys(tasks).map(id => [id, { status: 'current', sourceSha256: sha }])) };
  assert.equal(firstCreationInputsConfirmed(references, tasks, 4, inputs), true);
  assert.equal(firstCreationInputsConfirmed(references, tasks, 4, null), false);
  assert.equal(firstCreationInputsConfirmed(references, tasks, 4, { ...inputs, tasks: { ...inputs.tasks, 'client-two': { status: 'changed_during_task', sourceSha256: sha } } }), false);
  assert.equal(firstCreationInputsConfirmed(references, tasks, 5, inputs), false);
  assert.equal(firstCreationConfirmationKey(workspace, element, references, inputs), firstCreationConfirmationKey(workspace, element, references, { ...inputs, observedAt: '2026-10-15T00:04:00Z' }));
});

test('native input observations retain an unavailable cause and reject a different workspace', () => {
  const raw = { workspaceId: workspace.id, observedAt: '2026-10-15T00:03:00Z', currentSha256: null,
    tasks: { 'build-id': { status: 'unknown', detail: 'WORKSPACE_SNAPSHOT_UNSAFE_PATH' } } };
  assert.equal(parseInputObservation(raw, workspace.id).tasks['build-id'].detail, 'WORKSPACE_SNAPSHOT_UNSAFE_PATH');
  assert.throws(() => parseInputObservation(raw, 'other-workspace'));
  assert.throws(() => parseInputObservation({ ...raw, currentSha256: 'not-a-digest' }, workspace.id));
});

test('historical artifact compatibility uses only its receipt and unknown metadata never becomes current-project metadata', () => {
  assert.deepEqual(receiptCompatibility({ loader: 'fabric', minecraftVersion: '1.20.1', loaderVersion: '0.18' }),
    { loader: 'fabric', minecraftVersion: '1.20.1', loaderVersion: '0.18' });
  for (const value of [undefined, null, [], { loader: '', minecraftVersion: 26.2 }])
    assert.deepEqual(receiptCompatibility(value), { loader: null, minecraftVersion: null, loaderVersion: null });
});
