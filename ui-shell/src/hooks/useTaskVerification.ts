import { useEffect, useState } from 'react';
import { coreBridge, safeRandomUUID } from '../bridge';
import type { TaskSummary, TaskVerificationResult } from '../types/contract';
import { legacyTaskVerification } from '../utils/taskVerification';

export function useTaskVerification(task: TaskSummary | null, workspaceId: string | undefined, revision: number | undefined, visible: boolean) {
  const v = task?.verification;
  const key = JSON.stringify([workspaceId, revision, task?.id, task?.state, task?.completedAt, task?.resultObservation,
    v?.status, v?.reasonCode, v?.minimumTests, v?.acceptanceExecuted, v?.failed, v?.sourceSnapshot?.sha256]);
  const [observation, setObservation] = useState<{ key: string; result: TaskVerificationResult } | null>(null);
  useEffect(() => {
    if (!visible || !task || task.kind !== 'run_gametest' || !workspaceId) return;
    let active = true;
    void coreBridge.sendQuery<TaskVerificationResult>({
      messageType: 'query', schemaVersion: '1.0', requestId: safeRandomUUID(), workspaceId,
      operation: 'get_task_verification', payload: { taskId: task.id, resultVersion: '1' }
    }).then(response => {
      const result = response.data;
      if (active && response.status === 'succeeded' && result?.resultVersion === '1'
          && result.taskId === task.id && result.taskState === task.state) setObservation({ key, result });
    }).catch(() => { /* Legacy/disconnected hosts retain the task's original, language-neutral observation. */ });
    return () => { active = false; };
    // Language is deliberately absent: rendering a retained message never reruns a query or task.
  }, [key, visible]);
  return task ? observation?.key === key ? observation.result : legacyTaskVerification(task) : null;
}
