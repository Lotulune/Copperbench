package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.AIPathNodeType;
import net.mcreator.element.parts.AchievementEntry;
import net.mcreator.element.parts.ItemUseAnimation;
import net.mcreator.element.parts.MItemBlock;
import net.mcreator.element.parts.MapColor;
import net.mcreator.element.parts.NoteBlockInstrument;
import net.mcreator.element.types.Block;
import net.mcreator.element.types.Achievement;
import net.mcreator.element.types.Function;
import net.mcreator.element.types.Item;
import net.mcreator.element.types.LootTable;
import net.mcreator.element.types.Procedure;
import net.mcreator.element.types.Recipe;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.elements.ModElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Transaction participant for the first-party upstream-backed element slice. */
public final class MCreatorWorkspaceMutationGateway implements WorkspaceMutationGateway {

	public static final String ELEMENT_ID_METADATA = "dev.copperbench.elementId";
	public static final String ELEMENT_VALUES_METADATA = "dev.copperbench.values";
	private static final String EMPTY_PROCEDURE_XML = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
			+ "<block type=\"event_trigger\" deletable=\"false\" x=\"40\" y=\"40\">"
			+ "<field name=\"trigger\">no_ext_trigger</field></block></xml>";
	private static final String EMPTY_ADVANCEMENT_TRIGGER_XML = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
			+ "<block type=\"advancement_trigger\" deletable=\"false\" x=\"40\" y=\"80\">"
			+ "<next><shadow type=\"custom_trigger\"></shadow></next></block></xml>";

	private final Workspace workspace;
	private final UUID workspaceId;
	private final List<WorkspaceMutationObserver> observers;

	public MCreatorWorkspaceMutationGateway(Workspace workspace, UUID workspaceId) {
		this(workspace, workspaceId, List.of());
	}

	public MCreatorWorkspaceMutationGateway(Workspace workspace, UUID workspaceId,
			List<WorkspaceMutationObserver> observers) {
		this.workspace = workspace;
		this.workspaceId = workspaceId;
		this.observers = List.copyOf(observers);
	}

	@Override public void persist(WorkspaceState before, WorkspaceState after, Operation operation,
			Element affectedElement) throws Exception {
		ModElement existing = find(affectedElement.id());
		String storedName = existing == null ? affectedElement.name() : existing.getName();
		FileSnapshot snapshot = FileSnapshot.capture(workspace, storedName, existing);
		try {
			switch (operation) {
				case CREATE_MOD_ELEMENT -> create(affectedElement);
				case UPDATE_MOD_ELEMENT, UPDATE_PROCEDURE -> update(existing, affectedElement);
				case DELETE_MOD_ELEMENT -> delete(existing);
				default -> throw new IllegalArgumentException("Operation is not a content mutation: " + operation);
			}
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			for (WorkspaceMutationObserver observer : observers)
				observer.afterMutation(workspace, before, after, operation, affectedElement);
			workspace.getFileManager().advanceProductRevision(before.id(), before.revision());
		} catch (Exception exception) {
			try {
				snapshot.restore();
				workspace.reloadFromFileSystem();
			} catch (Exception rollbackFailure) {
				exception.addSuppressed(rollbackFailure);
			}
			throw exception;
		}
	}

	@Override public void persistRestoredRevision(WorkspaceState restored, long newRevision) throws Exception {
		workspace.getFileManager().synchronizeProductRevision(workspaceId, newRevision);
	}

	@Override public void persistWorkspaceData(WorkspaceState before, WorkspaceState after, Operation operation)
			throws Exception {
		FileSnapshot snapshot = FileSnapshot.capture(workspace, "__workspace_registry__", null);
		try {
			MCreatorWorkspaceRegistryMapper.synchronize(workspace, after.registries());
			for (Element element : after.elements()) {
				Element previous = before.element(element.id());
				if (previous != null && !previous.values().equals(element.values()))
					update(find(element.id()), element);
			}
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			workspace.getFileManager().advanceProductRevision(before.id(), before.revision(), after.registries());
		} catch (Exception exception) {
			try {
				snapshot.restore();
				workspace.reloadFromFileSystem();
			} catch (Exception rollbackFailure) {
				exception.addSuppressed(rollbackFailure);
			}
			throw exception;
		}
	}

