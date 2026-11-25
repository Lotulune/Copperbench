package dev.copperbench.generator.project;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.workspace.project.GradleProjectDetector;
import dev.copperbench.core.workspace.project.GradleProjectOperations;
import dev.copperbench.core.workspace.project.GradleProjectWorkspaceSession;
import dev.copperbench.generator.GradleProcessRunner;
import dev.copperbench.generator.GradleProcessRunner.ProcessResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adopted projects through the real session and task pipeline; only the Gradle processes are replaced. */
class GradleProjectTaskGatewayTest {

	private static final Path REPOSITORY = Path.of("").toAbsolutePath();
	private static final String PASSED = "<testsuite><testcase name=\"scenario\" classname=\"Behavior\"/></testsuite>";

	@TempDir Path temp;
	private Path root;
	private GradleProjectTaskGateway gateway;
	/** Directory name and arguments of every Gradle process. */
	private final List<String> processes = new CopyOnWriteArrayList<>();
	private GradleProcessRunner behavior = (directory, arguments, timeout, output) -> new ProcessResult(0, false);

	private void fabric(String loomVersion, String gradle) throws Exception {
		common("emberkit", gradle);
		write("build.gradle", "plugins {\n    id 'fabric-loom' version \"${loom_version}\"\n}\n");
		write("gradle.properties", "minecraft_version=1.21.1\nloom_version=" + loomVersion + "\n");
		write("src/main/resources/fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"emberkit\",\"name\":\"Ember Kit\"}\n");
	}

	private void neoForge() throws Exception {
		common("neokit", "gradle-9.2.1-bin.zip");
		write("build.gradle", "plugins {\n    id 'net.neoforged.moddev' version '2.0.107'\n}\n");
		write("gradle.properties", "minecraft_version=1.21.1\nmod_id=neokit\n");
		write("src/main/templates/META-INF/neoforge.mods.toml", "[[mods]]\nmodId=\"${mod_id}\"\n");
	}

	private void common(String name, String gradle) throws Exception {
		root = Files.createDirectory(temp.resolve(name));
		write("gradlew", "#!/bin/sh\nproject wrapper\n");
		write("gradlew.bat", "rem project wrapper\r\n");
		write("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https\\://services.gradle.org/distributions/" + gradle + "\n");
		write("gradle/wrapper/gradle-wrapper.jar", "project wrapper jar");
		write("settings.gradle", "rootProject.name = '" + name + "'\n");
		write("src/main/java/com/example/Mod.java", "package com.example;\n\npublic final class Mod {}\n");
	}

	private GradleProjectWorkspaceSession open() throws Exception {
		return GradleProjectWorkspaceSession.open(root, (store, folder) -> {
			gateway = new GradleProjectTaskGateway(store, ignored -> folder, REPOSITORY, Clock.systemUTC(), UUID::randomUUID,
					profile -> (directory, arguments, timeout, output) -> {
						processes.add(profile.generatorId() + " " + directory.getFileName() + " " + arguments);
						return behavior.run(directory, arguments, timeout, output);
					});
			return gateway;
		}, Clock.systemUTC(), UUID::randomUUID);
	}

	private JsonObject run(GradleProjectWorkspaceSession session, Operation operation, JsonObject payload) throws Exception {
		JsonObject started = gateway.start(session.workspaceId(), operation, payload);
		UUID taskId = UUID.fromString(started.get("id").getAsString());
		Instant deadline = Instant.now().plusSeconds(30);
		while (Instant.now().isBefore(deadline)) {
			JsonObject task = gateway.find(session.workspaceId(), taskId).orElseThrow();
			if (!List.of("running", "queued").contains(task.get("state").getAsString())) return task;
			Thread.sleep(20);
		}
		throw new AssertionError("Task timed out");
	}

	private List<String> codes(GradleProjectWorkspaceSession session, JsonObject task) {
		return gateway.diagnostics(session.workspaceId(), UUID.fromString(task.get("id").getAsString())).stream()
				.map(diagnostic -> diagnostic.get("code").getAsString()).toList();
	}

	private boolean logged(GradleProjectWorkspaceSession session, JsonObject task, String text) {
		return gateway.logs(session.workspaceId(), UUID.fromString(task.get("id").getAsString())).stream()
				.anyMatch(entry -> entry.get("text").getAsString().contains(text));
	}

	/** Builds a mod JAR where the project builds and writes a passing report where the host runs. */
	private GradleProcessRunner acceptance(String metadata) {
		return (directory, arguments, timeout, output) -> {
			if (directory.getFileName().toString().equals("host")) write(directory.resolve("run/gametest-results.xml"), PASSED);
			else jar(directory.resolve("build/libs/mod-1.0.jar"), metadata);
			return new ProcessResult(0, false);
		};
	}

