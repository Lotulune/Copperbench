import { ScenarioDefinition } from '../types/contract';

// Single source of truth: the canonical fixtures shipped by ui-core
// (ui-core/fixtures/v1.0/scenarios). Do NOT hand-copy scenario data here;
// re-exporting keeps the mock bridge and the JSON Schema validator aligned.
import bridgeRecovery from '../../../ui-core/fixtures/v1.0/scenarios/bridge-recovery.json';
import buildRunning from '../../../ui-core/fixtures/v1.0/scenarios/build-running.json';
import elementCreated from '../../../ui-core/fixtures/v1.0/scenarios/element-created.json';
import emptyWorkspace from '../../../ui-core/fixtures/v1.0/scenarios/empty-workspace.json';
import externalProcessExited from '../../../ui-core/fixtures/v1.0/scenarios/external-process-exited.json';
import loadingWorkbench from '../../../ui-core/fixtures/v1.0/scenarios/loading-workbench.json';
import offline from '../../../ui-core/fixtures/v1.0/scenarios/offline.json';
import partialCapability from '../../../ui-core/fixtures/v1.0/scenarios/partial-capability.json';
import permissionDenied from '../../../ui-core/fixtures/v1.0/scenarios/permission-denied.json';
import ready from '../../../ui-core/fixtures/v1.0/scenarios/ready.json';
import revisionConflict from '../../../ui-core/fixtures/v1.0/scenarios/revision-conflict.json';
import schemaIncompatible from '../../../ui-core/fixtures/v1.0/scenarios/schema-incompatible.json';
import validationFailed from '../../../ui-core/fixtures/v1.0/scenarios/validation-failed.json';
import historyReady from '../../../ui-core/fixtures/v1.0/scenarios/history-ready.json';
import approvalRequired from '../../../ui-core/fixtures/v1.0/scenarios/approval-required.json';

const fixtureList: unknown[] = [
  bridgeRecovery,
  buildRunning,
  elementCreated,
  emptyWorkspace,
  externalProcessExited,
  loadingWorkbench,
  offline,
  partialCapability,
  permissionDenied,
  ready,
  revisionConflict,
  schemaIncompatible,
  validationFailed,
  historyReady,
  approvalRequired
];

export const SCENARIOS: Record<string, ScenarioDefinition> = Object.fromEntries(
  fixtureList.map((fixture) => {
    const scenario = fixture as ScenarioDefinition;
    return [scenario.scenarioId, scenario];
  })
);
