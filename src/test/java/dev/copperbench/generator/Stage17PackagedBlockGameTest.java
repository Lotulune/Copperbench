package dev.copperbench.generator;

import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only explicitly requested acceptance against the exact structured-build JARs. */
@EnabledIfSystemProperty(named="copperbench.stage17.gameTest", matches="true")
class Stage17PackagedBlockGameTest {
    @ParameterizedTest @ValueSource(strings={"fabric-1.21.1", "neoforge-1.21.1"})
    void packagedBlockBehavior(String track) throws Exception {
        Path repo = Path.of(".").toAbsolutePath().normalize();
        Path evidence = repo.resolve("build/stage17-structured-block-builds/" + track);
        Path workspace;
        try (var paths = Files.list(evidence)) {
            workspace = paths.filter(p -> p.getFileName().toString().startsWith("workspace-"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElseThrow();
        }
        if (Files.isRegularFile(workspace.resolve("build/stage17-evidence/sha256.txt")))
            evidence = workspace.resolve("build/stage17-evidence");
        boolean fabric = track.startsWith("fabric");
        var environment = fabric ? new Fabric1211Generator(repo).gameTestEnvironment()
                : new NeoForge1211Generator(repo, NeoForge1211Generator.Profile.NEOFORGE_1211).gameTestEnvironment();
        GameTestSupport.prepare(workspace, environment, "structured_forge");
        Path tests = workspace.resolve("src/gametest/java/copperbench/acceptance/AcceptanceTests.java");
        Files.writeString(tests, source(fabric));
        var config = GameTestSupport.configuration(workspace);
        Path hostRoot = repo.resolve(".tmp/stage17-gt/" + UUID.randomUUID().toString().substring(0,8));
        var host = GameTestSupport.host(workspace, hostRoot, config, environment);
        Path example = hostRoot.resolve("src/main/java/copperbench/example/Stage17MachineRuntime.java");
        Files.createDirectories(example.getParent());
        Files.writeString(example, Files.readString(repo.resolve("examples/agent-native/lifecycle-1.21.1/"
                + (fabric ? "fabric" : "neoforge") + ".java.template"))
                .replace("__PACKAGE__", "copperbench.example").replace("__BLOCK_ID__", "structured_forge:resonance_forge"));
        assertEquals(Files.readString(evidence.resolve("sha256.txt")), host.jarSha256(), "Must test the previously built exact JAR");
        Instant start = Instant.now();
        var log = new StringBuilder();
        var arguments = new ArrayList<>(List.of("runGameTest", "--console=plain", "-g",
                net.mcreator.io.UserFolderManager.getGradleHome().getAbsolutePath()));
        // A previously generated same-version catalog can avoid unrelated client asset downloads.
        String assetProperties = System.getProperty("copperbench.stage17.assetProperties", "");
        if (!fabric && !assetProperties.isBlank()) {
            var properties = new Properties();
            try (var input = Files.newInputStream(Path.of(assetProperties))) { properties.load(input); }
            assertEquals("17", properties.getProperty("asset_index"), "Minecraft 1.21.1 catalog required");
            assertTrue(Files.isRegularFile(Path.of(properties.getProperty("assets_root"), "indexes/17.json")));
            Files.createDirectories(hostRoot.resolve("build/moddev"));
            Files.copy(Path.of(assetProperties), hostRoot.resolve("build/moddev/minecraft_assets.properties"));
            arguments.addAll(List.of("-x", "downloadAssets"));
        }
        var result = Fabric1211ProcessRunner.system("unused", repo.resolve("jdk/jdk21_win_64"))
                .run(hostRoot, arguments,
                        Duration.ofMinutes(15), line -> log.append(line).append('\n'));
        Files.writeString(evidence.resolve("gametest.log"), log);
        var report = GameTestReport.read(host.report(), start, 4);
        report.addProperty("artifactPath", host.jar().toString()); report.addProperty("artifactSha256", host.jarSha256());
        report.add("environment", environment); report.addProperty("hostRoot", hostRoot.toString());
        report.addProperty("eulaAcceptedByHarness", false); // 1.21.1's GameTest server does not require writing eula.txt.
        Files.writeString(evidence.resolve("gametest.json"), report.toString());
        if (Files.exists(host.report())) Files.copy(host.report(), evidence.resolve("gametest.xml"), StandardCopyOption.REPLACE_EXISTING);
        assertEquals(0, result.exitCode(), log.toString());
        assertEquals("passed", report.get("status").getAsString(), report.toString());
        assertEquals(4, report.get("acceptanceExecuted").getAsInt());
    }

    private static String source(boolean fabric) {
        String annotation = fabric ? "@GameTest(template=\"fabric-gametest-api-v1:empty\")"
                : "@GameTest(template=\"woodland_mansion/carpet_north\")";
        String header = fabric ? "implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest"
                : "";
        String holder = fabric ? "" : "@net.neoforged.neoforge.gametest.GameTestHolder(\"minecraft\")\n"
                + "@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)";
        return """
            package copperbench.acceptance;
            import net.minecraft.gametest.framework.*;
            import net.minecraft.core.*;
            import net.minecraft.core.registries.BuiltInRegistries;
            import net.minecraft.resources.ResourceLocation;
            import net.minecraft.world.*;
            import net.minecraft.world.item.*;
            import net.minecraft.world.item.context.BlockPlaceContext;
            import net.minecraft.world.level.block.*;
            import net.minecraft.world.level.block.entity.BlockEntity;
            import net.minecraft.world.level.block.state.BlockState;
            import net.minecraft.world.entity.item.ItemEntity;
            import net.minecraft.world.phys.*;
            %s
            public final class AcceptanceTests %s {
                private static final BlockPos POS = new BlockPos(1,2,1);
                private static Block block() {
                    var id = ResourceLocation.parse("structured_forge:resonance_forge");
                    if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("Structured block was not registered");
                    return BuiltInRegistries.BLOCK.get(id);
                }
                private static BlockState place(GameTestHelper h) {
                    h.setBlock(POS, block().defaultBlockState()); return h.getBlockState(POS);
                }
                %s public static void hardnessAndCollision(GameTestHelper h) {
                    var state = place(h); var pos = h.absolutePos(POS);
                    h.assertTrue(state.getDestroySpeed(h.getLevel(),pos) == 5, "Updated hardness was not persisted into JAR");
                    h.assertTrue(block().getExplosionResistance() == 6, "Resistance differs");
                    h.assertTrue(!state.getCollisionShape(h.getLevel(),pos).isEmpty(), "Default collision shape disappeared");
                    h.succeed();
                }
                %s public static void placementAndRotation(GameTestHelper h) {
                    var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); player.setYRot(90);
                    var pos = h.absolutePos(POS);
                    var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(block()),
                            new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
                    var state = block().getStateForPlacement(context);
                    h.assertTrue(state.getValue(HorizontalDirectionalBlock.FACING) == player.getDirection().getOpposite(), "Placement facing differs");
                    var rotated = state.rotate(Rotation.CLOCKWISE_90);
                    h.assertTrue(rotated.getValue(HorizontalDirectionalBlock.FACING) == state.getValue(HorizontalDirectionalBlock.FACING).getClockWise(), "Rotation did not update facing");
                    h.succeed();
                }
                %s public static void inventorySurvivesSerialization(GameTestHelper h) {
                    var state = place(h); var entity = h.getBlockEntity(POS);
                    h.assertTrue(entity instanceof Container, "Inventory block entity absent");
                    var inventory = (Container)entity;
                    h.assertTrue(inventory.getContainerSize() == 3, "Expected three slots");
                    h.assertTrue(inventory.getMaxStackSize() == 64, "Stack limit differs");
                    inventory.setItem(2,new ItemStack(Items.DIAMOND,7));
                    var nbt = entity.saveWithFullMetadata(h.getLevel().registryAccess());
                    var restored = BlockEntity.loadStatic(h.absolutePos(POS),state,nbt,h.getLevel().registryAccess());
                    h.assertTrue(restored instanceof Container && ((Container)restored).getItem(2).is(Items.DIAMOND)
                            && ((Container)restored).getItem(2).getCount()==7, "Inventory did not survive serialization");
                    h.succeed();
                }
                %s public static void breakingDropsInventory(GameTestHelper h) {
                    place(h); var inventory = (Container)h.getBlockEntity(POS);
                    inventory.setItem(0,new ItemStack(Items.DIAMOND,7));
                    var pos = h.absolutePos(POS); h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                    int dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).stream()
                            .filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
                    h.assertTrue(dropped==7, "Inventory drop count differs: " + dropped); h.succeed();
                }
            }
            """.formatted(holder, header, annotation, annotation, annotation, annotation);
    }
}
