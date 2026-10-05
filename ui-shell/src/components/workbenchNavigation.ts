import { Box, Bot, Database, FileCode2, FolderOpen, GitBranch, HelpCircle, LayoutDashboard, Network, Plug, Plus, Settings, Terminal, Waypoints } from 'lucide-react';
import type { NavView } from '../context/WorkbenchContext';
import { uiText } from '../i18n';

export const OPEN_WORKBENCH_SEARCH_EVENT = 'copperbench:open-workbench-search';

export function workbenchNavigation() {
  return [
    { id: 'hub', label: uiText('总览', 'Overview'), icon: LayoutDashboard, group: 'project' },
    { id: 'elements', label: uiText('模组元素', 'Mod elements'), icon: Box, group: 'project' },
    { id: 'assets', label: uiText('资产与模型', 'Assets & models'), icon: FolderOpen, group: 'project' },
    { id: 'relations', label: uiText('关系图', 'Relationships'), icon: Network, group: 'project' },
    { id: 'source', label: uiText('源码', 'Source'), icon: FileCode2, group: 'project' },
    { id: 'data', label: uiText('变量与数据', 'Variables & data'), icon: Database, group: 'project' },
    { id: 'history', label: uiText('本地历史', 'Local history'), icon: GitBranch, group: 'project' },
    { id: 'tracks', label: uiText('版本与迁移', 'Versions & migration'), icon: Waypoints, group: 'tools' },
    { id: 'ai', label: uiText('AI 与 MCP', 'AI & MCP'), icon: Bot, group: 'tools' },
    { id: 'python', label: uiText('Python 工作台', 'Python workbench'), icon: Terminal, group: 'tools' },
    { id: 'plugins', label: uiText('插件中心', 'Plugins'), icon: Plug, group: 'tools' },
    { id: 'help', label: uiText('帮助与关于', 'Help & about'), icon: HelpCircle, group: 'utility' },
    { id: 'settings', label: uiText('设置', 'Settings'), icon: Settings, group: 'utility' },
    { id: 'new-workspace', label: uiText('新建工作区', 'New workspace'), icon: Plus, group: 'utility' }
  ] satisfies Array<{ id: NavView; label: string; icon: typeof Box; group: string }>;
}
