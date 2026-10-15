import { useCallback, useState, type SetStateAction } from 'react';
import type { ModElementType } from '../types/contract';

export type SortOption = 'updated_desc' | 'updated_asc' | 'name_asc' | 'name_desc' | 'type_asc';
interface ElementListState {
  searchQuery: string; selectedType: ModElementType | 'all'; selectedState: 'all' | 'valid' | 'draft' | 'invalid';
  viewMode: 'grid' | 'table'; sortBy: SortOption; pageSize: number; currentPage: number;
}
const sessions = new Map<string, { state: ElementListState; scrollTop: number }>();
const defaults = (): ElementListState => ({ searchQuery: '', selectedType: 'all', selectedState: 'all', viewMode: 'grid', sortBy: 'updated_desc', pageSize: 24, currentPage: 1 });

/** Session-local browsing context, scoped to a workspace; editor drafts remain separate. */
export function useElementListSession(workspaceId: string) {
  if (!sessions.has(workspaceId)) sessions.set(workspaceId, { state: defaults(), scrollTop: 0 });
  const session = sessions.get(workspaceId)!;
  const [state, setState] = useState(session.state);
  const set = <K extends keyof ElementListState>(key: K, value: SetStateAction<ElementListState[K]>) => {
    setState(previous => {
      const next = { ...previous, [key]: typeof value === 'function' ? value(previous[key]) : value };
      if (key !== 'currentPage' && key !== 'viewMode') { next.currentPage = 1; session.scrollTop = 0; }
      session.state = next;
      return next;
    });
  };
  const restoreScroll = useCallback((element: HTMLDivElement | null) => {
    if (element) element.scrollTop = session.scrollTop;
  }, [session]);
  return { state, set, restoreScroll, rememberScroll: (value: number) => { session.scrollTop = value; } };
}
