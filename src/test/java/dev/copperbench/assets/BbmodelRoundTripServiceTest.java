package dev.copperbench.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BbmodelRoundTripServiceTest {
	@TempDir Path temp;

	@Test void goldenModelTextureAndAnimationReferencesSurviveJsonRoundTrip() throws IOException {
		Path model = copyFixture();
		var service = new BbmodelRoundTripService(new AssetWorkspaceService(temp));
		var before = service.inspect("assets/golden/copper_lamp.bbmodel");
		Files.writeString(model, Files.readString(model));
		var report = service.compare(before, service.inspect("assets/golden/copper_lamp.bbmodel"));
		assertTrue(report.compatible());
		assertEquals(2, before.textureReferences().size());
		assertEquals(2, before.elementIds().size());
		assertEquals(List.of("animation-flicker"), before.animationIds());
	}

	@Test void reportsDroppedTextureAndAnimationReferencesBeforeImport() throws IOException {
		Path model = copyFixture();
		var service = new BbmodelRoundTripService(new AssetWorkspaceService(temp));
		var before = service.inspect("assets/golden/copper_lamp.bbmodel");
		Files.writeString(model, Files.readString(model).replace(",\n    \"1\": \"copperbench:block/copper_lamp_emissive\"", "")
				.replace(",\n  \"animations\": [\n    { \"uuid\": \"animation-flicker\", \"name\": \"Flicker\" }\n  ]", ""));
		var report = service.compare(before, service.inspect("assets/golden/copper_lamp.bbmodel"));
		assertFalse(report.compatible());
		assertTrue(report.diagnostics().stream().anyMatch(d -> d.code().equals("TEXTURE_REFERENCE_DROPPED")));
		assertTrue(report.diagnostics().stream().anyMatch(d -> d.code().equals("ANIMATION_REFERENCE_DROPPED")));
	}

	private Path copyFixture() throws IOException {
		Path model = temp.resolve("assets/golden/copper_lamp.bbmodel");
		Files.createDirectories(model.getParent());
		try (var input = getClass().getResourceAsStream("/assets/golden/copper_lamp.bbmodel")) {
			Files.copy(input, model);
		}
		return model;
	}
}
