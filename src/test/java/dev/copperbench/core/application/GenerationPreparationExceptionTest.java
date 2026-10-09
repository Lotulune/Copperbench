package dev.copperbench.core.application;

import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException.ConflictReason;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException.SourceConflict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenerationPreparationExceptionTest {
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/private/source.java", "../source.java", "src/../source.java", "./source.java",
            "src//source.java", "src/", "C:/private/source.java", "C:\\private\\source.java", "\\\\host\\share\\source.java",
            "src/source.java\nsecret", "src/source.java\0secret"})
    void rejectsUnsafeLocationsWithoutEchoingThem(String path) {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> new SourceConflict(path, ConflictReason.SOURCE_CHANGED));
        assertEquals("Conflict location must be a safe workspace-relative file path", failure.getMessage());
    }

    @Test void detailsAreImmutableAndNeverInferredFromACause() {
        var cause = new IOException("/private/secret/source.java could not be read");
        var legacy = new GenerationPreparationException("GENERATION_SOURCE_CONFLICT", "legacy explanation", cause);
        assertTrue(legacy.conflicts().isEmpty());
        assertSame(cause, legacy.getCause());
        var conflicts = new ArrayList<>(List.of(new SourceConflict("src/main/java/Example.java", ConflictReason.UNOWNED_BASE_FILE)));
        var typed = new GenerationPreparationException(conflicts, cause);
        conflicts.clear();
        assertEquals(1, typed.conflicts().size());
        assertEquals("GENERATION_SOURCE_CONFLICT", typed.code());
        assertTrue(typed.getMessage().contains("src/main/java/Example.java"));
        assertTrue(typed.getMessage().contains(ConflictReason.UNOWNED_BASE_FILE.explanation()));
        assertFalse(typed.getMessage().contains("/private/secret"));
        assertEquals(GenerationPreparationException.SOURCE_CONFLICT_MESSAGE, legacy.getMessage());
        assertThrows(UnsupportedOperationException.class, () -> typed.conflicts().clear());
        assertThrows(IllegalArgumentException.class, () -> new GenerationPreparationException(List.of(), cause));
        assertNull(new SourceConflict(null, ConflictReason.PATH_OUTSIDE_WORKSPACE).relativePath());
    }
}
