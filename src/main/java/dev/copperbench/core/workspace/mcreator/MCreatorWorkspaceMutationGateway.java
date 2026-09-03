package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.release.GeneratorElementCapabilityCatalog;
import net.mcreator.blockly.data.BlocklyXML;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.parts.AIPathNodeType;
import net.mcreator.element.parts.AchievementEntry;
import net.mcreator.element.parts.ItemUseAnimation;
import net.mcreator.element.parts.IWorkspaceDependent;
import net.mcreator.element.parts.MItemBlock;
import net.mcreator.element.parts.MapColor;
import net.mcreator.element.parts.MobSpawnType;
import net.mcreator.element.parts.NoteBlockInstrument;
import net.mcreator.element.parts.Sound;
import net.mcreator.element.parts.StepSound;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.parts.procedure.NumberProcedure;
import net.mcreator.element.types.Achievement;
import net.mcreator.element.types.Block;
import net.mcreator.element.types.CustomElement;
import net.mcreator.element.types.Function;
import net.mcreator.element.types.Item;
import net.mcreator.element.types.LivingEntity;
import net.mcreator.element.types.LootTable;
import net.mcreator.element.types.Procedure;
import net.mcreator.element.types.Projectile;
import net.mcreator.element.types.Recipe;
import net.mcreator.element.types.SpecialEntity;
import net.mcreator.element.types.Tool;
import net.mcreator.element.util.GEValidator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.elements.ModElement;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Transaction participant for all first-party Java elements backed by the upstream model classes. */
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
		FileSnapshot snapshot = FileSnapshot.capture(workspace, existing);
		try {
			switch (operation) {
				case CREATE_MOD_ELEMENT -> create(affectedElement);
				case UPDATE_MOD_ELEMENT, UPDATE_PROCEDURE -> update(existing, affectedElement);
				case DELETE_MOD_ELEMENT -> delete(existing, true);
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

	@Override public void persistWorkspacePlan(WorkspaceState before, WorkspaceState after, List<Operation> operations)
			throws Exception {
		FileSnapshot snapshot = FileSnapshot.capturePlan(workspace, before, after, workspaceId);
		try {
			if (!before.registries().equals(after.registries()))
				MCreatorWorkspaceRegistryMapper.synchronize(workspace, after.registries());

			Map<UUID, Element> beforeElements = new LinkedHashMap<>();
			for (Element element : before.elements()) beforeElements.put(element.id(), element);
			Map<UUID, Element> afterElements = new LinkedHashMap<>();
			for (Element element : after.elements()) afterElements.put(element.id(), element);

			for (Element element : beforeElements.values())
				if (!afterElements.containsKey(element.id())) delete(find(element.id()), false);
			for (Element element : afterElements.values()) {
				Element previous = beforeElements.get(element.id());
				if (previous == null) create(element);
				else if (!sameContent(previous, element)) update(find(element.id()), element);
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

	private static boolean sameContent(Element left, Element right) {
		return left.type().equals(right.type()) && left.name().equals(right.name())
				&& left.displayName().equals(right.displayName()) && left.state().equals(right.state())
				&& left.ownership().equals(right.ownership()) && left.values().equals(right.values());
	}

	@Override public void persistRestoredRevision(WorkspaceState restored, long newRevision) throws Exception {
		workspace.getFileManager().synchronizeProductRevision(workspaceId, newRevision);
	}

	@Override public void persistWorkspaceData(WorkspaceState before, WorkspaceState after, Operation operation)
			throws Exception {
		FileSnapshot snapshot = FileSnapshot.capture(workspace, null);
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
		persistGeneratedElement(modElement, element, definition);
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
		persistGeneratedElement(modElement, element, definition);
	}

	private void persistGeneratedElement(ModElement modElement, Element element, GeneratableElement definition) {
		if (definition instanceof CustomElement) {
			persistCustomCode(modElement, element, definition);
			generateWorkspaceBaseIfReady();
			return;
		}
		workspace.getModElementManager().storeModElement(definition);
		generateRegisteredSources(definition);
	}

	private void generateRegisteredSources(GeneratableElement definition) {
		if (!generatorWorkspaceReady())
			return;
		String generatorId = workspace.getWorkspaceSettings().getCurrentGenerator();
		String type = definition.getModElement().getType().getRegistryName();
		var decision = GeneratorElementCapabilityCatalog.decision(generatorId, type);
		if (!decision.generatable())
			return;
		try {
			GEValidator.validateAndTryToCorrect(definition, null);
		} catch (GEValidator.ValidationException | AssertionError exception) {
			throw new IllegalStateException("Upstream element validation failed for "
					+ definition.getModElement().getName(), exception);
		}
		try {
			workspace.getGenerator().generateBase();
			if (!workspace.getGenerator().generateElement(definition)) {
				workspace.getGenerator().removeElementFilesAndWorkspaceLinks(definition);
				throw new IllegalStateException("Upstream source generation failed for "
						+ definition.getModElement().getName());
			}
			// The first base generation intentionally runs before element generation so upstream element
			// templates can resolve existing base classes. A newly created element's own Java class does
			// not exist in the import tree until generateElement() writes it, though. Refresh the base once
			// more so shared registries/init files can import that new class before the next real Gradle build.
			workspace.getGenerator().generateBase();
		} catch (RuntimeException exception) {
			try {
				workspace.getGenerator().removeElementFilesAndWorkspaceLinks(definition);
			} catch (RuntimeException cleanup) {
				exception.addSuppressed(cleanup);
			}
			throw exception;
		}
	}

	private void generateWorkspaceBaseIfReady() {
		if (generatorWorkspaceReady())
			workspace.getGenerator().generateBase();
	}

	private boolean generatorWorkspaceReady() {
		if (workspace.getGenerator() == null || workspace.getGenerator().getGeneratorConfiguration() == null)
			return false;
		java.io.File sourceRoot = workspace.getGenerator().getSourceRoot();
		return sourceRoot != null && sourceRoot.isDirectory();
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
			default -> ModElementTypeLoader.getModElementType(type);
		};
		if (result == null)
			throw new IllegalStateException("Element type is not registered: " + type);
		return result;
	}

	private GeneratableElement newDefinition(ModElement modElement, Element element) {
		GeneratableElement definition = switch (element.type()) {
			case "block" -> newBlock(modElement, element);
			case "item" -> newItem(modElement, element);
			case "recipe" -> newRecipe(modElement, element);
			case "procedure" -> {
				Procedure procedure = new Procedure(modElement);
				procedure.procedurexml = procedureXml(element.values());
				yield procedure;
			}
			case "projectile" -> newProjectile(modElement, element);
			case "function" -> newFunction(modElement, element);
			case "loottable" -> newLootTable(modElement, element);
			case "achievement" -> newAchievement(modElement, element);
			default -> newGenericDefinition(modElement, element);
		};
		applyRequiredGenerationDefaults(definition, element);
		IWorkspaceDependent.processWorkspaceDependentObjects(definition,
				workspaceDependent -> workspaceDependent.setWorkspace(workspace));
		return definition;
	}

	private GeneratableElement newGenericDefinition(ModElement modElement, Element element) {
		try {
			Class<? extends GeneratableElement> storageClass = modElementType(element.type()).getModElementStorageClass();
			GeneratableElement definition = storageClass.getConstructor(ModElement.class).newInstance(modElement);
			applyGenericValues(definition, element);
			return definition;
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to instantiate upstream element type: " + element.type(), exception);
		}
	}

	private Block newBlock(ModElement modElement, Element element) {
		Block block = new Block(modElement);
		block.name = element.displayName();
		block.customModelName = "Normal";
		block.transparencyType = "SOLID";
		block.colorOnMap = new MapColor(workspace, "DEFAULT");
		block.noteBlockInstrument = new NoteBlockInstrument(workspace, "harp");
		block.aiPathNodeType = new AIPathNodeType(workspace, "DEFAULT");
		block.soundOnStep = new StepSound(workspace, "STONE");
		block.luminance = new NumberProcedure(null, 0);
		block.emittedRedstonePower = new NumberProcedure(null, 0);
		block.customDrop = new MItemBlock(workspace, "");
		block.creativePickItem = new MItemBlock(workspace, "");
		block.strippingResult = new MItemBlock(workspace, "");
		block.texture = texture(element.values(), "texture", "minecraft:stone");
		block.textureTop = texture(element.values(), "textureTop", block.texture.toString());
		block.textureLeft = texture(element.values(), "textureLeft", block.texture.toString());
		block.textureFront = texture(element.values(), "textureFront", block.texture.toString());
		block.textureRight = texture(element.values(), "textureRight", block.texture.toString());
		block.textureBack = texture(element.values(), "textureBack", block.texture.toString());
		block.itemTexture = texture(element.values(), "itemTexture", block.texture.toString());
		block.particleTexture = texture(element.values(), "particleTexture", block.texture.toString());
		block.inventoryStackSize = 99;
		block.frequencyPerChunks = 10;
		block.frequencyOnChunk = 16;
		block.maxGenerateHeight = 64;
		applyGenericValues(block, element);
		return block;
	}

	private Item newItem(ModElement modElement, Element element) {
		Item item = new Item(modElement);
		applyItem(item, element);
		item.customModelName = "Normal";
		item.stackSize = 64;
		item.toolType = 1;
		item.animation = new ItemUseAnimation(workspace, "eat");
		return item;
	}

	private void applyItem(Item item, Element element) {
		JsonObject values = element.values();
		item.name = element.displayName();
		item.texture = new TextureHolder(workspace, string(values, "texture", "minecraft:barrier"));
	}

	private Projectile newProjectile(ModElement modElement, Element element) {
		Projectile projectile = new Projectile(modElement);
		applyProjectileDefaults(projectile, element);
		return projectile;
	}

	private void applyProjectileDefaults(Projectile projectile, Element element) {
		JsonObject values = element.values();
		projectile.projectileItem = new MItemBlock(workspace, string(values, "projectileItem", "Items.ARROW"));
		projectile.entityModel = string(values, "entityModel", "Default");
		projectile.customModelTexture = string(values, "customModelTexture", "");
		projectile.actionSound = new Sound(workspace, string(values, "actionSound", ""));
		projectile.power = decimal(values, "power", 1.0);
		projectile.damage = decimal(values, "damage", 5.0);
		projectile.knockback = integer(values, "knockback", 5);
		projectile.showParticles = bool(values, "showParticles", false);
		projectile.disableGravity = bool(values, "disableGravity", false);
		projectile.igniteFire = bool(values, "igniteFire", false);
		projectile.disableDiscarding = bool(values, "disableDiscarding", false);
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
			case Item item -> applyItem(item, element);
			case Recipe recipe -> recipe.name = element.name();
			case Procedure procedure -> procedure.procedurexml = procedureXml(element.values());
			case Projectile projectile -> applyProjectileDefaults(projectile, element);
			case Function function -> applyFunction(function, element);
			case LootTable lootTable -> applyLootTable(lootTable, element);
			case Achievement achievement -> applyAchievement(achievement, element);
			default -> applyGenericValues(definition, element);
		}
		applyRequiredGenerationDefaults(definition, element);
		IWorkspaceDependent.processWorkspaceDependentObjects(definition,
				workspaceDependent -> workspaceDependent.setWorkspace(workspace));
	}

	/**
	 * The upstream model classes expose their editable fields as public members. Mapping only
	 * fields present in the Copperbench value object keeps type-specific defaults intact while
	 * allowing newly added upstream fields to round-trip without another gateway switch.
	 */
	private void applyGenericValues(GeneratableElement definition, Element element) {
		JsonObject values = element.values();
		for (Field field : definition.getClass().getFields()) {
			if (Modifier.isStatic(field.getModifiers())) continue;
			com.google.gson.JsonElement raw = values.get(field.getName());
			if ((raw == null || raw.isJsonNull()) && values.has("fields") && values.get("fields").isJsonObject())
				raw = values.getAsJsonObject("fields").get(field.getName());
			if (raw == null || raw.isJsonNull()) continue;
			try {
				Object value = WorkspaceFileManager.gson.fromJson(raw, field.getGenericType());
				field.set(definition, value);
			} catch (RuntimeException | IllegalAccessException ignored) {
				// Complex workspace-dependent values retain the upstream default; raw values stay in metadata.
			}
		}
		try {
			Field name = definition.getClass().getField("name");
			if (name.getType() == String.class && values.has("displayName"))
				name.set(definition, element.displayName());
			else if (!values.has("name") && name.getType() == String.class
					&& (name.get(definition) == null || ((String) name.get(definition)).isBlank()))
				name.set(definition, element.displayName());
		} catch (NoSuchFieldException | IllegalAccessException ignored) {
			// Some upstream types intentionally do not expose a name field.
		}
		fillMissingStringDefaults(definition);
	}

	private void fillMissingStringDefaults(GeneratableElement definition) {
		for (Field field : definition.getClass().getFields()) {
			if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class)
				continue;
			try {
				if (field.get(definition) != null)
					continue;
				BlocklyXML blockly = field.getAnnotation(BlocklyXML.class);
				var options = field.getAnnotation(net.mcreator.element.types.interfaces.LimitedOptions.class);
				if (blockly != null) field.set(definition, blockly.defaultXML());
				else field.set(definition, options != null && options.value().length > 0 ? options.value()[0] : "");
			} catch (IllegalAccessException ignored) {
				// Keep the upstream constructor default when the field cannot be written.
			}
		}
	}

	private void applyRequiredGenerationDefaults(GeneratableElement definition, Element element) {
		JsonObject values = element.values();
		switch (definition) {
			case Tool tool -> {
				tool.texture = texture(values, "texture", "minecraft:barrier");
				tool.guiTexture = texture(values, "guiTexture", "");
			}
			case LivingEntity entity -> {
				entity.mobModelName = string(values, "mobModelName", "Biped");
				entity.mobModelTexture = string(values, "mobModelTexture", "zombie.png");
				if (entity.spawnEggBaseColor == null) entity.spawnEggBaseColor = java.awt.Color.GRAY;
				if (entity.spawnEggDotColor == null) entity.spawnEggDotColor = java.awt.Color.DARK_GRAY;
				if (entity.equipmentMainHand == null) entity.equipmentMainHand = new MItemBlock(workspace, "");
				if (entity.equipmentOffHand == null) entity.equipmentOffHand = new MItemBlock(workspace, "");
				if (entity.equipmentHelmet == null) entity.equipmentHelmet = new MItemBlock(workspace, "");
				if (entity.equipmentBody == null) entity.equipmentBody = new MItemBlock(workspace, "");
				if (entity.equipmentLeggings == null) entity.equipmentLeggings = new MItemBlock(workspace, "");
				if (entity.equipmentBoots == null) entity.equipmentBoots = new MItemBlock(workspace, "");
				if (entity.mobDrop == null) entity.mobDrop = new MItemBlock(workspace, "");
				if (entity.rangedAttackItem == null) entity.rangedAttackItem = new MItemBlock(workspace, "");
				entity.mobSpawningType = new MobSpawnType(workspace,
						string(values, "mobSpawningType", "creature"));
				if (!values.has("modelWidth")) entity.modelWidth = 0.6;
				if (!values.has("modelHeight")) entity.modelHeight = 1.8;
				if (!values.has("health")) entity.health = 10;
				if (!values.has("movementSpeed")) entity.movementSpeed = 0.3;
			}
			case SpecialEntity entity -> {
				entity.entityTexture = texture(values, "entityTexture", "minecraft:oak_planks");
				entity.itemTexture = texture(values, "itemTexture", "minecraft:oak_boat");
			}
			case Projectile projectile -> {
				projectile.projectileItem = new MItemBlock(workspace,
						string(values, "projectileItem", "Items.ARROW"));
				projectile.actionSound = new Sound(workspace, string(values, "actionSound", ""));
				projectile.entityModel = string(values, "entityModel", "Default");
				projectile.customModelTexture = string(values, "customModelTexture", "");
			}
			default -> {
			}
		}
	}

	private TextureHolder texture(JsonObject values, String key, String fallback) {
		return new TextureHolder(workspace, string(values, key, fallback));
	}

	private void persistCustomCode(ModElement modElement, Element element, GeneratableElement definition) {
		if (workspace.getGenerator() == null)
			throw new IllegalStateException("A generator is required to persist a code element");
		if (!generatorWorkspaceReady())
			return;
		if (modElement.getAssociatedFiles().isEmpty() && !workspace.getGenerator().generateElement(definition))
			throw new IllegalStateException("The generator could not create the code element source file");
		String code = element.values().has("code") && element.values().get("code").isJsonPrimitive()
				? element.values().get("code").getAsString() : null;
		if (code != null) {
			java.io.File source = modElement.getAssociatedFiles().stream()
					.filter(file -> file.getName().endsWith(".java")).findFirst()
					.orElseThrow(() -> new IllegalStateException("The code element has no generated Java source file"));
			try {
				Files.writeString(source.toPath(), code, java.nio.charset.StandardCharsets.UTF_8);
			} catch (IOException exception) {
				throw new IllegalStateException("Unable to write the code element source file", exception);
			}
		}
		modElement.setCodeLock(true);
	}

	private void delete(ModElement modElement, boolean checkpoint) {
		if (modElement == null)
			throw new IllegalStateException("Element is missing from upstream workspace");
		if (checkpoint)
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

	private static double decimal(JsonObject object, String key, double fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsDouble() : fallback;
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

	private record FileSnapshot(Map<Path, byte[]> files, Set<Path> directories, Set<Path> trees) {

		private static FileSnapshot capture(Workspace workspace, ModElement existing) throws IOException {
			Path root = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
			Map<Path, byte[]> files = new LinkedHashMap<>();
			Set<Path> directories = new LinkedHashSet<>();
			Set<Path> trees = new LinkedHashSet<>();
			capture(files, root, workspace.getFileManager().getWorkspaceFile().toPath());
			captureTree(files, directories, trees, root, root.resolve("src"));
			captureTree(files, directories, trees, root, workspace.getFolderManager().getModElementsDir().toPath());
			if (existing != null) {
				for (var associated : existing.getAssociatedFiles())
					capture(files, root, associated.toPath());
			}
			return new FileSnapshot(files, directories, trees);
		}

		private static FileSnapshot capturePlan(Workspace workspace, WorkspaceState before, WorkspaceState after,
				UUID workspaceId) throws IOException {
			Path root = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
			Map<Path, byte[]> files = new LinkedHashMap<>();
			Set<Path> directories = new LinkedHashSet<>();
			Set<Path> trees = new LinkedHashSet<>();
			capture(files, root, workspace.getFileManager().getWorkspaceFile().toPath());
			captureTree(files, directories, trees, root, root.resolve("src"));
			captureTree(files, directories, trees, root, workspace.getFolderManager().getModElementsDir().toPath());
			Map<UUID, Element> beforeElements = new LinkedHashMap<>();
			for (Element element : before.elements()) beforeElements.put(element.id(), element);
			Map<UUID, Element> afterElements = new LinkedHashMap<>();
			for (Element element : after.elements()) afterElements.put(element.id(), element);

			for (Element previous : beforeElements.values()) {
				Element next = afterElements.get(previous.id());
				if (next != null && sameContent(previous, next)) continue;
				ModElement existing = find(workspace, workspaceId, previous.id());
				capture(files, root, workspace.getFolderManager().getModElementsDir().toPath()
						.resolve(previous.name() + ".mod.json"));
				if (existing != null)
					for (var associated : existing.getAssociatedFiles()) capture(files, root, associated.toPath());
			}
			for (Element next : afterElements.values()) {
				if (beforeElements.containsKey(next.id())) continue;
				capture(files, root, workspace.getFolderManager().getModElementsDir().toPath()
						.resolve(next.name() + ".mod.json"));
			}
			return new FileSnapshot(files, directories, trees);
		}

		private static ModElement find(Workspace workspace, UUID workspaceId, UUID elementId) {
			for (ModElement element : workspace.getModElements()) {
				Object storedId = element.getMetadata(ELEMENT_ID_METADATA);
				if (storedId != null && elementId.toString().equals(String.valueOf(storedId))) return element;
				if (MCreatorWorkspaceStateMapper.elementId(workspaceId, element).equals(elementId)) return element;
			}
			return null;
		}

		private static void capture(Map<Path, byte[]> files, Path root, Path candidate) throws IOException {
			Path normalized = candidate.toAbsolutePath().normalize();
			if (!normalized.startsWith(root))
				throw new IOException("Workspace mutation referenced a file outside the workspace: " + normalized);
			files.putIfAbsent(normalized, Files.isRegularFile(normalized) ? Files.readAllBytes(normalized) : null);
		}

		private static void captureTree(Map<Path, byte[]> files, Set<Path> directories, Set<Path> trees, Path root,
				Path candidate) throws IOException {
			Path normalized = candidate.toAbsolutePath().normalize();
			if (!normalized.startsWith(root))
				throw new IOException("Workspace mutation referenced a directory outside the workspace: " + normalized);
			trees.add(normalized);
			if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) return;
			try (var paths = Files.walk(normalized)) {
				for (Path path : paths.toList()) {
					if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) directories.add(path);
					else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
						files.putIfAbsent(path, Files.readAllBytes(path));
				}
			}
		}

		private void restore() throws IOException {
			for (Path tree : trees) {
				if (!Files.isDirectory(tree, LinkOption.NOFOLLOW_LINKS)) continue;
				try (var paths = Files.walk(tree)) {
					for (Path path : paths.filter(candidate ->
							Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)).toList())
						if (!files.containsKey(path)) Files.deleteIfExists(path);
				}
			}
			for (var entry : files.entrySet()) {
				if (entry.getValue() == null) {
					Files.deleteIfExists(entry.getKey());
				} else {
					Files.createDirectories(entry.getKey().getParent());
					Files.write(entry.getKey(), entry.getValue());
				}
			}
			for (Path tree : trees) {
				if (!Files.isDirectory(tree, LinkOption.NOFOLLOW_LINKS)) continue;
				try (var paths = Files.walk(tree)) {
					for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
						if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !directories.contains(path))
							Files.deleteIfExists(path);
				}
			}
		}
	}
}
