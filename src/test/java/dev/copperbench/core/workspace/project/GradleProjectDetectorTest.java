package dev.copperbench.core.workspace.project;

import dev.copperbench.core.workspace.project.GradleProjectDetector.NotAProjectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleProjectDetectorTest {

	private static final String FABRIC_JSON = """
			{"schemaVersion":1,"id":"emberkit","version":"${version}","name":"Ember Kit","depends":{"minecraft":"~${minecraft_version}"}}
			""";

	@TempDir Path root;

	private void write(String relative, String text) throws Exception {
		Path target = root.resolve(relative);
		Files.createDirectories(target.getParent());
		Files.writeString(target, text, StandardCharsets.UTF_8);
	}

	private void wrapper() throws Exception {
		write("gradlew", "#!/bin/sh\n");
		write("gradlew.bat", "@echo off\r\n");
		write("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.0-bin.zip\n");
		write("gradle/wrapper/gradle-wrapper.jar", "jar");
	}

	/** The layout an agent without Copperbench produced in the comparison runs. */
	private void fabric(String minecraft) throws Exception {
		wrapper();
		write("settings.gradle", "rootProject.name = 'emberkit'\n");
		write("build.gradle", "dependencies {\n    minecraft \"com.mojang:minecraft:${project.minecraft_version}\"\n}\n");
		write("gradle.properties", "# Fabric properties\nminecraft_version=" + minecraft + "\nloader_version=0.19.3\n");
		write("src/main/resources/fabric.mod.json", FABRIC_JSON);
	}

	private List<String> codes(GradleProjectIdentity identity) {
		return identity.problems().stream().map(GradleProjectIdentity.Problem::code).toList();
	}

	@Test void fabricTemplateIsIdentifiedWithoutTouchingTheProject() throws Exception {
		fabric("1.21.1");
		var before = snapshot();
		GradleProjectIdentity identity = GradleProjectDetector.inspect(root);
		assertEquals(new GradleProjectIdentity("Ember Kit", "emberkit", "fabric", "1.21.1", "fabric-1.21.1", List.of()), identity);
		assertTrue(identity.supported());
		assertEquals(before, snapshot(), "identification only reads the project");
	}

	@Test void aLiteralMinecraftCoordinateIsAnExactVersion() throws Exception {
		// examples/agent-native/survey-pulse was written by an agent without gradle.properties versions.
		GradleProjectIdentity identity = GradleProjectDetector.inspect(Path.of("examples/agent-native/survey-pulse"));
		assertEquals(List.of(), codes(identity));
		assertEquals("fabric-1.21.1", identity.generatorId());
		assertEquals("survey_pulse", identity.modId());
	}

	@Test void twoDifferentLiteralCoordinatesAreNotGuessedBetween() throws Exception {
		fabric("1.21.1");
		write("gradle.properties", "org.gradle.parallel=true\n");
		write("build.gradle", "dependencies { minecraft 'com.mojang:minecraft:1.21.1'\n testImplementation \"com.mojang:minecraft:1.20.1\" }\n");
		assertEquals(List.of(GradleProjectDetector.VERSION_UNKNOWN), codes(GradleProjectDetector.inspect(root)));
	}

	@Test void aVersionRangeIsNotTakenAsTheVersion() throws Exception {
		fabric("1.21.1");
		write("gradle.properties", "minecraft_version=[1.21.1,1.22)\n");
		GradleProjectIdentity identity = GradleProjectDetector.inspect(root);
		assertEquals(List.of(GradleProjectDetector.VERSION_UNKNOWN), codes(identity));
		assertEquals(GradleProjectIdentity.UNKNOWN, identity.minecraftVersion());
		assertNull(identity.generatorId());
		assertFalse(identity.supported());
	}

	@Test void aVersionOutsideTheTracksStaysIdentifiedButUnsupported() throws Exception {
		fabric("1.21.4");
		GradleProjectIdentity identity = GradleProjectDetector.inspect(root);
		assertEquals(List.of(GradleProjectDetector.VERSION_UNSUPPORTED), codes(identity));
		assertEquals("1.21.4", identity.minecraftVersion());
		assertEquals("fabric", identity.loader());
	}

	@Test void neoForgeTemplateResolvesItsPlaceholders() throws Exception {
		wrapper();
		write("build.gradle", "plugins { id 'net.neoforged.moddev' version '2.0.141' }\n");
		write("gradle.properties", "minecraft_version=1.21.1\nneo_version=21.1.232\nmod_id=examplemod\nmod_name=Example Mod\n");
		write("src/main/templates/META-INF/neoforge.mods.toml", """
				modLoader="javafml" #mandatory
				[[mods]] #mandatory
				modId="${mod_id}" #mandatory
				displayName="${mod_name}"
				[[dependencies.${mod_id}]]
				modId="neoforge"
				""");
		assertEquals(new GradleProjectIdentity("Example Mod", "examplemod", "neoforge", "1.21.1", "neoforge-1.21.1", List.of()),
				GradleProjectDetector.inspect(root));
	}

	@Test void kotlinScriptsWithReadableMetadataAreSupported() throws Exception {
		fabric("26.2");
		Files.delete(root.resolve("build.gradle"));
		Files.delete(root.resolve("settings.gradle"));
		write("build.gradle.kts", "plugins { id(\"net.fabricmc.fabric-loom\") }\n");
		write("settings.gradle.kts", "rootProject.name = \"emberkit\"\n");
		GradleProjectIdentity identity = GradleProjectDetector.inspect(root);
		assertEquals(List.of(), codes(identity));
		assertEquals("fabric-26.2", identity.generatorId());
	}

	@Test void forgeMetadataNeedsNeoForgeInTheBuildScripts() throws Exception {
		wrapper();
		write("build.gradle", "// migrated from net.neoforged\nplugins { id 'net.minecraftforge.gradle' version '6.0' }\n");
		write("gradle.properties", "minecraft_version=1.20.1\nmod_id=oldmod\n");
		write("src/main/resources/META-INF/mods.toml", "[[mods]]\nmodId=\"${mod_id}\"\n");
		GradleProjectIdentity forge = GradleProjectDetector.inspect(root);
		assertEquals(List.of(GradleProjectDetector.LOADER_UNKNOWN), codes(forge));
		assertEquals(GradleProjectIdentity.UNKNOWN, forge.loader());

		write("build.gradle", "plugins { id 'net.neoforged.gradle' version '[6.0.18,6.2)' }\n");
		GradleProjectIdentity neoForge = GradleProjectDetector.inspect(root);
		assertEquals(List.of(), codes(neoForge));
		assertEquals("neoforge-1.20.1", neoForge.generatorId());
	}

	@Test void multiModuleAndMultiLoaderProjectsAreReported() throws Exception {
		fabric("1.21.1");
		write("settings.gradle", "includeBuild('build-logic')\n// include ':old'\n");
		assertEquals(List.of(), codes(GradleProjectDetector.inspect(root)));
		write("settings.gradle", "include(':common', ':fabric')\n");
		assertEquals(List.of(GradleProjectDetector.MULTI_MODULE), codes(GradleProjectDetector.inspect(root)));
		write("settings.gradle", "include ':common'\n");
		assertEquals(List.of(GradleProjectDetector.MULTI_MODULE), codes(GradleProjectDetector.inspect(root)));

		write("settings.gradle", "rootProject.name = 'both'\n");
		write("src/main/resources/META-INF/neoforge.mods.toml", "[[mods]]\nmodId=\"emberkit\"\n");
		GradleProjectIdentity both = GradleProjectDetector.inspect(root);
		assertEquals(List.of(GradleProjectDetector.MULTI_LOADER), codes(both));
		assertEquals(GradleProjectIdentity.UNKNOWN, both.loader());
	}

	@Test void missingWrapperAndUnresolvedModIdAreReported() throws Exception {
		fabric("1.21.1");
		Files.delete(root.resolve("gradle/wrapper/gradle-wrapper.jar"));
		write("src/main/resources/fabric.mod.json", "{\"id\":\"${mod_id}\"}");
		GradleProjectIdentity identity = GradleProjectDetector.inspect(root);
		assertEquals(List.of(GradleProjectDetector.WRAPPER_MISSING, GradleProjectDetector.MOD_ID_UNKNOWN), codes(identity));
		assertTrue(identity.problems().getFirst().message().contains("gradle-wrapper.jar"));
		assertNull(identity.modId());
		assertFalse(identity.supported());
	}

	@Test void invalidMetadataIsReportedInsteadOfAMissingLoader() throws Exception {
		fabric("1.21.1");
		write("src/main/resources/fabric.mod.json", "{\"id\":");
		assertEquals(List.of(GradleProjectDetector.METADATA_INVALID), codes(GradleProjectDetector.inspect(root)));
	}

	@Test void foldersThatAreNotProjectsToAdoptAreRefused() throws Exception {
		assertEquals(GradleProjectDetector.NOT_A_PROJECT,
				assertThrows(NotAProjectException.class, () -> GradleProjectDetector.inspect(root)).code());
		fabric("1.21.1");
		write("emberkit.mcreator", "{}");
		assertEquals(GradleProjectDetector.MCREATOR_WORKSPACE,
				assertThrows(NotAProjectException.class, () -> GradleProjectDetector.inspect(root)).code());
	}

	private TreeMap<String, String> snapshot() throws Exception {
		var result = new TreeMap<String, String>();
		try (var paths = Files.walk(root)) {
			for (Path path : paths.toList()) result.put(root.relativize(path).toString(), Files.isRegularFile(path)
					? Files.getLastModifiedTime(path) + " " + Files.readString(path) : "directory");
		}
		return result;
	}
}