	@Test void buildingUsesTheProjectAsItIsAndGeneratesNothing() throws Exception {
		fabric("1.7.4", "gradle-8.8-bin.zip");
		var before = projectFiles();
		behavior = (directory, arguments, timeout, output) -> {
			output.accept("> Task :build");
			jar(directory.resolve("build/libs/mod-1.0.jar"), "fabric.mod.json");
			return new ProcessResult(0, false);
		};
		try (var session = open()) {
			JsonObject validated = run(session, Operation.VALIDATE_WORKSPACE, new JsonObject());
			assertEquals("succeeded", validated.get("state").getAsString(), validated.toString());
			JsonObject built = run(session, Operation.BUILD_WORKSPACE, new JsonObject());
			assertEquals("succeeded", built.get("state").getAsString(), built.toString());
			assertTrue(built.has("sourceSnapshot"), "a live build still records which inputs it observed");
			assertFalse(logged(session, built, "generation completed"));
			assertEquals(List.of("fabric-1.21.1 emberkit [build]"), processes);
		}
		assertEquals(before, projectFiles());
	}

	@Test void acceptanceBuildsWithTheProjectWrapperAndTestsInAPinnedHost() throws Exception {
		fabric("1.7.4", "gradle-8.8-bin.zip");
		var before = projectFiles();
		Path[] host = new Path[1];
		var acceptance = acceptance("fabric.mod.json");
		behavior = (directory, arguments, timeout, output) -> {
			if (directory.getFileName().toString().equals("host")) {
				host[0] = directory;
				assertTrue(Files.readString(directory.resolve("gradle/wrapper/gradle-wrapper.properties")).contains("gradle-9.7.0-bin.zip"),
						"the host's pinned Loom needs the track's Gradle, not the project's 8.8");
				assertTrue(Files.readString(directory.resolve("build.gradle")).contains("1.17.19"));
				assertTrue(Files.readString(directory.resolve("src/main/java/copperbench/acceptance/AcceptanceTests.java")).contains("isModLoaded(\"emberkit\")"));
			} else {
				assertTrue(Files.readString(directory.resolve("gradle/wrapper/gradle-wrapper.properties")).contains("gradle-8.8-bin.zip"));
				write(directory.resolve(".gradle/loom-cache/remapped/minecraft.jar"), "prepared by Loom 1.7.4");
			}
			return acceptance.run(directory, arguments, timeout, output);
		};
		try (var session = open()) {
			JsonObject early = run(session, Operation.RUN_GAMETEST, new JsonObject());
			assertEquals("failed", early.get("state").getAsString());
			assertEquals(List.of("GAMETEST_NOT_CONFIGURED"), codes(session, early));
			assertEquals(List.of(), processes);

			JsonObject prepared = run(session, Operation.PREPARE_GAME_TESTS, new JsonObject());
			assertEquals("succeeded", prepared.get("state").getAsString(), prepared.toString());
			assertEquals(List.of("src/copperbenchTest/java/copperbench/acceptance/AcceptanceTests.java", "src/copperbenchTest/README.md",
					"copperbench-tests.json"), prepared.getAsJsonObject("gameTestSetup").getAsJsonArray("createdPaths").asList().stream()
					.map(path -> path.getAsString()).toList());

			JsonObject tested = run(session, Operation.RUN_GAMETEST, new JsonObject());
			assertEquals("succeeded", tested.get("state").getAsString(), tested.toString());
			assertEquals("passed", tested.getAsJsonObject("verification").get("status").getAsString());
			assertEquals(List.of("fabric-1.21.1 workspace [build]", "fabric-1.21.1 host [runGameTest]"), processes);
			assertFalse(Files.exists(host[0].resolve(".gradle/loom-cache")), "another Loom version's cache is not reused");

			JsonObject export = new JsonObject();
			export.addProperty("verifiedTaskId", tested.get("id").getAsString());
			JsonObject exported = run(session, Operation.EXPORT_WORKSPACE, export);
			assertEquals("succeeded", exported.get("state").getAsString(), exported.toString());
			assertEquals("passed_current_input", exported.getAsJsonObject("verifiedExport").get("status").getAsString());

			write("src/main/java/com/example/Mod.java", "package com.example;\n\npublic final class Mod { int changed; }\n");
			JsonObject stale = run(session, Operation.EXPORT_WORKSPACE, export);
			assertEquals(List.of("VERIFICATION_INPUT_CHANGED"), codes(session, stale));
			write("src/main/java/com/example/Mod.java", "package com.example;\n\npublic final class Mod {}\n");
		}
		var after = projectFiles();
		after.keySet().removeIf(path -> path.startsWith("src/copperbenchTest") || path.equals("copperbench-tests.json"));
		assertEquals(before, after, "acceptance changes none of the project's own files");
	}

