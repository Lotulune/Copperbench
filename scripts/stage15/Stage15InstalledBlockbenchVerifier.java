import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.copperbench.assets.AssetDescriptor;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.assets.BlockbenchBridgeException;
import dev.copperbench.assets.BlockbenchExecutableLocator;
import dev.copperbench.assets.BlockbenchProcessService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Interactive clean-guest verifier for the real installed Linux Blockbench managed-process path. */
public final class Stage15InstalledBlockbenchVerifier {
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Duration START_STABILITY = Duration.ofSeconds(3);
	private static final Duration EDIT_TIMEOUT = Duration.ofMinutes(15);
	private static final String PROBE_MODEL = """
			{
			  "meta": { "format_version": "4.10", "model_format": "free", "box_uv": false },
			  "name": "stage15_blockbench_probe",
			  "model_identifier": "",
			  "visible_box": [1, 1, 0],
			  "variable_placeholders": "",
			  "resolution": { "width": 16, "height": 16 },
			  "elements": [],
			  "outliner": [],
			  "textures": []
			}
			""";

	private Stage15InstalledBlockbenchVerifier() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 2)
			throw new IllegalArgumentException(
					"Usage: Stage15InstalledBlockbenchVerifier.java <disposable-workspace-root> <result-json>");

		Path workspace = Path.of(args[0]).toAbsolutePath().normalize();
		Path resultFile = Path.of(args[1]).toAbsolutePath().normalize();
		require(Files.isDirectory(workspace), "Disposable workspace root does not exist: " + workspace);

		Path executable = BlockbenchExecutableLocator.locate();
		require(executable != null, "No runnable Blockbench installation was found by the production Linux locator");
		require(Files.isRegularFile(executable) && Files.isExecutable(executable),
				"Located Blockbench path is not an executable file: " + executable);

		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String relativePath = "assets/copperbench/models/stage15_blockbench_probe_" + suffix + ".bbmodel";
		Path model = workspace.resolve(relativePath).normalize();
		require(model.startsWith(workspace), "Probe model escaped the disposable workspace");
		Files.createDirectories(model.getParent());
		Files.writeString(model, PROBE_MODEL, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE);

		AssetWorkspaceService assets = new AssetWorkspaceService(workspace);
		AssetDescriptor asset = assets.findByRelativePath(relativePath)
				.orElseThrow(() -> new IllegalStateException("Probe .bbmodel was not indexed as a workspace asset"));
		String leaseConflictCode;
		BlockbenchProcessService.Snapshot opened;
		BlockbenchProcessService.Snapshot exited;
		try (BlockbenchProcessService service = new BlockbenchProcessService(assets, executable);
				BlockbenchProcessService contender = new BlockbenchProcessService(new AssetWorkspaceService(workspace),
						executable)) {
			BlockbenchProcessService.Snapshot ready = service.status();
			require(ready.state() == BlockbenchProcessService.State.READY,
					"Production Blockbench service is not ready: " + ready.state() + " / " + ready.diagnosticCode());

			opened = service.openAsset(asset.id());
			require(opened.state() == BlockbenchProcessService.State.RUNNING,
					"Real Blockbench did not enter RUNNING state: " + opened.state());
			require(opened.processId() != null && ProcessHandle.of(opened.processId()).map(ProcessHandle::isAlive).orElse(false),
					"Managed Blockbench process is not alive after launch");

			try {
				contender.openAsset(asset.id());
				throw new IllegalStateException("A second managed Blockbench service acquired an already leased asset");
			} catch (BlockbenchBridgeException exception) {
				require("BLOCKBENCH_ASSET_LEASED".equals(exception.code()),
						"Unexpected second-open diagnostic: " + exception.code());
				leaseConflictCode = exception.code();
			}

			Thread.sleep(START_STABILITY.toMillis());
			BlockbenchProcessService.Snapshot stable = service.status();
			require(stable.state() == BlockbenchProcessService.State.RUNNING,
					"Managed Blockbench process did not survive the startup stability window: " + stable.state());

			System.out.println("Stage 15 Blockbench gate: a real Blockbench window should now show " + model.getFileName() + ".");
			System.out.println("Add or rename a visible model element, save the .bbmodel, then close Blockbench normally.");
			System.out.println("Do not edit the probe file from another program; this gate is specifically for Blockbench.");

			Instant deadline = Instant.now().plus(EDIT_TIMEOUT);
			exited = stable;
			while (Instant.now().isBefore(deadline)) {
				Thread.sleep(500);
				exited = service.status();
				if (exited.state() == BlockbenchProcessService.State.EXITED) break;
				require(exited.state() == BlockbenchProcessService.State.RUNNING,
						"Blockbench managed task entered unexpected state: " + exited.state());
			}
		}

		require(exited.state() == BlockbenchProcessService.State.EXITED,
				"Timed out waiting for the tester to save and close Blockbench normally");
		require(exited.exitCode() != null && exited.exitCode() == 0,
				"Blockbench exited abnormally: " + exited.exitCode());
		require(exited.currentSha256() != null && !exited.currentSha256().equals(exited.openedSha256()),
				"The .bbmodel did not change; make a visible edit in Blockbench and save before closing");
		require("ASSET_CHANGED_EXTERNALLY".equals(exited.diagnosticCode()),
				"Production change detector did not report the saved Blockbench edit: " + exited.diagnosticCode());

		JsonObject result = new JsonObject();
		result.addProperty("schemaVersion", "1.0");
		result.addProperty("status", "passed");
		result.addProperty("probe", "installed-blockbench-managed-process");
		result.addProperty("executable", executable.toString());
		result.addProperty("blockbenchVersion", opened.blockbenchVersion());
		result.addProperty("assetRelativePath", relativePath);
		result.addProperty("processId", opened.processId());
		result.addProperty("exitCode", exited.exitCode());
		result.addProperty("openedSha256", exited.openedSha256());
		result.addProperty("currentSha256", exited.currentSha256());
		result.addProperty("leaseConflictCode", leaseConflictCode);
		result.addProperty("changeDiagnosticCode", exited.diagnosticCode());
		result.addProperty("managedProcessStable", true);
		result.addProperty("changeDetected", true);
		result.addProperty("formalSupportClaim", false);
		Files.createDirectories(resultFile.getParent());
		Files.writeString(resultFile, JSON.toJson(result) + System.lineSeparator(), StandardCharsets.UTF_8);
		System.out.println("Stage 15 installed Blockbench gate passed: " + resultFile);
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new IllegalStateException(message);
	}
}