	private void create(Element element) {
		if (workspace.getModElementByName(element.name()) != null)
			throw new IllegalStateException("Element already exists in upstream workspace: " + element.name());

		ModElement modElement = new ModElement(workspace, element.name(), modElementType(element.type()));
		storeProductMetadata(modElement, element);
		GeneratableElement definition = newDefinition(modElement, element);
		workspace.addModElement(modElement);
		workspace.getModElementManager().storeModElement(definition);
	}

	private void update(ModElement modElement, Element element) {
		if (modElement == null)
			throw new IllegalStateException("Element is missing from upstream workspace: " + element.id());
		GeneratableElement definition = modElement.getGeneratableElement();
		if (definition == null || !element.type().equals(modElement.getTypeString()))
			throw new IllegalStateException("Element type does not match the upstream definition: " + element.id());
		storeProductMetadata(modElement, element);
		updateDefinition(definition, element);
		workspace.markDirty();
		workspace.getModElementManager().storeModElement(definition);
	}

	private ModElementType<?> modElementType(String type) {
		ModElementType<?> result = switch (type) {
			case "block" -> ModElementType.BLOCK;
			case "item" -> ModElementType.ITEM;
			case "recipe" -> ModElementType.RECIPE;
			case "procedure" -> ModElementType.PROCEDURE;
			case "function" -> ModElementType.FUNCTION;
			case "loottable" -> ModElementType.LOOTTABLE;
			case "achievement" -> ModElementType.ADVANCEMENT;
			default -> throw new UnsupportedOperationException("Unsupported first-party element type: " + type);
		};
		if (result == null)
			throw new IllegalStateException("Element type is not registered: " + type);
		return result;
	}

	private GeneratableElement newDefinition(ModElement modElement, Element element) {
		return switch (element.type()) {
			case "block" -> newBlock(modElement, element);
			case "item" -> newItem(modElement, element);
			case "recipe" -> newRecipe(modElement, element);
			case "procedure" -> {
				Procedure procedure = new Procedure(modElement);
				procedure.procedurexml = procedureXml(element.values());
				yield procedure;
			}
			case "function" -> newFunction(modElement, element);
			case "loottable" -> newLootTable(modElement, element);
			case "achievement" -> newAchievement(modElement, element);
			default -> throw new UnsupportedOperationException("Unsupported first-party element type: " + element.type());
		};
	}

	private Block newBlock(ModElement modElement, Element element) {
		Block block = new Block(modElement);
		block.name = element.displayName();
		block.customModelName = "Normal";
		block.transparencyType = "SOLID";
		block.colorOnMap = new MapColor(workspace, "DEFAULT");
		block.noteBlockInstrument = new NoteBlockInstrument(workspace, "harp");
		block.aiPathNodeType = new AIPathNodeType(workspace, "DEFAULT");
		block.boundingBoxes.clear();
		block.inventoryStackSize = 99;
		block.frequencyPerChunks = 10;
		block.frequencyOnChunk = 16;
		block.maxGenerateHeight = 64;
		return block;
	}

	private Item newItem(ModElement modElement, Element element) {
		Item item = new Item(modElement);
		item.name = element.displayName();
		item.customModelName = "Normal";
		item.stackSize = 64;
		item.toolType = 1;
		item.animation = new ItemUseAnimation(workspace, "eat");
		return item;
	}

	private Recipe newRecipe(ModElement modElement, Element element) {
		Recipe recipe = new Recipe(modElement);
		recipe.name = element.name();
		recipe.recipeType = "Crafting";
		recipe.recipeSlots = new MItemBlock[9];
		for (int index = 0; index < recipe.recipeSlots.length; index++)
			recipe.recipeSlots[index] = new MItemBlock(workspace, "");
		recipe.recipeReturnStack = new MItemBlock(workspace, "");
		return recipe;
	}