	@Test void theHostReusesTheProjectCacheOnlyForTheSameLoom() throws Exception {
		fabric("1.17.19", "gradle-9.7.0-bin.zip");
		Path[] host = new Path[1];
		var acceptance = acceptance("fabric.mod.json");
		behavior = (directory, arguments, timeout, output) -> {
			if (directory.getFileName().toString().equals("host")) host[0] = directory;
			else write(directory.resolve(".gradle/loom-cache/remapped/minecraft.jar"), "prepared by Loom 1.17.19");
			return acceptance.run(directory, arguments, timeout, output);
		};
		try (var session = open()) {
			run(session, Operation.PREPARE_GAME_TESTS, new JsonObject());
			JsonObject tested = run(session, Operation.RUN_GAMETEST, new JsonObject());
			assertEquals("succeeded", tested.get("state").getAsString(), tested.toString());
			assertTrue(Files.isRegularFile(host[0].resolve(".gradle/loom-cache/remapped/minecraft.jar")));
		}
	}

	@Test void declaredLoomVersionsAreReadOnlyWhenTheyAreLiteral() throws Exception {
		fabric("1.17.19", "gradle-9.7.0-bin.zip");
		assertEquals("1.17.19", GradleProjectBackend.LoomVersion.declared(root).orElseThrow());
		write("build.gradle", "plugins { id(\"net.fabricmc.fabric-loom-remap\") version \"1.17.21\" }\n");
		assertEquals("1.17.21", GradleProjectBackend.LoomVersion.declared(root).orElseThrow());
		write("build.gradle", "plugins { id 'fabric-loom' version '${loom_version}-SNAPSHOT' }\n");
		assertFalse(GradleProjectBackend.LoomVersion.declared(root).filter("1.17.19"::equals).isPresent());
		write("build.gradle", "// id 'fabric-loom' version '1.17.19'\nplugins { id 'fabric-loom' version '1.7.4' }\n");
		assertTrue(GradleProjectBackend.LoomVersion.declared(root).isEmpty());
		write("build.gradle", "plugins { alias(libs.plugins.fabric.loom) }\n");
		assertTrue(GradleProjectBackend.LoomVersion.declared(root).isEmpty());
	}

	@Test void aNeoForgeServerRunIsIsolatedAndNeedsTheEulaFirst() throws Exception {
		neoForge();
		write("run/world/level.dat", "the user's world");
		List<String> observed = new CopyOnWriteArrayList<>();
		behavior = (directory, arguments, timeout, output) -> {
			for (String run : List.of("run", "runs/server")) {
				observed.add(Files.readString(directory.resolve(run + "/eula.txt")).strip());
				observed.add(Files.readString(directory.resolve(run + "/server.properties")).lines().findFirst().orElse(""));
			}
			observed.add(Boolean.toString(Files.exists(directory.resolve("run/world"))));
			return new ProcessResult(0, true);
		};
		try (var session = open()) {
			JsonObject refused = run(session, Operation.RUN_SERVER, new JsonObject());
			assertEquals("failed", refused.get("state").getAsString());
			assertEquals(List.of(), processes, "nothing starts without the accepted EULA");
			JsonObject accepted = new JsonObject();
			accepted.addProperty("eulaAccepted", true);
			JsonObject served = run(session, Operation.RUN_SERVER, accepted);
			assertEquals("succeeded", served.get("state").getAsString(), served.toString());
			assertEquals(List.of("neoforge-1.21.1 workspace [runServer]"), processes);
			assertEquals(List.of("eula=true", "server-ip=127.0.0.1", "eula=true", "server-ip=127.0.0.1", "false"), observed);
		}
		assertFalse(Files.exists(root.resolve("run/eula.txt")));
		assertEquals("the user's world", Files.readString(root.resolve("run/world/level.dat")));
	}

	@Test void neoForgeAcceptanceUsesTheSameFlow() throws Exception {
		neoForge();
		behavior = acceptance("META-INF/neoforge.mods.toml");
		try (var session = open()) {
			run(session, Operation.PREPARE_GAME_TESTS, new JsonObject());
			JsonObject tested = run(session, Operation.RUN_GAMETEST, new JsonObject());
			assertEquals("succeeded", tested.get("state").getAsString(), tested.toString());
			assertEquals(List.of("neoforge-1.21.1 workspace [build]", "neoforge-1.21.1 host [runGameTest]"), processes);
		}
	}

