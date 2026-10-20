import type { TaskSummary } from '../types/contract';

export const taskTone: Record<TaskSummary['state'], string> = {
  queued: 'blue', running: 'copper', succeeded: 'green', failed: 'red', cancelled: 'neutral'
};
export const newestTasks = (tasks: TaskSummary[]) => [...tasks].sort((a, b) =>
  Date.parse(b.completedAt ?? b.startedAt) - Date.parse(a.completedAt ?? a.startedAt) || a.id.localeCompare(b.id));
