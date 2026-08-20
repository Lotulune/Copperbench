import React from 'react';
import { WorkbenchProvider, useWorkbench } from './context/WorkbenchContext';
import { FramelessTitlebar } from './components/FramelessTitlebar';
import { NavRail } from './components/NavRail';
import { WorkspaceHub } from './components/WorkspaceHub';
import { ModElementsWorkbench } from './components/ModElementsWorkbench';
import { AssetsView, PluginsView } from './components/SecondaryViews';
import { HistoryView } from './components/HistoryView';
import { AIControlView } from './components/AIControlView';
import { TracksAndMigrationView } from './components/TracksAndMigrationView';
import { HelpView } from './components/HelpView';
import { TaskDrawer } from './components/TaskDrawer';
import { StatusFooter } from './components/StatusFooter';
import { CreateElementModal } from './components/CreateElementModal';
import { RevisionConflictModal } from './components/RevisionConflictModal';
import { BridgeRecoveryView } from './components/BridgeRecoveryView';
import { SchemaIncompatibleView } from './components/SchemaIncompatibleView';
import { ScenarioSwitcher } from './components/ScenarioSwitcher';
import './styles/global.css';

const ShellContent: React.FC = () => {
  const { activeView, announcement } = useWorkbench();

  return (
    <div className="app-shell" data-testid="app-shell">
      {/* Scenario / state announcements for assistive technology */}
      <div data-testid="global-announcer" aria-live="polite" className="sr-only">
        {announcement ?? ''}
      </div>

      {/* Titlebar with frameless window contract */}
      <FramelessTitlebar />

      {/* Main Layout Area */}
      <div className="app-main-layout">
        <NavRail />

        <main className="app-content-canvas">
          {activeView === 'hub' && <WorkspaceHub />}
          {activeView === 'elements' && <ModElementsWorkbench />}
          {activeView === 'tracks' && <TracksAndMigrationView />}
          {activeView === 'assets' && <AssetsView />}
          {activeView === 'history' && <HistoryView />}
          {activeView === 'ai' && <AIControlView />}
          {activeView === 'plugins' && <PluginsView />}
          {activeView === 'help' && <HelpView />}

          <TaskDrawer />
        </main>
      </div>

      {/* Global Status Footer */}
      <StatusFooter />

      {/* System Modals & Recovery Views */}
      <CreateElementModal />
      <RevisionConflictModal />
      <BridgeRecoveryView />
      <SchemaIncompatibleView />

      {/* Multi-scenario testing switcher */}
      <ScenarioSwitcher />
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <WorkbenchProvider>
      <ShellContent />
    </WorkbenchProvider>
  );
};

export default App;