	private Function newFunction(ModElement modElement, Element element) {
		Function function = new Function(modElement);
		applyFunction(function, element);
		return function;
	}

	private void applyFunction(Function function, Element element) {
		JsonObject values = element.values();
		function.name = string(values, "name", element.name()).toLowerCase(java.util.Locale.ROOT);
		function.namespace = string(values, "namespace", "mod");
		if (values.has("commands") && values.get("commands").isJsonArray()) {
			List<String> commands = new java.util.ArrayList<>();
			values.getAsJsonArray("commands").forEach(command -> commands.add(command.getAsString()));
			function.code = String.join("\n", commands) + (commands.isEmpty() ? "" : "\n");
		} else function.code = string(values, "code", "# New Copperbench function\n");
	}

	private LootTable newLootTable(ModElement modElement, Element element) {
		LootTable lootTable = new LootTable(modElement);
		applyLootTable(lootTable, element);
		return lootTable;
	}

	private void applyLootTable(LootTable lootTable, Element element) {
		JsonObject values = element.values();
		lootTable.name = string(values, "name", element.name()).toLowerCase(java.util.Locale.ROOT);
		lootTable.namespace = string(values, "namespace", "mod");
		lootTable.type = string(values, "type", "Generic");
		lootTable.pools = new java.util.ArrayList<>();
		if (!values.has("pools") || !values.get("pools").isJsonArray()) return;
		for (var rawPool : values.getAsJsonArray("pools")) {
			JsonObject value = rawPool.getAsJsonObject();
			LootTable.Pool pool = new LootTable.Pool();
			pool.minrolls = integer(value, "minRolls", 1);
			pool.maxrolls = integer(value, "maxRolls", pool.minrolls);
			pool.hasbonusrolls = bool(value, "hasBonusRolls", false);
			pool.minbonusrolls = integer(value, "minBonusRolls", 0);
			pool.maxbonusrolls = integer(value, "maxBonusRolls", pool.minbonusrolls);
			for (var rawEntry : array(value, "entries")) {
				JsonObject source = rawEntry.getAsJsonObject();
				LootTable.Pool.Entry entry = new LootTable.Pool.Entry();
				entry.type = string(source, "type", "item");
				entry.item = new MItemBlock(workspace, string(source, "item", "Blocks.STONE"));
				entry.weight = integer(source, "weight", 1);
				entry.minCount = integer(source, "minCount", 1);
				entry.maxCount = integer(source, "maxCount", entry.minCount);
				entry.minEnchantmentLevel = integer(source, "minEnchantmentLevel", 0);
				entry.maxEnchantmentLevel = integer(source, "maxEnchantmentLevel", entry.minEnchantmentLevel);
				entry.affectedByFortune = bool(source, "affectedByFortune", false);
				entry.explosionDecay = bool(source, "explosionDecay", false);
				entry.silkTouchMode = integer(source, "silkTouchMode", 0);
				pool.entries.add(entry);
			}
			lootTable.pools.add(pool);
		}
	}

	private Achievement newAchievement(ModElement modElement, Element element) {
		Achievement achievement = new Achievement(modElement);
		applyAchievement(achievement, element);
		return achievement;
	}

	private void applyAchievement(Achievement achievement, Element element) {
		JsonObject values = element.values();
		achievement.achievementName = string(values, "title", element.displayName());
		achievement.achievementDescription = string(values, "description", "");
		achievement.achievementIcon = new MItemBlock(workspace, string(values, "icon", "Blocks.STONE"));
		achievement.background = string(values, "background", "Default");
		achievement.disableDisplay = bool(values, "disableDisplay", false);
		achievement.showPopup = bool(values, "showPopup", true);
		achievement.announceToChat = bool(values, "announceToChat", true);
		achievement.hideIfNotCompleted = bool(values, "hideIfNotCompleted", false);
		achievement.rewardLoot = strings(values, "rewardLoot");
		achievement.rewardRecipes = strings(values, "rewardRecipes");
		String rewardFunction = string(values, "rewardFunction", "");
		achievement.rewardFunction = rewardFunction.isBlank() ? null : rewardFunction;
		achievement.rewardXP = integer(values, "rewardXP", 0);
		achievement.achievementType = string(values, "frame", "task");
		achievement.parent = new AchievementEntry(workspace, string(values, "parent", "ROOT"));
		achievement.triggerxml = string(values, "triggerxml", EMPTY_ADVANCEMENT_TRIGGER_XML);
	}

