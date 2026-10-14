package copperbench.acceptance;

import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;

/** The recipe and loot cases share one real server resource manager. Preserve both pack selections. */
final class ReloadCoordinator {
    private static CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
    static CompletableFuture<Void> reload(MinecraftServer server, String pack) {
        pending = pending.thenComposeAsync(ignored -> {
            var repository = server.getPackRepository();
            repository.reload();
            if (!repository.getAvailableIds().contains(pack))
                throw new AssertionError("Controlled datapack is not discoverable: " + pack);
            var selected = new LinkedHashSet<>(repository.getSelectedIds());
            selected.add(pack);
            return server.reloadResources(selected);
        }, server);
        return pending;
    }
}
