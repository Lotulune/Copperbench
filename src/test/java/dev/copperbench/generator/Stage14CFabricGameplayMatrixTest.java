package dev.copperbench.generator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Stage 14C packaged-JAR gameplay matrix for all four Fabric tracks, native and Copperbench generated. */
class Stage14CFabricGameplayMatrixTest {
    private static final String ENABLE_PROPERTY = "copperbench.stage14c.fabricGameplayMatrix";
    private static final String RESUME_PROPERTY = "copperbench.stage14c.fabricGameplayMatrixResume";
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration SERVER_TIMEOUT = Duration.ofMinutes(5);

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "true")
    void packagedGameplayAcrossFabricMatrix() throws Exception {
        Path repository = Path.of(".").toAbsolutePath().normalize();
        Path fixtureRoot = repository.resolve(".tmp/stage14-track-audit/projects");
        Path evidenceRoot = repository.resolve("evidence/stage14/2026-09-08/fabric-gameplay-matrix");
        Path hostRoot = repository.resolve("build/stage14c-fabric-gameplay-matrix");
        Path temp = repository.resolve("t/stage14c-fabric-gameplay-matrix");
        Files.createDirectories(evidenceRoot);
        Files.createDirectories(temp);

        boolean resume = Boolean.parseBoolean(System.getProperty(RESUME_PROPERTY, "false"));
        List<CellResult> results = new ArrayList<>();
        for (FabricTrack track : tracks(repository)) {
            for (String variant : List.of("native", "copperbench")) {
                CellResult existing = resume ? readPassedCell(evidenceRoot, track.id(), variant) : null;
                results.add(existing != null ? existing
                        : runCell(repository, fixtureRoot, evidenceRoot, hostRoot, temp, track, variant));
            }
        }

        JsonArray cells = new JsonArray();
        List<String> failures = new ArrayList<>();
        for (CellResult result : results) {
            cells.add(result.evidence());
            if (!result.passed()) failures.add(result.track() + "/" + result.variant() + ": " + result.failure());
        }
        JsonObject matrix = new JsonObject();
        matrix.addProperty("schemaVersion", "1.0");
        matrix.addProperty("kind", "stage14c-fabric-packaged-gameplay-matrix");
        matrix.addProperty("eulaAcceptedByHarness", false);
        matrix.addProperty("cellCount", results.size());
        matrix.addProperty("passedCount", results.stream().filter(CellResult::passed).count());
        matrix.add("cells", cells);
        matrix.addProperty("passed", failures.isEmpty());
        matrix.addProperty("completedAt", Instant.now().toString());
        Files.writeString(evidenceRoot.resolve("matrix.json"),
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(matrix),
                StandardCharsets.UTF_8);
        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }

    private static CellResult readPassedCell(Path evidenceRoot, String track, String variant) {
        Path json = evidenceRoot.resolve(track).resolve(variant + ".json");
        if (!Files.isRegularFile(json)) return null;
        try {
            JsonObject evidence = com.google.gson.JsonParser.parseString(Files.readString(json)).getAsJsonObject();
            if (!evidence.has("passed") || !evidence.get("passed").getAsBoolean()) return null;
            if (!evidence.has("behaviorVerified") || !evidence.get("behaviorVerified").getAsBoolean()) return null;
            return new CellResult(track, variant, true, "", evidence);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static CellResult runCell(Path repository, Path fixtureRoot, Path evidenceRoot, Path hostRoot,
            Path temp, FabricTrack track, String variant) throws Exception {
        Instant started = Instant.now();
        Path source = fixtureRoot.resolve(track.id()).resolve(variant);
        Path cellDir = evidenceRoot.resolve(track.id());
        Path json = cellDir.resolve(variant + ".json");
        Path log = cellDir.resolve(variant + ".log");
        Path host = hostRoot.resolve(track.id()).resolve(variant);
        Files.createDirectories(cellDir);

        ProcessRun build = null;
        ProcessRun runtime = null;
        Path deployedJar = null;
        String output = "";
        String failure = "";
        boolean passed = false;
        try {
            require(Files.isDirectory(source), "missing fixture source: " + source);
            build = runGradle(source, track.gradleHome(), track.javaHome(), temp, BUILD_TIMEOUT,
                    List.of(track.packageTask()), false);
            require(!build.timedOut(), track.packageTask() + " timed out");
            require(build.exitCode() == 0, diagnosticTail(build.output()));
            Path packaged = findPackagedJar(source.resolve("build/libs"));
            require(packaged != null, "packaged Survey Pulse JAR missing");

            prepareHost(source, host, track);
            Path run = host.resolve("run");
            Files.createDirectories(run.resolve("mods"));
            deployedJar = run.resolve("mods/survey_pulse-1.0.jar");
            Files.copy(packaged, deployedJar);
            require(!eulaAccepted(run), "harness must not pre-accept EULA");
            require(!Files.exists(host.resolve("src/main/java/dev/example/surveypulse")),
                    "packaged host contains Survey Pulse source");

            runtime = runGradle(host, track.gradleHome(), track.javaHome(), temp, SERVER_TIMEOUT,
                    List.of("runServer"), true);
            output = runtime.output();
            require(!runtime.timedOut(), diagnosticTail(output));
            require(runtime.exitCode() == 0, diagnosticTail(output));
            require(output.contains("SURVEY_PULSE_INITIALIZED track=" + track.id()), diagnosticTail(output));
            require(output.contains("Started game test server"), diagnosticTail(output));
            require(output.contains("STAGE14C_GAMEPLAY_VERIFIED track=" + track.id()), diagnosticTail(output));
            require(requiredTestsPassed(output), diagnosticTail(output));
            require(!fatalSeen(output), diagnosticTail(output));
            require(!eulaAccepted(run), "GameTest run accepted EULA");
            passed = true;
        } catch (Throwable problem) {
            failure = problem.getClass().getSimpleName() + ": " + String.valueOf(problem.getMessage());
        } finally {
            if (runtime != null) output = runtime.output();
            Files.writeString(log, output, StandardCharsets.UTF_8);
            JsonObject evidence = evidence(repository, started, track, variant, source, host, deployedJar,
                    build, runtime, output, failure, passed, log);
            Files.writeString(json,
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(evidence),
                    StandardCharsets.UTF_8);
        }
        return new CellResult(track.id(), variant, passed, failure,
                com.google.gson.JsonParser.parseString(Files.readString(json)).getAsJsonObject());
    }

    private static JsonObject evidence(Path repository, Instant started, FabricTrack track, String variant,
            Path source, Path host, Path jar, ProcessRun build, ProcessRun runtime, String output,
            String failure, boolean passed, Path log) {
        boolean jarPresent = jar != null && Files.isRegularFile(jar);
        JsonObject root = new JsonObject();
        root.addProperty("track", track.id());
        root.addProperty("variant", variant);
        root.addProperty("minecraftVersion", track.minecraftVersion());
        root.addProperty("loaderVersion", track.loaderVersion());
        root.addProperty("fabricApiVersion", track.fabricApiVersion());
        root.addProperty("javaHome", track.javaHome().toString());
        root.addProperty("gradleHome", track.gradleHome().toString());
        root.addProperty("prepared", Files.isDirectory(source));
        root.addProperty("compiled", build != null && build.exitCode() == 0 && !build.timedOut());
        root.addProperty("initializerExecuted", output.contains("SURVEY_PULSE_INITIALIZED track=" + track.id()));
        root.addProperty("eulaBoundaryStatus", "not_applicable_game_test_bypass");
        root.addProperty("eulaAcceptedByHarness", false);
        root.addProperty("serverReady", output.contains("Started game test server"));
        root.addProperty("serverReadyMode", "fabric_headless_gametest_dedicated");
        root.addProperty("packagedJarLoaded", jarPresent && output.contains("SURVEY_PULSE_INITIALIZED track=" + track.id()));
        root.addProperty("behaviorVerified", output.contains("STAGE14C_GAMEPLAY_VERIFIED track=" + track.id()));
        root.addProperty("rightClickVerified", output.contains("rightClick=true"));
        root.addProperty("normalRadius", 5);
        root.addProperty("sneakRadius", 8);
        root.addProperty("cooldownTicks", 60);
        root.addProperty("spectatorRejected", output.contains("spectator=true"));
        root.addProperty("multiplayerIsolation", output.contains("multiplayerIsolation=true"));
        root.addProperty("hostContainsSurveySources", Files.exists(host.resolve("src/main/java/dev/example/surveypulse")));
        root.addProperty("runtimeExitCode", runtime == null ? -1 : runtime.exitCode());
        root.addProperty("runtimeTimedOut", runtime == null || runtime.timedOut());
        root.addProperty("fatalSeen", fatalSeen(output));
        if (jarPresent) {
            try { root.addProperty("packagedJarSha256", sha256(jar)); } catch (Exception ignored) {}
        }
        root.addProperty("logFile", repository.relativize(log).toString().replace('\\', '/'));
        root.addProperty("durationSeconds", Duration.between(started, Instant.now()).toMillis() / 1000.0);
        root.addProperty("failure", failure);
        root.addProperty("passed", passed);
        root.addProperty("completedAt", Instant.now().toString());
        return root;
    }

    private static void prepareHost(Path source, Path host, FabricTrack track) throws IOException {
        deleteRecursively(host);
        Files.createDirectories(host.resolve("gradle/wrapper"));
        Files.createDirectories(host.resolve("src/main/java/dev/copperbench/stage14c"));
        Files.createDirectories(host.resolve("src/main/resources"));
        Files.copy(source.resolve("gradlew.bat"), host.resolve("gradlew.bat"));
        Files.copy(source.resolve("gradlew"), host.resolve("gradlew"));
        Files.copy(source.resolve("gradle/wrapper/gradle-wrapper.jar"), host.resolve("gradle/wrapper/gradle-wrapper.jar"));
        Files.copy(source.resolve("gradle/wrapper/gradle-wrapper.properties"), host.resolve("gradle/wrapper/gradle-wrapper.properties"));
        Files.writeString(host.resolve("settings.gradle"), """
                pluginManagement { repositories { maven { url = 'https://maven.fabricmc.net/' }; mavenCentral(); gradlePluginPortal() } }
                rootProject.name = 'stage14c-fabric-gameplay-host'
                """, StandardCharsets.UTF_8);
        Files.writeString(host.resolve("gradle.properties"), """
                org.gradle.jvmargs=-Xmx1G -Dfile.encoding=UTF-8 -Duser.language=en
                org.gradle.parallel=false
                org.gradle.configuration-cache=false
                """, StandardCharsets.UTF_8);
        String mappings = track.officialMappings() ? "    mappings loom.officialMojangMappings()\n" : "";
        Files.writeString(host.resolve("build.gradle"), """
                plugins { id '%s' version '%s' }
                dependencies {
                    minecraft 'com.mojang:minecraft:%s'
                %s    %s 'net.fabricmc:fabric-loader:%s'
                    %s 'net.fabricmc.fabric-api:fabric-api:%s'
                }
                tasks.withType(JavaCompile).configureEach { options.release = %d }
                """.formatted(track.pluginId(), track.pluginVersion(), track.minecraftVersion(), mappings,
                        track.dependencyConfiguration(), track.loaderVersion(), track.dependencyConfiguration(),
                        track.fabricApiVersion(), track.javaRelease()), StandardCharsets.UTF_8);
        Files.writeString(host.resolve("src/main/java/dev/copperbench/stage14c/HostGameTest.java"),
                gameplaySource(track), StandardCharsets.UTF_8);
        Files.writeString(host.resolve("src/main/resources/fabric.mod.json"), """
                {
                  "schemaVersion": 1,
                  "id": "stage14c_host",
                  "version": "1.0.0",
                  "name": "Stage 14C Gameplay Host",
                  "environment": "server",
                  "entrypoints": { "fabric-gametest": ["dev.copperbench.stage14c.HostGameTest"] },
                  "depends": { "fabricloader": ">=%s", "minecraft": "%s", "fabric-api": "*" }
                }
                """.formatted(track.loaderVersion(), track.minecraftVersion()), StandardCharsets.UTF_8);
    }

    private static String gameplaySource(FabricTrack track) {
        if (track.minecraftVersion().equals("1.20.1")) return gameplay1201(track.id());
        if (track.minecraftVersion().equals("1.21.1")) return gameplay1211(track.id());
        return gameplay26(track.id());
    }

    private static String gameplay1201(String track) {
        return """
                package dev.copperbench.stage14c;
                import java.util.UUID;
                import com.mojang.authlib.GameProfile;
                import io.netty.channel.embedded.EmbeddedChannel;
                import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
                import net.minecraft.core.BlockPos;
                import net.minecraft.core.registries.BuiltInRegistries;
                import net.minecraft.gametest.framework.GameTest;
                import net.minecraft.gametest.framework.GameTestHelper;
                import net.minecraft.network.Connection;
                import net.minecraft.network.chat.Component;
                import net.minecraft.network.protocol.PacketFlow;
                import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
                import net.minecraft.resources.ResourceLocation;
                import net.minecraft.server.level.ServerPlayer;
                import net.minecraft.world.InteractionHand;
                import net.minecraft.world.item.Item;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.world.level.block.Blocks;

                public final class HostGameTest implements FabricGameTest {
                    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 140)
                    public void gameplay(GameTestHelper helper) {
                        BlockPos base = helper.absolutePos(new BlockPos(1,2,1)); BlockPos origin = new BlockPos(base.getX(),250,base.getZ());
                        BlockPos near=origin.offset(4,0,0), far=origin.offset(7,0,0); var bn=helper.getLevel().getBlockState(near); var bf=helper.getLevel().getBlockState(far);
                        helper.getLevel().setBlock(near,Blocks.DIAMOND_ORE.defaultBlockState(),3); helper.getLevel().setBlock(far,Blocks.GOLD_ORE.defaultBlockState(),3);
                        Item wand=BuiltInRegistries.ITEM.get(new ResourceLocation("survey_pulse","survey_wand")); require(BuiltInRegistries.ITEM.getKey(wand).toString().equals("survey_pulse:survey_wand"),"wand missing");
                        Probe first=connect(helper,"first",false), second=connect(helper,"second",false), spectator=connect(helper,"spectator",true);
                        for(Probe p:new Probe[]{first,second,spectator}){p.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(wand));p.last="";}
                        first.setShiftKeyDown(false); use(first,0); require(first.last.contains("1 ore blocks"),"normal radius: "+first.last); require(first.getCooldowns().isOnCooldown(wand),"first cooldown"); String before=first.last; use(first,1); require(first.last.equals(before),"repeat not blocked");
                        require(!second.getCooldowns().isOnCooldown(wand),"cooldown leaked"); second.setShiftKeyDown(true); use(second,0); require(second.last.contains("2 ore blocks"),"sneak radius: "+second.last); require(second.getCooldowns().isOnCooldown(wand),"second cooldown"); require(first.getCooldowns().isOnCooldown(wand),"first cooldown changed");
                        use(spectator,0); require(spectator.last.isEmpty(),"spectator message"); require(!spectator.getCooldowns().isOnCooldown(wand),"spectator cooldown");
                        helper.runAfterDelay(62,()->{require(!first.getCooldowns().isOnCooldown(wand),"cooldown did not expire");first.setShiftKeyDown(true);use(first,2);require(first.last.contains("2 ore blocks"),"post cooldown");System.out.println("STAGE14C_GAMEPLAY_VERIFIED track=%s rightClick=true normalRadius=5 sneakRadius=8 cooldown60=true spectator=true multiplayerIsolation=true");helper.getLevel().setBlock(near,bn,3);helper.getLevel().setBlock(far,bf,3);helper.succeed();});
                    }
                    private static Probe connect(GameTestHelper h,String n,boolean s){Probe p=new Probe(h,n,s);Connection c=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(c);h.getLevel().getServer().getPlayerList().placeNewPlayer(c,p);h.getLevel().getServer().getConnection().getConnections().add(c);return p;}
                    private static void use(Probe p,int seq){p.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND,seq));}
                    private static void require(boolean c,String m){if(!c)throw new IllegalStateException(m);}
                    private static final class Probe extends ServerPlayer{final boolean spectator;String last="";Probe(GameTestHelper h,String n,boolean s){super(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"stage14c-"+n));spectator=s;}@Override public boolean isSpectator(){return spectator;}@Override public void displayClientMessage(Component c,boolean o){if(o)last=c.getString();}}
                }
                """.formatted(track);
    }

    private static String gameplay1211(String track) {
        return """
                package dev.copperbench.stage14c;
                import java.util.UUID;
                import com.mojang.authlib.GameProfile;
                import io.netty.channel.embedded.EmbeddedChannel;
                import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
                import net.minecraft.core.BlockPos;
                import net.minecraft.core.registries.BuiltInRegistries;
                import net.minecraft.gametest.framework.GameTest;
                import net.minecraft.gametest.framework.GameTestHelper;
                import net.minecraft.network.Connection;
                import net.minecraft.network.chat.Component;
                import net.minecraft.network.protocol.PacketFlow;
                import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
                import net.minecraft.resources.ResourceLocation;
                import net.minecraft.server.level.ServerPlayer;
                import net.minecraft.server.network.CommonListenerCookie;
                import net.minecraft.world.InteractionHand;
                import net.minecraft.world.item.Item;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.world.level.block.Blocks;

                public final class HostGameTest implements FabricGameTest {
                    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 140)
                    public void gameplay(GameTestHelper helper) {
                        BlockPos base=helper.absolutePos(new BlockPos(1,2,1));BlockPos origin=new BlockPos(base.getX(),250,base.getZ());BlockPos near=origin.offset(4,0,0),far=origin.offset(7,0,0);var bn=helper.getLevel().getBlockState(near);var bf=helper.getLevel().getBlockState(far);helper.getLevel().setBlock(near,Blocks.DIAMOND_ORE.defaultBlockState(),3);helper.getLevel().setBlock(far,Blocks.GOLD_ORE.defaultBlockState(),3);
                        Item wand=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("survey_pulse","survey_wand"));require(BuiltInRegistries.ITEM.getKey(wand).toString().equals("survey_pulse:survey_wand"),"wand missing");
                        Probe first=connect(helper,"first",false),second=connect(helper,"second",false),spectator=connect(helper,"spectator",true);for(Probe p:new Probe[]{first,second,spectator}){p.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(wand));p.last="";}
                        first.setShiftKeyDown(false);use(first,0);require(first.last.contains("1 ore blocks"),"normal radius: "+first.last);require(first.getCooldowns().isOnCooldown(wand),"first cooldown");String before=first.last;use(first,1);require(first.last.equals(before),"repeat not blocked");require(!second.getCooldowns().isOnCooldown(wand),"cooldown leaked");second.setShiftKeyDown(true);use(second,0);require(second.last.contains("2 ore blocks"),"sneak radius: "+second.last);require(second.getCooldowns().isOnCooldown(wand),"second cooldown");require(first.getCooldowns().isOnCooldown(wand),"first cooldown changed");use(spectator,0);require(spectator.last.isEmpty(),"spectator message");require(!spectator.getCooldowns().isOnCooldown(wand),"spectator cooldown");
                        helper.runAfterDelay(62,()->{require(!first.getCooldowns().isOnCooldown(wand),"cooldown did not expire");first.setShiftKeyDown(true);use(first,2);require(first.last.contains("2 ore blocks"),"post cooldown");System.out.println("STAGE14C_GAMEPLAY_VERIFIED track=%s rightClick=true normalRadius=5 sneakRadius=8 cooldown60=true spectator=true multiplayerIsolation=true");helper.getLevel().setBlock(near,bn,3);helper.getLevel().setBlock(far,bf,3);helper.succeed();});
                    }
                    private static Probe connect(GameTestHelper h,String n,boolean s){GameProfile gp=new GameProfile(UUID.randomUUID(),"stage14c-"+n);CommonListenerCookie cookie=CommonListenerCookie.createInitial(gp,false);Probe p=new Probe(h,gp,cookie,s);Connection c=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(c);h.getLevel().getServer().getPlayerList().placeNewPlayer(c,p,cookie);h.getLevel().getServer().getConnection().getConnections().add(c);return p;}
                    private static void use(Probe p,int seq){p.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND,seq,p.getYRot(),p.getXRot()));}
                    private static void require(boolean c,String m){if(!c)throw new IllegalStateException(m);}
                    private static final class Probe extends ServerPlayer{final boolean spectator;String last="";Probe(GameTestHelper h,GameProfile gp,CommonListenerCookie cookie,boolean s){super(h.getLevel().getServer(),h.getLevel(),gp,cookie.clientInformation());spectator=s;}@Override public boolean isSpectator(){return spectator;}@Override public void displayClientMessage(Component c,boolean o){if(o)last=c.getString();}}
                }
                """.formatted(track);
    }

    private static String gameplay26(String track) {
        return """
                package dev.copperbench.stage14c;
                import java.util.UUID;
                import com.mojang.authlib.GameProfile;
                import io.netty.channel.embedded.EmbeddedChannel;
                import net.fabricmc.fabric.api.gametest.v1.GameTest;
                import net.minecraft.core.BlockPos;
                import net.minecraft.core.registries.BuiltInRegistries;
                import net.minecraft.gametest.framework.GameTestHelper;
                import net.minecraft.network.Connection;
                import net.minecraft.network.chat.Component;
                import net.minecraft.network.protocol.PacketFlow;
                import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
                import net.minecraft.resources.Identifier;
                import net.minecraft.server.level.ServerPlayer;
                import net.minecraft.server.network.CommonListenerCookie;
                import net.minecraft.world.InteractionHand;
                import net.minecraft.world.item.Item;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.world.level.block.Blocks;

                public final class HostGameTest {
                    @GameTest(maxTicks = 140)
                    public void gameplay(GameTestHelper helper) {
                        BlockPos base=helper.absolutePos(new BlockPos(1,2,1));BlockPos origin=new BlockPos(base.getX(),250,base.getZ());BlockPos near=origin.offset(4,0,0),far=origin.offset(7,0,0);var bn=helper.getLevel().getBlockState(near);var bf=helper.getLevel().getBlockState(far);helper.getLevel().setBlock(near,Blocks.DIAMOND_ORE.defaultBlockState(),3);helper.getLevel().setBlock(far,Blocks.GOLD_ORE.defaultBlockState(),3);
                        Item wand=BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("survey_pulse","survey_wand"));require(BuiltInRegistries.ITEM.getKey(wand).toString().equals("survey_pulse:survey_wand"),"wand missing");
                        Probe first=connect(helper,"first",false),second=connect(helper,"second",false),spectator=connect(helper,"spectator",true);for(Probe p:new Probe[]{first,second,spectator}){p.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(wand));p.last="";}
                        first.setShiftKeyDown(false);use(first,0);require(first.last.contains("1 ore blocks"),"normal radius: "+first.last);require(first.getCooldowns().isOnCooldown(first.getItemInHand(InteractionHand.MAIN_HAND)),"first cooldown");String before=first.last;use(first,1);require(first.last.equals(before),"repeat not blocked");require(!second.getCooldowns().isOnCooldown(second.getItemInHand(InteractionHand.MAIN_HAND)),"cooldown leaked");second.setShiftKeyDown(true);use(second,0);require(second.last.contains("2 ore blocks"),"sneak radius: "+second.last);require(second.getCooldowns().isOnCooldown(second.getItemInHand(InteractionHand.MAIN_HAND)),"second cooldown");require(first.getCooldowns().isOnCooldown(first.getItemInHand(InteractionHand.MAIN_HAND)),"first cooldown changed");use(spectator,0);require(spectator.last.isEmpty(),"spectator message");require(!spectator.getCooldowns().isOnCooldown(spectator.getItemInHand(InteractionHand.MAIN_HAND)),"spectator cooldown");
                        helper.runAfterDelay(62,()->{require(!first.getCooldowns().isOnCooldown(first.getItemInHand(InteractionHand.MAIN_HAND)),"cooldown did not expire");first.setShiftKeyDown(true);use(first,2);require(first.last.contains("2 ore blocks"),"post cooldown");System.out.println("STAGE14C_GAMEPLAY_VERIFIED track=%s rightClick=true normalRadius=5 sneakRadius=8 cooldown60=true spectator=true multiplayerIsolation=true");helper.getLevel().setBlock(near,bn,3);helper.getLevel().setBlock(far,bf,3);helper.succeed();});
                    }
                    private static Probe connect(GameTestHelper h,String n,boolean s){GameProfile gp=new GameProfile(UUID.randomUUID(),"stage14c-"+n);CommonListenerCookie cookie=CommonListenerCookie.createInitial(gp,false);Probe p=new Probe(h,gp,cookie,s);Connection c=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(c);h.getLevel().getServer().getPlayerList().placeNewPlayer(c,p,cookie);h.getLevel().getServer().getConnection().getConnections().add(c);for(int i=0;i<60&&!p.connection.hasClientLoaded();i++)p.connection.tickClientLoadTimeout();require(p.connection.hasClientLoaded(),"embedded client not loaded");return p;}
                    private static void use(Probe p,int seq){p.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND,seq,p.getYRot(),p.getXRot()));}
                    private static void require(boolean c,String m){if(!c)throw new IllegalStateException(m);}
                    private static final class Probe extends ServerPlayer{final boolean spectator;String last="";Probe(GameTestHelper h,GameProfile gp,CommonListenerCookie cookie,boolean s){super(h.getLevel().getServer(),h.getLevel(),gp,cookie.clientInformation());spectator=s;}@Override public boolean isSpectator(){return spectator;}@Override public void sendSystemMessage(Component c,boolean o){if(o)last=c.getString();}}
                }
                """.formatted(track);
    }

    private static ProcessRun runGradle(Path project, Path gradleHome, Path javaHome, Path temp, Duration timeout,
            List<String> tasks, boolean gameTest) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("cmd.exe"); command.add("/c"); command.add("gradlew.bat"); command.add("--no-daemon"); command.add("--console=plain"); command.addAll(tasks);
        ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", javaHome.toString());
        builder.environment().put("GRADLE_USER_HOME", gradleHome.toString());
        builder.environment().put("TEMP", temp.toString()); builder.environment().put("TMP", temp.toString());
        if (gameTest) builder.environment().put("JAVA_TOOL_OPTIONS", "-Dfabric-api.gametest=1 -Dfabric-api.gametest.report-file=run/gametest-results.xml");
        Process process = builder.start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> { try { return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8); } catch (IOException e) { throw new IllegalStateException(e); } });
        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
        return new ProcessRun(completed ? process.exitValue() : -1, !completed, output.get(30, TimeUnit.SECONDS));
    }

    private static List<FabricTrack> tracks(Path repository) {
        return List.of(
            new FabricTrack("fabric-1.20.1","1.20.1","0.15.11","0.92.2+1.20.1","fabric-loom","1.7.4","modImplementation","remapJar",true,17,repository.resolve("jdk/jdk21_win_64"),repository.resolve("build/stage8-workspace-generator-gradle/fabric-1_20_1")),
            new FabricTrack("fabric-1.21.1","1.21.1","0.19.3","0.116.15+1.21.1","net.fabricmc.fabric-loom-remap","1.17.19","modImplementation","remapJar",true,21,repository.resolve("jdk/jdk21_win_64"),repository.resolve("build/stage8-workspace-generator-gradle/fabric-1_21_1")),
            new FabricTrack("fabric-26.1.2","26.1.2","0.19.3","0.155.2+26.1.2","net.fabricmc.fabric-loom","1.17-SNAPSHOT","implementation","jar",false,25,repository.resolve("jdk/jbr25_win_64"),repository.resolve("build/stage8-workspace-generator-gradle/fabric-26_1_2")),
            new FabricTrack("fabric-26.2","26.2","0.19.3","0.158.0+26.2","net.fabricmc.fabric-loom","1.17-SNAPSHOT","implementation","jar",false,25,repository.resolve("jdk/jbr25_win_64"),repository.resolve("build/stage8-workspace-generator-gradle/fabric-26_2")));
    }

    private static Path findPackagedJar(Path libs) throws IOException {
        if (!Files.isDirectory(libs)) return null;
        try (Stream<Path> entries = Files.list(libs)) {
            return entries.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().contains("sources")).filter(p -> !p.getFileName().toString().contains("dev-shadow"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
        }
    }
    private static boolean eulaAccepted(Path run) throws IOException { Path e=run.resolve("eula.txt"); return Files.isRegularFile(e) && Files.readString(e).lines().map(String::trim).anyMatch(l -> l.equalsIgnoreCase("eula=true")); }
    private static boolean requiredTestsPassed(String output) { return output != null && output.matches("(?s).*All [1-9][0-9]* required tests passed :\\).*?"); }
    private static boolean fatalSeen(String output) { String l=output==null?"":output.toLowerCase(Locale.ROOT); return l.contains("crash report")||l.contains("failed to start the minecraft server")||l.contains("exception in server tick loop"); }
    private static String sha256(Path path) throws Exception { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static String diagnosticTail(String output) { return output==null?"":output.length()<=8000?output:output.substring(output.length()-8000); }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void deleteRecursively(Path root) throws IOException { if(!Files.exists(root))return; try(Stream<Path> paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);} }
    private record FabricTrack(String id,String minecraftVersion,String loaderVersion,String fabricApiVersion,String pluginId,String pluginVersion,String dependencyConfiguration,String packageTask,boolean officialMappings,int javaRelease,Path javaHome,Path gradleHome){}
    private record ProcessRun(int exitCode,boolean timedOut,String output){}
    private record CellResult(String track,String variant,boolean passed,String failure,JsonObject evidence){}
}
