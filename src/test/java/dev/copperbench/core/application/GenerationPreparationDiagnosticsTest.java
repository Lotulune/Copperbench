package dev.copperbench.core.application;

import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException.ConflictReason;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException.SourceConflict;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenerationPreparationDiagnosticsTest {
    @Test void retainsTypedRelativePathAndConflictReason() {
        for (var reason : List.of(ConflictReason.UNOWNED_BASE_FILE, ConflictReason.UNOWNED_ELEMENT_FILE,
                ConflictReason.SOURCE_CHANGED)) {
            var conflict = new SourceConflict("src/main/java/example/Manual.java", reason);
            String message = GenerationPreparationDiagnostics.sourceConflict(List.of(conflict));
            assertTrue(message.contains(conflict.relativePath()), message);
            assertTrue(message.contains(reason.explanation()), message);
            assertTrue(message.contains("Do not delete manual files or claim ownership automatically."));
        }
    }

    @Test void prefixedLegacyIOExceptionCannotSupplyDisplayDetails() {
        for (String raw : List.of("Access denied: /private/unrelated/path",
                "GENERATION_SOURCE_CONFLICT: /private/unrelated/path private-token")) {
            var failure = new GenerationPreparationException("GENERATION_SOURCE_CONFLICT", raw, new IOException(raw));
            String message = GenerationPreparationDiagnostics.sourceConflict(failure.conflicts());
            assertEquals(GenerationPreparationException.SOURCE_CONFLICT_MESSAGE, message);
            assertEquals(message, failure.getMessage());
            assertFalse(message.contains("/private/"));
            assertFalse(message.contains("private-token"));
            assertFalse(message.contains("Conflict:"));
        }
    }

    @Test void unlocatedConflictNeverReconstructsLocationFromCause() {
        var failure = new GenerationPreparationException(
                List.of(new SourceConflict(null, ConflictReason.PATH_OUTSIDE_WORKSPACE)),
                new IOException("/private/secret"));
        assertTrue(failure.getMessage().contains(ConflictReason.PATH_OUTSIDE_WORKSPACE.explanation()));
        assertFalse(failure.getMessage().contains("/private/"));
        assertNull(GenerationPreparationDiagnostics.displaySourcePath(failure.conflicts().getFirst()));
    }

    @Test void sanitizesUnicodeDisplayWithoutChangingStructuredLocation() {
        String path = "src/铜仪\u202e\u2028\u2029\u200d.java";
        var conflict = new SourceConflict(path, ConflictReason.SOURCE_CHANGED);
        String display = GenerationPreparationDiagnostics.displaySourcePath(conflict);
        assertEquals("src/铜仪    .java", display);
        assertEquals(path, conflict.relativePath());
        assertFalse(GenerationPreparationDiagnostics.sourceConflict(List.of(conflict)).contains("\u202e"));
    }

    @Test void truncatesOnlyDisplayWithoutSplittingUnicodeCodePoints() {
        String input = "a".repeat(511) + "\uD83D\uDD52" + "trailing";
        var conflict = new SourceConflict(input, ConflictReason.SOURCE_CHANGED);
        assertEquals("a".repeat(511) + "\uD83D\uDD52 [truncated]",
                GenerationPreparationDiagnostics.displaySourcePath(conflict));
        assertEquals(input, conflict.relativePath());
        assertFalse(GenerationPreparationDiagnostics.sourceConflict(List.of(conflict)).contains("trailing"));
    }

    @Test void exactLimitDoesNotClaimTruncation() {
        String detail = "x".repeat(512);
        assertEquals(detail, GenerationPreparationDiagnostics.displaySourcePath(
                new SourceConflict(detail, ConflictReason.UNOWNED_BASE_FILE)));
    }

    @Test void boundedSummaryKeepsAllStructuredConflictsAndReportsOmittedCount() {
        var conflicts = List.of(new SourceConflict("x".repeat(1000), ConflictReason.SOURCE_CHANGED),
                new SourceConflict("src/Other.java", ConflictReason.UNOWNED_ELEMENT_FILE));
        var failure = new GenerationPreparationException(conflicts, null);
        assertEquals(conflicts, failure.conflicts());
        assertTrue(failure.getMessage().endsWith("[truncated] (1 more conflicts)"));
        assertFalse(failure.getMessage().contains("src/Other.java"));
        assertTrue(failure.getMessage().length() < 1000);
    }
}
