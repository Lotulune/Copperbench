import { coreBridge } from './index';

/** A confirmation read may finish after navigation, but must never rebase an old
 * object session with another workspace's revision or editor dispatch. */
export async function confirmEditorRead<T>(workspaceId: string, refresh: () => Promise<void>, read: () => Promise<T | null>) {
  const workspace = () => {
    const current = coreBridge.getState().workbench?.workspace;
    if (!current || current.id !== workspaceId) throw new Error('The workspace changed during confirmation.');
    return current;
  };
  workspace();
  await refresh();
  const revision = workspace().revision;
  const editor = await read();
  if (workspace().revision !== revision) throw new Error('The workspace revision changed during confirmation.');
  if (!editor) throw new Error('The editor projection is unavailable.');
  return { editor, revision };
}
