package dev.copperbench.shell;

import dev.copperbench.release.ProductUpdates;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ProductUpdateNotificationsTest {
    @Test void startupAndManualChecksShareOneRequestAndPublishOnlyNewerVersions() throws Exception {
        var gate = new CountDownLatch(1);
        var requests = new AtomicInteger();
        var monitor = new ProductUpdateNotifications(() -> {
            requests.incrementAndGet();
            assertTrue(gate.await(3, TimeUnit.SECONDS));
            return Optional.of(new ProductUpdates.Release("0.1.10", Map.of()));
        });
        List<String> events = new CopyOnWriteArrayList<>();
        monitor.addListener(events::add);
        monitor.start();
        var first = monitor.check();
        assertSame(first, monitor.check());
        assertNull(monitor.availableVersion());
        gate.countDown();
        first.get(3, TimeUnit.SECONDS);
        monitor.start();
        assertEquals(1, requests.get());
        assertEquals("0.1.10", monitor.availableVersion());
        assertEquals(List.of("0.1.10"), events);
    }

    @Test void failedAutomaticCheckStaysQuietAndManualRetryCanRecover() throws Exception {
        var requests = new AtomicInteger();
        var monitor = new ProductUpdateNotifications(() -> {
            if (requests.incrementAndGet() == 1) throw new IOException("offline");
            return Optional.of(new ProductUpdates.Release("0.2.0", Map.of()));
        });
        List<String> events = new CopyOnWriteArrayList<>();
        monitor.addListener(events::add);
        assertThrows(ExecutionException.class, () -> monitor.check().get(3, TimeUnit.SECONDS));
        assertNull(monitor.availableVersion());
        assertTrue(events.isEmpty());
        monitor.check().get(3, TimeUnit.SECONDS);
        assertEquals("0.2.0", monitor.availableVersion());
        assertEquals(List.of("0.2.0"), events);
    }

    @Test void upToDateResultClearsTheNoticeAndRemovedListenersAreNotNotified() throws Exception {
        var requests = new AtomicInteger();
        var monitor = new ProductUpdateNotifications(() -> requests.incrementAndGet() == 1
                ? Optional.of(new ProductUpdates.Release("0.2.0", Map.of())) : Optional.empty());
        var notifications = new AtomicInteger();
        java.util.function.Consumer<String> listener = ignored -> notifications.incrementAndGet();
        monitor.addListener(listener);
        monitor.check().get(3, TimeUnit.SECONDS);
        monitor.removeListener(listener);
        monitor.check().get(3, TimeUnit.SECONDS);
        assertNull(monitor.availableVersion());
        assertEquals(1, notifications.get());
    }
}
