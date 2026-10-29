import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { WorkbenchProvider, useWorkbench } from './context/WorkbenchContext';
import { FramelessTitlebar } from './components/FramelessTitlebar';
import { NavRail } from './components/NavRail';
import { WorkspaceHub } from './components/WorkspaceHub';
import { ModElementsWorkbench } from './components/ModElementsWorkbench';
import { AssetsView, PluginsView } from './components/SecondaryViews';
import { CreatorDataView } from './components/CreatorDataView';
import { HistoryView } from './components/HistoryView';
import { AIControlView } from './components/AIControlView';
import { TracksAndMigrationView } from './components/TracksAndMigrationView';
import { NewWorkspaceView } from './components/NewWorkspaceView';
import { HelpView } from './components/HelpView';
import { TaskDrawer } from './components/TaskDrawer';
import { StatusFooter } from './components/StatusFooter';
import { CreateElementModal } from './components/CreateElementModal';
import { RevisionConflictModal } from './components/RevisionConflictModal';
import { BridgeRecoveryView } from './components/BridgeRecoveryView';
import { SchemaIncompatibleView } from './components/SchemaIncompatibleView';
import { StartupFailureView } from './components/StartupFailureView';
import { ScenarioSwitcher } from './components/ScenarioSwitcher';
import { PythonWorkbench } from './components/PythonWorkbench';
import { PythonContextSync } from './components/PythonContextSync';
import { RelationshipGraphView } from './components/RelationshipGraphView';
import { SourceWorkbench } from './components/SourceWorkbench';
import { SettingsView } from './components/SettingsView';
import { WorkbenchNavigationBar } from './components/WorkbenchNavigationBar';
import { FirstCreationProvider } from './onboarding/FirstCreationContext';
import { FirstCreationGuide } from './components/FirstCreationGuide';
import { DeliveryDialog } from './components/DeliveryDialog';
import { OpenWorkspaceDialog } from './components/WorkspaceEntryActions';
import { FeedbackHost } from './components/FeedbackHost';
import { WorkbenchModeProvider, useWorkbenchMode } from './context/WorkbenchModeContext';
import { SimpleCreationView } from './components/SimpleCreationView';
import { AgentProvider } from './context/AgentContext';
import { ProfessionalAgentPanel, AgentPanelToggle } from './components/ProfessionalAgentPanel';
import './styles/global.css';
import './styles/workbench.css';
import './styles/motion.css';
import './components/simpleCreation.css';

const ShellContent: React.FC = () => {
  const { activeView, announcement, state, sourceFocusRequest } = useWorkbench();
  const { mode } = useWorkbenchMode();
  const simple = mode === 'simple';
  const [settingsOpened, setSettingsOpened] = useState(activeView === 'settings');
  const [newWorkspaceOpened, setNewWorkspaceOpened] = useState(activeView === 'new-workspace');
  const contentRef = useRef<HTMLDivElement>(null);
  const previousView = useRef(activeView);
  useLayoutEffect(() => {
    if (previousView.current === activeView) return;
    previousView.current = activeView;
    if (!contentRef.current || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    // Keep persistent editors mounted; only acknowledge the newly selected surface.
    const transition = contentRef.current.animate([{ opacity: 0.55 }, { opacity: 1 }],
      { duration: 150, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' });
    return () => transition.cancel();
  }, [activeView]);
  useEffect(() => { if (activeView === 'settings') setSettingsOpened(true); }, [activeView]);
  useEffect(() => { if (activeView === 'new-workspace') setNewWorkspaceOpened(true); }, [activeView]);

  return (
    <div className="app-shell" data-testid="app-shell" data-workbench-mode={mode}>
      {/* Scenario / state announcements for assistive technology */}
      <div data-testid="global-announcer" aria-live="polite" className="sr-only">
        {announcement ?? ''}
      </div>

      {/* Titlebar with frameless window contract */}
      <FramelessTitlebar />

      {/* Main Layout Area */}
      <div className="app-main-layout">
        {!simple && <NavRail />}

        <main className="app-content-canvas" data-active-view={activeView}>
          {!simple && <div className="professional-navigation"><WorkbenchNavigationBar key={`navigation-${state.workbench?.workspace.id}`} /><AgentPanelToggle /></div>}
          <SimpleCreationView active={simple}>
          <div className="workbench-view-content" ref={contentRef}>
          {activeView === 'hub' && <WorkspaceHub />}
          {activeView === 'elements' && <ModElementsWorkbench key={`elements-${state.workbench?.workspace.id}`} />}
          {activeView === 'relations' && <RelationshipGraphView key={`relations-${state.workbench?.workspace.id}`} />}
          {activeView === 'data' && <CreatorDataView />}
          {activeView === 'tracks' && <TracksAndMigrationView />}
          {(newWorkspaceOpened || activeView === 'new-workspace') && <div className="workbench-persistent-view" hidden={activeView !== 'new-workspace'}><NewWorkspaceView active={activeView === 'new-workspace'} /></div>}
          {activeView === 'assets' && <AssetsView />}
          {activeView === 'history' && <HistoryView />}
          {activeView === 'ai' && <AIControlView />}
          {activeView === 'plugins' && <PluginsView />}
          {activeView === 'help' && <HelpView />}
          {(settingsOpened || activeView === 'settings') && <div className="workbench-persistent-view" hidden={activeView !== 'settings'}><SettingsView /></div>}
          {state.workbench && <SourceWorkbench key={`source-${state.workbench.workspace.id}`} active={activeView === 'source'}
            focusRequest={sourceFocusRequest?.workspaceId === state.workbench.workspace.id ? sourceFocusRequest : null} />}
          {state.workbench && <PythonWorkbench key={`python-${state.workbench.workspace.id}`} visible={activeView === 'python'} />}
          </div>
          </SimpleCreationView>

          <TaskDrawer />
        </main>
        <ProfessionalAgentPanel />
        <AgentPanelToggle activity />
      </div>

      {/* Global Status Footer */}
      <StatusFooter />

      {/* System Modals & Recovery Views */}
      <CreateElementModal />
      <RevisionConflictModal />
      <BridgeRecoveryView />
      <StartupFailureView />
      <SchemaIncompatibleView />
      <FirstCreationGuide />
      <DeliveryDialog />
      <OpenWorkspaceDialog />
      <FeedbackHost />

      {/* Multi-scenario testing switcher */}
      <ScenarioSwitcher />
      <PythonContextSync />
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <WorkbenchProvider>
      <WorkbenchModeProvider><AgentProvider><FirstCreationProvider><ShellContent /></FirstCreationProvider></AgentProvider></WorkbenchModeProvider>
    </WorkbenchProvider>
  );
};

export default App;