	private void updateDefinition(GeneratableElement definition, Element element) {
			switch (definition) {
			case Block block -> block.name = element.displayName();
			case Item item -> item.name = element.displayName();
			case Recipe recipe -> recipe.name = element.name();
			case Procedure procedure -> procedure.procedurexml = procedureXml(element.values());
			case Function function -> applyFunction(function, element);
			case LootTable lootTable -> applyLootTable(lootTable, element);
			case Achievement achievement -> applyAchievement(achievement, element);
			default -> throw new UnsupportedOperationException(
					"Unsupported first-party definition: " + definition.getClass().getName());
		}
	}

	private void delete(ModElement modElement) {
		if (modElement == null)
			throw new IllegalStateException("Element is missing from upstream workspace");
		workspace.getHistoryManager().importantCheckpoint("copperbench_before_delete", modElement.getName());
		workspace.removeModElement(modElement);
	}

	private void storeProductMetadata(ModElement modElement, Element element) {
		modElement.putMetadata(ELEMENT_ID_METADATA, element.id().toString());
		modElement.putMetadata(ELEMENT_VALUES_METADATA,
				WorkspaceFileManager.gson.fromJson(element.values(), Object.class));
	}

	private String procedureXml(JsonObject values) {
		return values.has("procedurexml") && values.get("procedurexml").isJsonPrimitive()
				? values.get("procedurexml").getAsString() : EMPTY_PROCEDURE_XML;
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
	}

	private static int integer(JsonObject object, String key, int fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
	}

	private static boolean bool(JsonObject object, String key, boolean fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
	}

	private static com.google.gson.JsonArray array(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new com.google.gson.JsonArray();
	}

	private static List<String> strings(JsonObject object, String key) {
		List<String> result = new java.util.ArrayList<>();
		array(object, key).forEach(value -> result.add(value.getAsString()));
		return result;
	}

	private ModElement find(UUID elementId) {
		for (ModElement element : workspace.getModElements()) {
			Object storedId = element.getMetadata(ELEMENT_ID_METADATA);
			if (storedId != null && elementId.toString().equals(String.valueOf(storedId)))
				return element;
			UUID derived = MCreatorWorkspaceStateMapper.elementId(workspaceId, element);
			if (derived.equals(elementId))
				return element;
		}
		return null;
	}

	private record FileSnapshot(Map<Path, byte[]> files) {

		private static FileSnapshot capture(Workspace workspace, String elementName, ModElement existing)
				throws IOException {
			Path root = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
			Map<Path, byte[]> files = new LinkedHashMap<>();
			capture(files, root, workspace.getFileManager().getWorkspaceFile().toPath());
			capture(files, root, workspace.getFolderManager().getModElementsDir().toPath()
					.resolve(elementName + ".mod.json"));
			if (existing != null) {
				for (var associated : existing.getAssociatedFiles())
					capture(files, root, associated.toPath());
			}
			return new FileSnapshot(files);
		}

		private static void capture(Map<Path, byte[]> files, Path root, Path candidate) throws IOException {
			Path normalized = candidate.toAbsolutePath().normalize();
			if (!normalized.startsWith(root))
				throw new IOException("Workspace mutation referenced a file outside the workspace: " + normalized);
			files.putIfAbsent(normalized, Files.isRegularFile(normalized) ? Files.readAllBytes(normalized) : null);
		}

		private void restore() throws IOException {
			for (var entry : files.entrySet()) {
				if (entry.getValue() == null) {
					Files.deleteIfExists(entry.getKey());
				} else {
					Files.createDirectories(entry.getKey().getParent());
					Files.write(entry.getKey(), entry.getValue());
				}
			}
		}
	}
}
