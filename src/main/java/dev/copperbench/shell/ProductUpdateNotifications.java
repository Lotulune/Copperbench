/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import dev.copperbench.release.ProductUpdates;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/** One background check per application session, shared by both desktop surfaces. */
public final class ProductUpdateNotifications {
    public static final ProductUpdateNotifications INSTANCE = new ProductUpdateNotifications(ProductUpdates::check);
    private final Callable<Optional<ProductUpdates.Release>> fetch;
    private final CopyOnWriteArraySet<Consumer<String>> LISTENERS = new CopyOnWriteArraySet<>();
    private volatile String version;
    private CompletableFuture<Optional<ProductUpdates.Release>> pending;
    private boolean started;
    ProductUpdateNotifications(Callable<Optional<ProductUpdates.Release>> fetch) { this.fetch = fetch; }

    public String availableVersion() { return version; }
    public void addListener(Consumer<String> listener) { LISTENERS.add(listener); }
    public void removeListener(Consumer<String> listener) { LISTENERS.remove(listener); }

    public synchronized void start() {
        if (!started) { started = true; check(); }
    }

    public synchronized CompletableFuture<Optional<ProductUpdates.Release>> check() {
        if (pending != null && !pending.isDone()) return pending;
        started = true;
        pending = new CompletableFuture<>();
        var result = pending;
        Thread.ofVirtual().name("product-update-check").start(() -> {
            try {
                var release = fetch.call();
                version = release.map(ProductUpdates.Release::version).orElse(null);
                LISTENERS.forEach(listener -> listener.accept(version));
                result.complete(release);
            } catch (Exception error) {
                org.apache.logging.log4j.LogManager.getLogger(ProductUpdateNotifications.class)
                        .info("Background update check unavailable: {}", error.getMessage());
                result.completeExceptionally(error);
            }
        });
        return result;
    }
}
