package copperbench.acceptance;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.ScoreHolder;

/** The packaged function must change independent server state; command success is insufficient. */
public final class FunctionAcceptance {
    public FunctionAcceptance() { Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t13"), this::t13); }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("copperbench_camp_kit", path); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void write(String name, JsonObject value) {
        try { Files.writeString(Path.of(name), value.toString()); }
        catch (java.io.IOException error) { throw new AssertionError("Cannot persist function evidence", error); }
    }
    private void t13(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withLevel(h.getLevel());
        var functions = server.getFunctions();
        var function = functions.get(id("camp_signal")).orElseThrow();
        var pos = new BlockPos(12, 80, 12);
        h.getLevel().getChunk(pos); h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        require(server.getScoreboard().getObjective("camp_count") == null, "the function's scoreboard does not exist before execution");
        functions.execute(function, source);
        var objective = server.getScoreboard().getObjective("camp_count");
        require(objective != null, "the generated function creates its scoreboard objective");
        var score = server.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly("camp_probe"), objective);
        require(score != null && score.value() == 11, "the edited generated function sets the independent score to 11");
        require(h.getLevel().getBlockState(pos).is(Blocks.GOLD_BLOCK), "the generated function places the expected block");
        JsonObject effects = new JsonObject(); effects.addProperty("function", id("camp_signal").toString());
        effects.addProperty("score", score.value()); effects.addProperty("block", "minecraft:gold_block");
        effects.addProperty("position", "12,80,12"); write("function-effects.json", effects);
        // These are real game parser diagnostics, outside the single business case count.
        for (var entry : java.util.Map.of("invalid-command", "copperbench_nonexistent_command",
                "invalid-reference", "give @s copperbench_camp_kit:missing_function_item").entrySet()) {
            try {
                CommandFunction.fromLines(id(entry.getKey()), server.getCommands().getDispatcher(), source, List.of(entry.getValue()));
                throw new AssertionError("Minecraft accepted an invalid function line: " + entry.getValue());
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage() != null && expected.getMessage().contains("line 1"), "the raw parser diagnostic locates line 1");
                JsonObject negative = new JsonObject(); negative.addProperty("status", "rejected");
                negative.addProperty("input", entry.getValue()); negative.addProperty("message", expected.getMessage());
                write(entry.getKey() + ".json", negative);
                System.out.println("COPPERBENCH_FUNCTION_REJECTED " + negative);
            }
        }
        System.out.println("COPPERBENCH_FUNCTION_EFFECTS " + effects);
        h.succeed();
    }
}
