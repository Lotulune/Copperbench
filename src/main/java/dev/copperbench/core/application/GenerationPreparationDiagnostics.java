package dev.copperbench.core.application;

import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException.SourceConflict;

import java.util.List;

/** Bounded presentation of typed conflicts; never interprets exception messages or causes. */
public final class GenerationPreparationDiagnostics {
    private static final int DETAIL_LIMIT = 512;

    private GenerationPreparationDiagnostics() { }

    /**
     * Builds a bounded summary without discarding any structured conflicts.
     * @param conflicts explicitly classified conflicts, possibly empty
     * @return recovery guidance and a bounded first-conflict preview
     */
    public static String sourceConflict(List<SourceConflict> conflicts) {
        String guidance = WorkspaceTaskGateway.GenerationPreparationException.SOURCE_CONFLICT_MESSAGE;
        if (conflicts.isEmpty()) return guidance;
        SourceConflict first = conflicts.getFirst();
        String detail = first.reason().explanation()
                + (first.relativePath() == null ? "" : " " + first.relativePath());
        return guidance + " Conflict: " + bounded(detail)
                + (conflicts.size() == 1 ? "" : " (" + (conflicts.size() - 1) + " more conflicts)");
    }

    /**
     * Returns presentation text only; never use this value as a path or action target.
     * @param conflict a checked conflict
     * @return bounded, single-line path text, or null when no safe location is known
     */
    public static String displaySourcePath(SourceConflict conflict) {
        return conflict.relativePath() == null ? null : bounded(conflict.relativePath());
    }

    private static String bounded(String detail) {
        StringBuilder visible = new StringBuilder();
        detail.codePoints().limit(DETAIL_LIMIT).forEach(codePoint -> {
            int kind = Character.getType(codePoint);
            if (Character.isISOControl(codePoint) || kind == Character.FORMAT
                    || kind == Character.LINE_SEPARATOR || kind == Character.PARAGRAPH_SEPARATOR
                    || kind == Character.SURROGATE)
                visible.append(' ');
            else visible.appendCodePoint(codePoint);
        });
        if (detail.codePointCount(0, detail.length()) > DETAIL_LIMIT) visible.append(" [truncated]");
        return visible.toString();
    }
}
