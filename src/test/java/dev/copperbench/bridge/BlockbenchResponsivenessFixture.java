package dev.copperbench.bridge;

import com.google.gson.JsonObject;
import dev.copperbench.assets.BlockbenchProcessFixture;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.history.*;
import dev.copperbench.shell.CopperbenchProductShell;
import dev.copperbench.shell.UiLocalePreferences;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.io.UserFolderManager;
import net.mcreator.ui.chromium.CefUtils;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.util.TerribleModuleHacks;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Manual native acceptance: a real JCEF workbench with a controllably slow backup and a fake editor process. */
public final class BlockbenchResponsivenessFixture {
	public static void main(String[] args) throws Exception {
		Path evidence = Path.of(args[0]).toAbsolutePath();
		Path root = Files.createDirectories(evidence.resolve("workspace"));
		Path model = root.resolve("models/slow_launch.bbmodel");
		Files.createDirectories(model.getParent());
		Files.writeString(model, "{\"meta\":{\"model_format\":\"java_block\"},\"elements\":[],\"textures\":[]}");
		UserFolderManager.createUserFolderIfNotExists();
		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();
		McreatorTestRuntime.ensureInitialized();
		UiLocalePreferences.save("zh");
		Clock clock = Clock.systemUTC();
		UUID id = UUID.randomUUID();
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(id, "Blockbench 响应验证", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		var history = new GatedHistory(JGitLocalHistoryService.open(root, clock), evidence);
		var service = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID),
				WorkspaceMutationGateway.noOp(), history, null, ignored -> root, clock, UUID::randomUUID);
		var editor = new BlockbenchProcessFixture();
		var processService = editor.service(root, service.blockbenchEditLifecycle(id));
		SwingUtilities.invokeAndWait(() -> {
			WebView web = new WebView(CopperbenchProductShell.UI_URL);
			var core = web.attachCoreBridge(id, new WorkspaceEntryAdapter(service,
					new UiCore.RequestContext(UiCore.Actor.UI, UiCore.PermissionProfile.WORKSPACE)));
			var blockbench = JcefBlockbenchBridgeTransport.attach(web, processService);
			JFrame frame = new JFrame("Copperbench Blockbench Responsiveness Acceptance");
			frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			frame.setContentPane(web);
			frame.setSize(1360, 900);
			frame.setLocationRelativeTo(null);
			frame.addWindowListener(new WindowAdapter() {
				@Override public void windowClosing(WindowEvent event) {
					blockbench.close();
					core.close();
					web.close();
					frame.dispose();
					Thread.ofVirtual().start(() -> {
						try {
							processService.close();
							history.close();
							CefUtils.close();
							Files.writeString(evidence.resolve("normal-exit.txt"), "closed");
							System.exit(0);
						} catch (Exception exception) { exception.printStackTrace(); System.exit(1); }
					});
				}
			});
			frame.setVisible(true);
			web.forceLoad();
		});
	}

	private record GatedHistory(LocalHistoryService delegate, Path evidence) implements LocalHistoryService {
		@Override public synchronized RecoveryPoint createRecoveryPoint(RecoveryPointRequest request) throws LocalHistoryException {
			try {
				Files.writeString(evidence.resolve("backup-waiting.txt"), request.label());
				long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
				while (!Files.exists(evidence.resolve("release-backup.txt"))) {
					if (System.nanoTime() > deadline) throw new IllegalStateException("Acceptance backup was not released");
					Thread.sleep(100);
				}
				RecoveryPoint result = delegate.createRecoveryPoint(request);
				Files.writeString(evidence.resolve("backup-completed.txt"), result.id());
				return result;
			} catch (Exception exception) { throw new LocalHistoryException("Acceptance backup failed", exception); }
		}
		@Override public synchronized List<RecoveryPoint> listRecoveryPoints() throws LocalHistoryException { return delegate.listRecoveryPoints(); }
		@Override public synchronized String currentRecoveryPointId() throws LocalHistoryException { return delegate.currentRecoveryPointId(); }
		@Override public synchronized List<WorkspaceChange> compare(String from, String to) throws LocalHistoryException { return delegate.compare(from, to); }
		@Override public synchronized List<WorkspaceChange> previewRestore(String id) throws LocalHistoryException { return delegate.previewRestore(id); }
		@Override public synchronized RestoreResult restore(String id) throws LocalHistoryException { return delegate.restore(id); }
		@Override public void close() { delegate.close(); }
	}
}
