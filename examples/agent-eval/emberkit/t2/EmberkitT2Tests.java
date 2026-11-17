package copperbench.eval;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * T2 evaluation. Uses the server's own item-use path with a survival player, so durability, cooldown and messages
 * behave as for a real player. Each behaviour runs in its own batch so no other test's entities are in range.
 */
public final class EmberkitT2Tests implements FabricGameTest {
    private static final ResourceLocation LANTERN = ResourceLocation.fromNamespaceAndPath("emberkit", "ember_lantern");
    private static final Vec3 PLAYER_AT = new Vec3(2.5, 2, 2.5);

    @FunctionalInterface private interface Body { void run(List<Entity> spawned) throws Exception; }

    /** Records system and action-bar messages; displayClientMessage reaches sendSystemMessage on the server. */
    private static final class RecordingPlayer extends FakePlayer {
        Component message;
        boolean overlay;

        RecordingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "emberkit-eval"));
        }

        @Override public void sendSystemMessage(Component component, boolean actionBar) {
            message = component;
            overlay = actionBar;
        }
    }

    /** Mod exceptions become test failures, and every spawned entity is removed so later batches start empty. */
    private static void check(GameTestHelper helper, Body body) {
        List<Entity> spawned = new ArrayList<>();
        try {
            body.run(spawned);
        } catch (GameTestAssertException assertion) {
            throw assertion;
        } catch (Throwable failure) {
            helper.fail("Mod code threw " + failure);
        } finally {
            spawned.forEach(Entity::discard);
        }
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private static Item lantern(GameTestHelper helper) {
        require(helper, BuiltInRegistries.ITEM.containsKey(LANTERN), "Item emberkit:ember_lantern is not registered");
        return BuiltInRegistries.ITEM.get(LANTERN);
    }

    private static RecordingPlayer player(GameTestHelper helper, List<Entity> spawned) {
        RecordingPlayer player = new RecordingPlayer(helper.getLevel());
        Vec3 at = helper.absoluteVec(PLAYER_AT);
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(lantern(helper)));
        spawned.add(player);
        return player;
    }

    private static LivingEntity pig(GameTestHelper helper, double x, List<Entity> spawned) {
        LivingEntity pig = helper.spawn(EntityType.PIG, new Vec3(x, PLAYER_AT.y, PLAYER_AT.z));
        spawned.add(pig);
        return pig;
    }

    /** The server path for a right click: cooldown gate, Item.use and hand replacement. */
    private static void use(GameTestHelper helper, RecordingPlayer player) {
        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lanternRegistered(GameTestHelper helper) {
        check(helper, spawned -> lantern(helper));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lanternUnstackable(GameTestHelper helper) {
        check(helper, spawned -> {
            int size = new ItemStack(lantern(helper)).getMaxStackSize();
            require(helper, size == 1, "The lantern stacks to " + size);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lanternDurability64(GameTestHelper helper) {
        check(helper, spawned -> {
            ItemStack stack = new ItemStack(lantern(helper));
            require(helper, stack.isDamageableItem() && stack.getMaxDamage() == 64, "Durability is " + stack.getMaxDamage());
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lanternInToolsAndUtilities(GameTestHelper helper) {
        check(helper, spawned -> {
            CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess());
            ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    ResourceLocation.withDefaultNamespace("tools_and_utilities"));
            require(helper, BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(key).contains(new ItemStack(lantern(helper))),
                    "The lantern is not in the Tools & Utilities tab");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "emberkit_targets_glow")
    public void targetsGlow(GameTestHelper helper) {
        check(helper, spawned -> {
            RecordingPlayer player = player(helper, spawned);
            LivingEntity near = pig(helper, PLAYER_AT.x + 3, spawned);
            use(helper, player);
            MobEffectInstance glow = near.getEffect(MobEffects.GLOWING);
            require(helper, glow != null && glow.getDuration() >= 199 && glow.getDuration() <= 200,
                    "Target 3 blocks away has glowing " + glow);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "emberkit_targets_radius")
    public void radiusAndUserExcluded(GameTestHelper helper) {
        check(helper, spawned -> {
            RecordingPlayer player = player(helper, spawned);
            pig(helper, PLAYER_AT.x + 3, spawned);
            LivingEntity far = pig(helper, PLAYER_AT.x + 12, spawned);
            use(helper, player);
            require(helper, !far.hasEffect(MobEffects.GLOWING), "A target 12 blocks away glows");
            require(helper, !player.hasEffect(MobEffects.GLOWING), "The user glows");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "emberkit_targets_durability")
    public void targetsCostDurability(GameTestHelper helper) {
        check(helper, spawned -> {
            RecordingPlayer player = player(helper, spawned);
            pig(helper, PLAYER_AT.x + 3, spawned);
            use(helper, player);
            ItemStack held = player.getMainHandItem();
            require(helper, held.is(lantern(helper)) && held.getDamageValue() == 1,
                    "After a use with a target the lantern has damage " + held.getDamageValue() + " (" + held + ")");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "emberkit_targets_cooldown")
    public void targetsCooldown100(GameTestHelper helper) {
        check(helper, spawned -> {
            RecordingPlayer player = player(helper, spawned);
            pig(helper, PLAYER_AT.x + 3, spawned);
            Item item = lantern(helper);
            use(helper, player);
            require(helper, player.getCooldowns().isOnCooldown(item), "No cooldown after a use with a target");
            for (int tick = 0; tick < 99; tick++) player.getCooldowns().tick();
            require(helper, player.getCooldowns().isOnCooldown(item), "The cooldown ended before 100 ticks");
            player.getCooldowns().tick();
            player.getCooldowns().tick();
            require(helper, !player.getCooldowns().isOnCooldown(item), "The cooldown lasts longer than 101 ticks");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "emberkit_alone_cost")
    public void noTargetsNoCost(GameTestHelper helper) {
        check(helper, spawned -> {
            RecordingPlayer player = player(helper, spawned);
            use(helper, player);
            require(helper, player.getMainHandItem().getDamageValue() == 0, "Durability was used without a target");
            require(helper, !player.getCooldowns().isOnCooldown(lantern(helper)), "A cooldown started without a target");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "emberkit_alone_message")
    public void noTargetsActionBar(GameTestHelper helper) {
        check(helper, spawned -> {
            RecordingPlayer player = player(helper, spawned);
            use(helper, player);
            require(helper, player.message != null && player.message.getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals("message.emberkit.no_targets"), "Message was " + player.message);
            require(helper, player.overlay, "The message was not shown in the action bar");
        });
    }
}
