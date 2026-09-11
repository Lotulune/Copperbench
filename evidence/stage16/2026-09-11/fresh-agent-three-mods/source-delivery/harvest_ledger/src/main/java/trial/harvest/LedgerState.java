package trial.harvest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class LedgerState extends SavedData {
    public static final String SAVE_ID = "harvest_ledger_totals";
    public static final Factory<LedgerState> TYPE = new Factory<>(LedgerState::new, LedgerState::read, null);
    private final Map<UUID, Long> totals = new HashMap<>();

    public static LedgerState forWorld(ServerLevel world) {
        return world.getServer().overworld().getDataStorage().computeIfAbsent(TYPE, SAVE_ID);
    }

    public long total(UUID player) { return totals.getOrDefault(player, 0L); }

    public void credit(UUID player) {
        totals.put(player, Math.addExact(total(player), 1L));
        setDirty();
    }

    private static LedgerState read(CompoundTag nbt, HolderLookup.Provider lookup) {
        LedgerState result = new LedgerState();
        CompoundTag saved = nbt.getCompound("players");
        for (String key : saved.getAllKeys()) {
            try {
                result.totals.put(UUID.fromString(key), Math.max(0L, saved.getLong(key)));
            } catch (IllegalArgumentException ignored) {
                // Ignore unrelated or malformed UUID keys without discarding valid player totals.
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag saved = new CompoundTag();
        totals.forEach((player, total) -> saved.putLong(player.toString(), total));
        nbt.put("players", saved);
        return nbt;
    }
}
