package dev.copperbench.headless;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import net.mcreator.Launcher;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Disposable cross-track source fixture. Builds and server probes run as separate real Gradle processes. */
public final class TrackAudit {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final Path AUDIT = Path.of(".tmp/stage14-track-audit").toAbsolutePath().normalize();
    static final String PACKAGE = "dev.example.surveypulse";
    static final String MODID = "survey_pulse";
    static String track;
    static boolean fabric, modern, oldest;
    static Path trackRoot, generated, nativeRoot;
    static final JsonObject report = new JsonObject();
    static long revision;

    public static void main(String[] args) {
        int exit = 0;
        track = args[0]; fabric = track.startsWith("fabric-");
        oldest = track.endsWith("1.20.1"); modern = track.contains("-26.");
        trackRoot = AUDIT.resolve("projects").resolve(track);
        generated = trackRoot.resolve("copperbench"); nativeRoot = trackRoot.resolve("native");
        if (Files.isRegularFile(trackRoot.resolve("prepare.json"))) {
            try {
                JsonObject prior = JsonParser.parseString(Files.readString(trackRoot.resolve("prepare.json"))).getAsJsonObject();
                for (var entry : prior.entrySet()) if (entry.getKey().startsWith("metadataOnly") || entry.getKey().startsWith("ownership") || entry.getKey().equals("rejectedCreateLeavesNoOrphan") || entry.getKey().equals("collisionPreservesExistingPrimary")) report.add(entry.getKey(), entry.getValue());
            } catch (IOException ignored) { }
        }
        report.addProperty("track", track);
        report.addProperty("startedAt", Instant.now().toString());
        report.addProperty("mode", "production source bootstrap/Core mutation; source-only task stub; real Gradle build/bootstrap recorded separately");
        try {
            Files.createDirectories(trackRoot);
            initialize();
            prepare();
            report.addProperty("prepared", true);
        } catch (Throwable error) {
            exit = 1; error.printStackTrace();
            report.addProperty("failure", error.toString());
        } finally {
            report.addProperty("finishedAt", Instant.now().toString());
            try { Files.writeString(trackRoot.resolve("prepare.json"), JSON.toJson(report)); }
            catch (IOException error) { error.printStackTrace(); }
        }
        System.exit(exit);
    }

    static void initialize() throws Exception {
        net.mcreator.util.TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
        net.mcreator.util.TerribleModuleHacks.openMCreatorRequirements();
        System.setProperty("copperbench.gradle.user.home", Path.of("build/stage8-workspace-generator-gradle", track.replace('.', '_')).toAbsolutePath().toString());
        net.mcreator.io.UserFolderManager.createUserFolderIfNotExists();
        Properties props = new Properties();
        try (var input = Launcher.class.getResourceAsStream("/mcreator.conf")) { props.load(input); }
        Launcher.version = new net.mcreator.util.MCreatorVersionNumber(props);
        PreferencesManager.init();
        PreferencesManager.PREFERENCES.gradle.xmx.set(1536);
        PreferencesManager.PREFERENCES.hidden.java_home.set(javaHome().resolve("bin/java.exe").toFile());
        HeadlessRuntimeBootstrap.ensureInitialized();
    }

    static Path javaHome() {
        return Path.of("../../jdk", modern ? "jbr25_win_64" : "jdk21_win_64").toAbsolutePath().normalize();
    }

