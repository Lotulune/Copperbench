package net.mcreator.ui.dialogs.workspace;

import dev.copperbench.testing.McreatorTestRuntime;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

class PreviewCreationSelectionTest {
    @Test void legacyPickersExcludeTargetsThatRequireCorePreviewChoice() throws Exception {
        McreatorTestRuntime.ensureInitialized();
        SwingUtilities.invokeAndWait(() -> {
            var settings = new WorkspaceDialogs.WorkspaceDialogPanel(null, null);
            for (int index = 0; index < settings.generator.getItemCount(); index++)
                assertFalse(java.util.Set.of("fabric-26.3", "neoforge-26.3")
                        .contains(settings.generator.getItemAt(index).getGeneratorName()));
            for (String loader : java.util.List.of("fabric", "neoforge")) {
                var panel = loader.equals("fabric") ? new FabricWorkspacePanel(null) : new NeoForgeWorkspacePanel(null);
                var choices = panel.workspaceDialogPanel.generator;
                var ids = new java.util.HashSet<String>();
                for (int index = 0; index < choices.getItemCount(); index++) ids.add(choices.getItemAt(index).getGeneratorName());
                assertFalse(ids.contains(loader + "-26.3"));
                for (String version : java.util.List.of("26.2", "26.1.2", "1.21.1", "1.20.1"))
                    assertTrue(ids.contains(loader + "-" + version));
            }
        });
    }
}
