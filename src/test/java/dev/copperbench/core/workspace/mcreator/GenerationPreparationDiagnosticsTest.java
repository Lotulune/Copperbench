package dev.copperbench.core.workspace.mcreator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenerationPreparationDiagnosticsTest {
    @Test void retainsSpecificRelativePathAndConflictReason() {
        for (String reason : new String[]{"unowned base file ", "unowned element file ",
                "source changed before generation: "}) {
            String detail = reason + "src/main/java/example/Manual.java";
            String message = GenerationPreparationDiagnostics.sourceConflict("GENERATION_SOURCE_CONFLICT: " + detail);
            assertTrue(message.endsWith("Conflict: " + detail), message);
            assertTrue(message.contains("Do not delete manual files or claim ownership automatically."));
        }
    }

    @Test void doesNotSurfaceUnclassifiedIOExceptionDetails() {
        String message = GenerationPreparationDiagnostics.sourceConflict("Access denied: /private/unrelated/path");
        assertFalse(message.contains("/private/"));
        assertFalse(message.contains("Conflict:"));
        assertTrue(message.contains("Review"));
    }

    @Test void emptyOrMissingConflictStillHasRecoveryGuidance() {
        for (String input : new String[]{null, "", "GENERATION_SOURCE_CONFLICT:", "GENERATION_SOURCE_CONFLICT:  "}) {
            String message = GenerationPreparationDiagnostics.sourceConflict(input);
            assertTrue(message.contains("ownership"));
            assertFalse(message.contains("Conflict:"));
        }
    }

    @Test void preventsControlCharactersFromImpersonatingLogLines() {
        String message = GenerationPreparationDiagnostics.sourceConflict(
                "GENERATION_SOURCE_CONFLICT: unowned element file src/铜仪\r\n\u001b\u202e\u2028.java");
        assertTrue(message.contains("src/铜仪"));
        assertFalse(message.contains("\r"));
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("\u001b"));
        assertFalse(message.contains("\u202e"));
        assertFalse(message.contains("\u2028"));
    }

    @Test void truncatesOnlyDisplayDetailWithoutSplittingUnicodeCodePoints() {
        String input = "a".repeat(511) + "\uD83D\uDD52" + "trailing";
        String message = GenerationPreparationDiagnostics.sourceConflict("GENERATION_SOURCE_CONFLICT: " + input);
        assertTrue(message.endsWith("a".repeat(511) + "\uD83D\uDD52 [truncated]"));
        assertFalse(message.contains("trailing"));
    }

    @Test void exactLimitDoesNotClaimTruncation() {
        String detail = "x".repeat(512);
        String message = GenerationPreparationDiagnostics.sourceConflict("GENERATION_SOURCE_CONFLICT: " + detail);
        assertTrue(message.endsWith(detail));
        assertFalse(message.contains("[truncated]"));
    }
}