    static void prepare() throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings(MODID);
        settings.setModName("Survey Pulse"); settings.setVersion("1.0.0");
        settings.setCurrentGenerator(track); settings.setModElementsPackage(PACKAGE);
        Files.createDirectories(generated);
        Path workspaceFile = generated.resolve(MODID + ".mcreator");
        boolean fresh = !Files.exists(workspaceFile);
        try (Workspace workspace = fresh ? Workspace.createWorkspace(workspaceFile.toFile(), settings)
                : Workspace.readFromFS(workspaceFile.toFile(), null)) {
            if (fresh) net.mcreator.generator.setup.WorkspaceGeneratorSetup.setupWorkspaceBaseOrThrow(workspace);
            net.mcreator.gradle.GradleUtils.updateMCreatorBuildFile(workspace);
            var config = workspace.getGenerator().getGeneratorConfiguration();
            report.addProperty("minecraftVersion", config.getGeneratorMinecraftVersion());
            report.addProperty("buildFileVersion", config.getGeneratorBuildFileVersion());
            report.addProperty("gradleJavaHome", javaHome().toString());
            // Source ownership does not depend on Gradle resolution; preserve its result even if setup later fails.
            if (fresh) {
                if (!workspace.getGenerator().generateBase()) throw new IOException("initial generateBase failed");
                try (var session = MCreatorWorkspaceSession.attach(workspace, workspace.getFileManager().loadOrCreateProductMetadata(UUID::randomUUID).workspaceId(),
                        new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                    JsonObject item = new JsonObject(); item.addProperty("displayName", "Survey Wand");
                    item.addProperty("texture", "minecraft:amethyst_shard");
                    JsonObject fields = new JsonObject(); fields.addProperty("maxStackSize", 1); item.add("fields", fields);
                    create(session, "item", "survey_wand", item, true);
                    var result = create(session, "code", "pulse_runtime", codeValues(), true);
                    String id = result.result().data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
                    Path primary = sources(generated).resolve("pulse_runtime.java");
                    Path helper = sources(generated).resolve("OreScanner.java");
                    String p = Files.readString(primary) + "\n// EXTERNAL_PRIMARY_EDIT\n";
                    String h = Files.readString(helper) + "\n// EXTERNAL_HELPER_EDIT\n";
                    Files.writeString(primary, p); Files.writeString(helper, h);
                    JsonObject change = new JsonObject(); change.addProperty("path", "/displayName"); change.addProperty("value", "Survey Pulse Runtime");
                    JsonArray changes = new JsonArray(); changes.add(change);
                    JsonObject update = new JsonObject(); update.addProperty("clientMutationId", UUID.randomUUID().toString());
                    update.addProperty("elementId", id); update.add("changes", changes);
                    committed(session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.UPDATE_MOD_ELEMENT, update)));
                    report.addProperty("metadataOnlyPreservesPrimary", Files.readString(primary).equals(p));
                    report.addProperty("metadataOnlyPreservesHelper", Files.readString(helper).equals(h));
                    JsonObject collision = new JsonObject(); collision.addProperty("code", "package " + PACKAGE + "; public class conflict_owner {}\n");
                    JsonArray files = new JsonArray(); JsonObject f = new JsonObject(); f.addProperty("path", "pulse_runtime.java"); f.addProperty("code", "// should never replace the owned file"); files.add(f); collision.add("codeFiles", files);
                    var rejected = create(session, "code", "conflict_owner", collision, false);
                    report.addProperty("ownershipConflictRejected", !"committed".equals(rejected.result().status()));
                    report.add("ownershipDiagnostics", JSON.toJsonTree(rejected.result().diagnostics()));
                    report.addProperty("rejectedCreateLeavesNoOrphan", !Files.exists(sources(generated).resolve("conflict_owner.java")) && workspace.getModElementByName("conflict_owner") == null);
                    report.addProperty("collisionPreservesExistingPrimary", Files.readString(primary).equals(p));
                    if (!report.get("metadataOnlyPreservesPrimary").getAsBoolean() || !report.get("metadataOnlyPreservesHelper").getAsBoolean()
                            || !report.get("ownershipConflictRejected").getAsBoolean() || !report.get("rejectedCreateLeavesNoOrphan").getAsBoolean())
                        throw new IllegalStateException("source integrity regression: " + report);
                }
            }
            try (var session = MCreatorWorkspaceSession.attach(workspace, workspace.getFileManager().loadOrCreateProductMetadata(UUID::randomUUID).workspaceId(),
                    new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                var item = workspace.getModElementByName("survey_wand");
                String id = item.getMetadata("dev.copperbench.elementId").toString();
                long currentRevision = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_WORKBENCH, new JsonObject())).revision();
                JsonObject change = new JsonObject(); change.addProperty("path", "/stackSize"); change.addProperty("value", 1);
                JsonArray changes = new JsonArray(); changes.add(change);
                JsonObject update = new JsonObject(); update.addProperty("clientMutationId", UUID.randomUUID().toString());
                update.addProperty("elementId", id); update.add("changes", changes);
                var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), currentRevision, Operation.UPDATE_MOD_ELEMENT, update));
                report.addProperty("canonicalStackSizeRequest", 1);
                report.addProperty("canonicalStackSizeUpdateStatus", result.result().status());
                report.addProperty("persistedStackSize", ((net.mcreator.element.types.Item) workspace.getModElementByName("survey_wand").getGeneratableElement()).stackSize);
                report.addProperty("stackSizeMatchesIntent", report.get("persistedStackSize").getAsInt() == 1);
            }
            Files.writeString(trackRoot.resolve("prepare.json"), JSON.toJson(report));
            if (!Boolean.getBoolean("audit.skipModel")) {
                System.out.println("MODEL_BEGIN " + track);
                dev.copperbench.gradle.GradleDistributionPool.seedForWorkspace(workspace);
                // Match the production/golden setup sequence; an Eclipse model alone may omit game artifacts.
                if (!fabric && !Files.exists(generated.resolve(".audit-model-sync-complete"))) {
                    var connection = net.mcreator.gradle.GradleUtils.getGradleProjectConnection(workspace);
                    String task = config.getGradleTaskFor("sync_task");
                    net.mcreator.gradle.GradleUtils.getGradleTaskLauncher(config, connection, task == null ? "help" : task).addArguments("--max-workers=2").run();
                    Files.writeString(generated.resolve(".audit-model-sync-complete"), Instant.now().toString());
                }
                workspace.getGenerator().reloadGradleCaches();
                report.addProperty("gradleModelLoaded", true);
            }
            if (!workspace.getGenerator().generateBase()) throw new IOException("generateBase after model failed");
            for (var element : workspace.getModElements())
                if (!element.isCodeLocked() && element.getGeneratableElement() != null)
                    if (!workspace.getGenerator().generateElement(element.getGeneratableElement())) throw new IOException("generate failed: " + element.getName());
            workspace.getGenerator().runResourceSetupTasks();
            // Explicit sample API migration, not an implicit metadata-only edit.
            Files.writeString(sources(generated).resolve("pulse_runtime.java"), runtime());
            Set<Path> locked = new HashSet<>();
            for (var element : workspace.getModElements()) if (element.isCodeLocked())
                for (File file : element.getAssociatedFiles()) locked.add(file.toPath().toAbsolutePath().normalize());
            try (var paths = Files.walk(workspace.getGenerator().getSourceRoot().toPath())) {
                net.mcreator.generator.io.JavaWriter.formatAndOrganiseImportsForFiles(workspace,
                        paths.filter(Files::isRegularFile).filter(p -> !locked.contains(p.toAbsolutePath().normalize())).map(Path::toFile).toList(), null);
            }
            write(generated.resolve("src/main/resources/data/" + MODID + "/" + (oldest ? "recipes" : "recipe") + "/survey_wand.json"), recipe());
            hookGeneratedInitializer();
            prepareNative();
            report.addProperty("generatedSourceCount", javaCount(generated));
            report.addProperty("nativeSourceCount", javaCount(nativeRoot));
        }
    }

    static CommandOutcome create(MCreatorWorkspaceSession session, String type, String name, JsonObject values, boolean mustCommit) {
        JsonObject payload = new JsonObject(); payload.addProperty("clientMutationId", UUID.randomUUID().toString());
        payload.addProperty("elementType", type); payload.addProperty("name", name); payload.add("initialValues", values);
        var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.CREATE_MOD_ELEMENT, payload));
        if (mustCommit) committed(result);
        return result;
    }
    static void committed(CommandOutcome result) {
        if (!"committed".equals(result.result().status())) throw new IllegalStateException(JSON.toJson(result.result()));
        revision++;
    }
    static Path sources(Path root) { return root.resolve("src/main/java/" + PACKAGE.replace('.', '/')); }
    static long javaCount(Path root) throws IOException {
        try (var paths = Files.walk(root.resolve("src/main/java"))) { return paths.filter(p -> p.toString().endsWith(".java")).count(); }
    }
    static JsonObject codeValues() throws IOException {
        JsonObject values = new JsonObject(); values.addProperty("code", runtime());
        JsonArray files = new JsonArray();
        for (String name : List.of("OreScanner.java", "ScanResult.java")) {
            JsonObject f = new JsonObject(); f.addProperty("path", name); f.addProperty("code", common(name)); files.add(f);
        }
        values.add("codeFiles", files); return values;
    }
    static String common(String name) throws IOException {
        return Files.readString(Path.of("examples/agent-native/survey-pulse/src/main/java/dev/example/surveypulse", name));
    }
    static void hookGeneratedInitializer() throws IOException {
        Path main = sources(generated).resolve("SurveyPulseMod.java");
        String code = Files.readString(main);
        if (code.contains("pulse_runtime.init();")) { report.addProperty("initHook", "preserved existing initialization call"); return; }
        String marker = "// Start of user code block mod init";
        if (code.contains(marker)) {
            code = code.replace(marker, marker + "\n        pulse_runtime.init();");
            report.addProperty("initHook", "marked generated-main user block");
        } else {
            String ctor = "public SurveyPulseMod() {";
            if (!code.contains(ctor)) throw new IOException("Missing generated mod constructor");
            code = code.replace(ctor, ctor + "\n        pulse_runtime.init();");
            report.addProperty("initHook", "source-fixture constructor patch; this old generator lacks the modern user block");
        }
        Files.writeString(main, code);
    }
    static void prepareNative() throws IOException {
        Files.createDirectories(nativeRoot);
        for (String name : List.of("settings.gradle", "gradle.properties", "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")) {
            if (Files.exists(generated.resolve(name))) {
                Files.createDirectories(nativeRoot.resolve(name).getParent());
                Files.copy(generated.resolve(name), nativeRoot.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        String build = Files.readString(generated.resolve("build.gradle"));
        build = build.replace("apply from: 'mcreator.gradle'", "// Standalone fixture: no Copperbench metadata or runtime dependency.");
        if (fabric) build = build.replace("if (file(\"src/main/resources/META-INF/survey_pulse.classtweaker\").exists())", "if (false)");
        write(nativeRoot.resolve("build.gradle"), build);
        report.addProperty("nativeToolchainReuse", "exact rendered build configuration and stock wrapper reused for matched dependency versions; independent native source, not a from-zero timing comparison");
        write(sources(nativeRoot).resolve("SurveyPulseMod.java"), nativeMain());
        write(sources(nativeRoot).resolve("pulse_runtime.java"), runtime());
        for (String name : List.of("OreScanner.java", "ScanResult.java")) write(sources(nativeRoot).resolve(name), common(name));
        Path resources = nativeRoot.resolve("src/main/resources");
        write(resources.resolve("assets/survey_pulse/models/item/survey_wand.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/amethyst_shard\"}}");
        write(resources.resolve("assets/survey_pulse/lang/en_us.json"), "{\"item.survey_pulse.survey_wand\":\"Survey Wand\"}");
        if (modern) write(resources.resolve("assets/survey_pulse/items/survey_wand.json"), "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"survey_pulse:item/survey_wand\"}}");
        write(resources.resolve("data/survey_pulse/" + (oldest ? "recipes" : "recipe") + "/survey_wand.json"), recipe());
        if (fabric) {
            JsonObject descriptor = new JsonObject(); descriptor.addProperty("schemaVersion", 1); descriptor.addProperty("id", MODID); descriptor.addProperty("version", "1.0.0"); descriptor.addProperty("name", "Survey Pulse Native"); descriptor.addProperty("environment", "*");
            JsonObject entrypoints = new JsonObject(); JsonArray entries = new JsonArray(); entries.add(PACKAGE + ".SurveyPulseMod"); entrypoints.add("main", entries); descriptor.add("entrypoints", entrypoints);
            JsonObject depends = new JsonObject(); depends.addProperty("minecraft", track.substring("fabric-".length())); depends.addProperty("fabric-api", "*"); depends.addProperty("fabricloader", ">=0.15.11"); descriptor.add("depends", depends);
            write(resources.resolve("fabric.mod.json"), JSON.toJson(descriptor));
        } else {
            write(resources.resolve("META-INF/" + (oldest ? "mods.toml" : "neoforge.mods.toml")),
                    "modLoader=\"javafml\"\nloaderVersion=\"[" + (oldest ? "47" : "4") + ",)\"\nlicense=\"All Rights Reserved\"\n[[mods]]\nmodId=\"survey_pulse\"\nversion=\"1.0.0\"\ndisplayName=\"Survey Pulse Native\"\n");
            if (oldest) write(resources.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Survey Pulse\",\"pack_format\":15}}");
        }
    }
    static String recipe() {
        String ingredients = modern ? "[\"minecraft:amethyst_shard\",\"minecraft:copper_ingot\"]" : "[{\"item\":\"minecraft:amethyst_shard\"},{\"item\":\"minecraft:copper_ingot\"}]";
        return "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":" + ingredients + ",\"result\":{\"" + (oldest ? "item" : "id") + "\":\"survey_pulse:survey_wand\",\"count\":1}}";
    }
    static String nativeMain() {
        String base = "package " + PACKAGE + ";\nimport net.minecraft.world.item.Item;\n";
        if (fabric) {
            String idType = modern ? "Identifier" : "ResourceLocation";
            String id = oldest ? "new ResourceLocation(\"survey_pulse\", \"survey_wand\")" : idType + ".fromNamespaceAndPath(\"survey_pulse\", \"survey_wand\")";
            String registration = modern
                    ? "net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM, " + id + ", new Item(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, " + id + ")).stacksTo(1)));"
                    : "net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM, " + id + ", new Item(new Item.Properties().stacksTo(1)));";
            return base + "import net.minecraft.resources." + idType + ";\npublic final class SurveyPulseMod implements net.fabricmc.api.ModInitializer {\n @Override public void onInitialize() {\n" + registration + "\npulse_runtime.init();\n}\n}\n";
        }
        if (oldest) return base + "import net.minecraftforge.fml.common.Mod;\nimport net.minecraftforge.registries.DeferredRegister;\nimport net.minecraftforge.registries.ForgeRegistries;\n@Mod(\"survey_pulse\") public final class SurveyPulseMod {\n private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, \"survey_pulse\");\n static { ITEMS.register(\"survey_wand\", () -> new Item(new Item.Properties().stacksTo(1))); }\npublic SurveyPulseMod() { ITEMS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus()); pulse_runtime.init(); }\n}\n";
        return base + "import net.neoforged.fml.common.Mod;\nimport net.neoforged.neoforge.registries.DeferredRegister;\n@Mod(\"survey_pulse\") public final class SurveyPulseMod {\n private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(\"survey_pulse\");\n static { ITEMS.registerItem(\"survey_wand\", p -> new Item(p.stacksTo(1))); }\n public SurveyPulseMod(net.neoforged.bus.api.IEventBus bus) { ITEMS.register(bus); pulse_runtime.init(); }\n}\n";
    }
    static String runtime() {
        String retType = modern ? "InteractionResult" : "InteractionResultHolder<ItemStack>";
        String retImport = modern ? "" : "import net.minecraft.world.InteractionResultHolder;\n";
        String eventImport = fabric ? "import net.fabricmc.fabric.api.event.player.UseItemCallback;\n"
                : "import " + (oldest ? "net.minecraftforge.event.entity.player" : "net.neoforged.neoforge.event.entity.player") + ".PlayerInteractEvent;\n";
        String register = fabric ? "UseItemCallback.EVENT.register(pulse_runtime::use);"
                : (oldest ? "net.minecraftforge.common.MinecraftForge" : "net.neoforged.neoforge.common.NeoForge") + ".EVENT_BUS.addListener(pulse_runtime::onUse);";
        String neoHandler = fabric ? "" : "private static void onUse(PlayerInteractEvent.RightClickItem event) {\n if (!matches(event.getItemStack())) return;\n var result = use(event.getEntity(), event.getLevel(), event.getHand());\n event.setCanceled(true); event.setCancellationResult(" + (modern ? "result" : "result.getResult()") + ");\n}\n";
        String code = """
                package %s;
                import net.minecraft.core.BlockPos;
                import net.minecraft.core.registries.BuiltInRegistries;
                import net.minecraft.core.particles.ParticleTypes;
                import net.minecraft.network.chat.Component;
                import net.minecraft.server.level.ServerLevel;
                import net.minecraft.world.InteractionHand;
                import net.minecraft.world.InteractionResult;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.world.level.Level;
                import net.minecraft.world.entity.player.Player;
                %s%s
                public final class pulse_runtime {
                    private static boolean initialized;
                    public static void init() {
                        if (initialized) return;
                        initialized = true;
                        %s
                        System.out.println("SURVEY_PULSE_INITIALIZED track=%s");
                    }
                    private static boolean matches(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("survey_pulse:survey_wand"); }
                    %s
                    private static %s use(Player player, Level level, InteractionHand hand) {
                        ItemStack held = player.getItemInHand(hand);
                        if (!matches(held)) return %s;
                        if (player.isSpectator() || player.getCooldowns().isOnCooldown(%s)) return %s;
                        if (level instanceof ServerLevel serverLevel) {
                            BlockPos at = player.blockPosition();
                            int radius = player.isShiftKeyDown() ? 8 : 5;
                            ScanResult result = OreScanner.scan(point -> {
                                BlockPos pos = new BlockPos(point.x(), point.y(), point.z());
                                if (!serverLevel.hasChunkAt(pos)) return null;
                                return BuiltInRegistries.BLOCK.getKey(serverLevel.getBlockState(pos).getBlock()).getPath();
                            }, new ScanResult.Point(at.getX(), at.getY(), at.getZ()), radius);
                            player.displayClientMessage(Component.literal(result.message(radius)), true);
                            player.getCooldowns().addCooldown(%s, 60);
                            result.nearest().ifPresent(point -> serverLevel.sendParticles(ParticleTypes.END_ROD,
                                    point.x() + 0.5, point.y() + 1.0, point.z() + 0.5, 12, 0.25, 0.35, 0.25, 0.01));
                        }
                        return %s;
                    }
                }
                """.formatted(PACKAGE, retImport, eventImport, register, track, neoHandler, retType,
                modern ? "InteractionResult.PASS" : "InteractionResultHolder.pass(held)", modern ? "held" : "held.getItem()",
                modern ? "InteractionResult.FAIL" : "InteractionResultHolder.fail(held)", modern ? "held" : "held.getItem()",
                modern ? "InteractionResult.SUCCESS" : "InteractionResultHolder.sidedSuccess(held, level.isClientSide())");
        return modern ? code.replace("player.displayClientMessage(Component.literal(result.message(radius)), true);",
                "if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(result.message(radius)), true);") : code;
    }
    static void write(Path file, String content) throws IOException { Files.createDirectories(file.getParent()); Files.writeString(file, content); }
}
