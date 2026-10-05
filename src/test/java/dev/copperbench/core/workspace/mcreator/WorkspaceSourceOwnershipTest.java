package dev.copperbench.core.workspace.mcreator;

import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.generator.TagsUtils;
import net.mcreator.minecraft.TagType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.TagElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceSourceOwnershipTest {
	@TempDir Path root;
	@BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

	@Test void ownershipIncludesGeneratorLanguageFolderAndTrackedTagsOnly() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("source_owner");
		settings.setModName("Source Owner"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
		try (Workspace workspace = Workspace.createWorkspace(root.resolve("source_owner.mcreator").toFile(), settings)) {
			TagElement tag = new TagElement(TagType.ITEMS, "mod:registered_items");
			workspace.getTagElements().put(tag, new ArrayList<>());
			MCreatorWorkspaceMutationGateway gateway = new MCreatorWorkspaceMutationGateway(workspace, UUID.randomUUID());
			Map<String, String> ownership = gateway.workspaceSourceOwnership();
			String language = relative(workspace.getGenerator().getLangFilesRoot().toPath()) + "/";
			assertEquals("generated", ownership.get(language));
			assertEquals("generated", ownership.get(relative(TagsUtils.getTagFileFor(workspace, tag).toPath())));
			assertFalse(ownership.containsKey("src/main/resources/assets/other_mod/lang/"));
			assertFalse(ownership.containsKey("src/main/resources/data/source_owner/tags/item/handwritten.json"));
		}
	}
	private String relative(Path path) { return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'); }
}
