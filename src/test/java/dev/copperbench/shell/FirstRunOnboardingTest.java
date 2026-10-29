package dev.copperbench.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FirstRunOnboardingTest {
    @TempDir Path directory;
    @Test void freshUserSeesTheTourOnceAcrossRestartsAndUpgrades() throws Exception {
        Path marker = directory.resolve("tour");
        FirstRunOnboarding.initialize(marker, false);
        assertTrue(FirstRunOnboarding.claim(marker));
        assertFalse(FirstRunOnboarding.claim(marker));
        FirstRunOnboarding.initialize(marker, true);
        assertFalse(FirstRunOnboarding.claim(marker));
    }
    @Test void upgradingExistingUsersDoesNotStartANewTour() throws Exception {
        Path marker = directory.resolve("tour"); FirstRunOnboarding.initialize(marker, true);
        assertFalse(FirstRunOnboarding.claim(marker));
        FirstRunOnboarding.initialize(marker, false);
        assertFalse(FirstRunOnboarding.claim(marker));
    }
    @Test void launchInterruptedBeforeTheFirstWindowKeepsTheTourPending() throws Exception {
        Path marker = directory.resolve("tour"); FirstRunOnboarding.initialize(marker, false);
        FirstRunOnboarding.initialize(marker, true);
        assertTrue(FirstRunOnboarding.claim(marker));
    }
}