	@Test void tasksReadTheFilesOnDiskInsteadOfTheOpenedIdentity() throws Exception {
		fabric("1.7.4", "gradle-8.8-bin.zip");
		try (var session = open()) {
			write("gradle.properties", "minecraft_version=26.2\nloom_version=1.17.19\n");
			assertEquals(List.of(GradleProjectBackend.TARGET_CHANGED), codes(session, run(session, Operation.BUILD_WORKSPACE, new JsonObject())));
			write("gradle.properties", "minecraft_version=1.21.4\n");
			assertEquals(List.of(GradleProjectDetector.VERSION_UNSUPPORTED), codes(session, run(session, Operation.RUN_CLIENT, new JsonObject())));
			write("settings.gradle", "include ':common'\n");
			assertTrue(codes(session, run(session, Operation.VALIDATE_WORKSPACE, new JsonObject())).contains(GradleProjectDetector.MULTI_MODULE));
			assertEquals(List.of(), processes);
		}
	}

	@Test void generationAndDatagenAreNotTasksOfAnAdoptedProject() throws Exception {
		fabric("1.7.4", "gradle-8.8-bin.zip");
		try (var session = open()) {
			for (Operation operation : List.of(Operation.GENERATE_WORKSPACE, Operation.RUN_DATAGEN))
				assertThrows(IllegalArgumentException.class, () -> gateway.start(session.workspaceId(), operation, new JsonObject()), operation.name());
		}
		write("gradle.properties", "minecraft_version=1.21.4\n");
		try (var session = open()) {
			var refused = assertThrows(IllegalArgumentException.class,
					() -> gateway.start(session.workspaceId(), Operation.BUILD_WORKSPACE, new JsonObject()));
			assertEquals(GradleProjectOperations.UNSUPPORTED, refused.getMessage());
		}
	}

	@Test void theEnvironmentSeparatesTheProjectBuildFromThePinnedHost() throws Exception {
		fabric("1.7.4", "gradle-8.8-bin.zip");
		try (var session = open()) {
			JsonObject execution = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(),
					Operation.GET_WORKSPACE_ENVIRONMENT, JsonParser.parseString("{\"fieldContracts\":\"summary\"}").getAsJsonObject()))
					.data().getAsJsonObject().getAsJsonObject("execution");
			assertEquals("project", execution.get("dependencySource").getAsString());
			assertEquals("gradle-8.8-bin.zip", execution.getAsJsonObject("gradle").get("distribution").getAsString());
			assertFalse(execution.getAsJsonObject("gradle").getAsJsonObject("tasks").has("runDatagen"));
			assertFalse(execution.has("loomVersion"), "the track's versions do not describe the project's dependencies");
			JsonObject host = execution.getAsJsonObject("testing").getAsJsonObject("host");
			assertEquals("1.17.19", host.get("loomVersion").getAsString());
			assertEquals("gradle-9.7.0-bin.zip", host.get("gradleDistribution").getAsString());
		}
		write("gradle.properties", "minecraft_version=1.21.4\n");
		try (var session = open()) {
			assertEquals("succeeded", session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(),
					Operation.GET_WORKSPACE_ENVIRONMENT, new JsonObject())).status());
		}
	}

	@Test void aFinishedTaskIsFoundAfterReopening() throws Exception {
		fabric("1.7.4", "gradle-8.8-bin.zip");
		JsonObject built;
		try (var session = open()) {
			built = run(session, Operation.VALIDATE_WORKSPACE, new JsonObject());
		}
		try (var session = open()) {
			UUID taskId = UUID.fromString(built.get("id").getAsString());
			assertEquals("succeeded", gateway.find(session.workspaceId(), taskId).orElseThrow().get("state").getAsString());
			assertEquals(1, gateway.recent(session.workspaceId()).size());
			assertFalse(gateway.logs(session.workspaceId(), taskId).isEmpty());
		}
	}

	private void write(String relative, String text) throws Exception {
		write(root.resolve(relative), text);
	}

	private static void write(Path target, String text) throws java.io.IOException {
		Files.createDirectories(target.getParent());
		Files.writeString(target, text, StandardCharsets.UTF_8);
	}

	private static void jar(Path path, String metadata) throws java.io.IOException {
		Files.createDirectories(path.getParent());
		try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
			zip.putNextEntry(new ZipEntry(metadata));
			zip.write("{}".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
	}

	/** The project's own files: everything except Copperbench's metadata and build output. */
	private TreeMap<String, String> projectFiles() throws Exception {
		var result = new TreeMap<String, String>();
		try (var paths = Files.walk(root)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				String relative = root.relativize(path).toString().replace('\\', '/');
				if (relative.startsWith(".copperbench/") || relative.startsWith("build/")) continue;
				result.put(relative, Files.readString(path));
			}
		}
		return result;
	}
}
