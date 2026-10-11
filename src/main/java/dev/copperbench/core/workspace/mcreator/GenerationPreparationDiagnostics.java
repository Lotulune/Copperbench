package dev.copperbench.core.workspace.mcreator;

/** Presentation only; generation ownership checks and rollback remain unchanged. */
final class GenerationPreparationDiagnostics {
    private static final String PREFIX = "GENERATION_SOURCE_CONFLICT:";
    private static final int DETAIL_LIMIT = 512;
    private static final String GUIDANCE = "Source files changed or are not owned by the generator. "
            + "Review the reported file and its ownership before generating again. "
            + "Do not delete manual files or claim ownership automatically.";

    private GenerationPreparationDiagnostics() {
    }

    static String sourceConflict(String message) {
        // Only surface the recognized, locally generated conflict detail. Do
        // not append arbitrary IO exceptions or absolute-path stack traces.
        if (message == null || !message.startsWith(PREFIX)) return GUIDANCE;
        String detail = message.substring(PREFIX.length()).strip();
        if (detail.isEmpty()) return GUIDANCE;
        StringBuilder visible = new StringBuilder();
        detail.codePoints().limit(DETAIL_LIMIT).forEach(codePoint -> {
            int kind = Character.getType(codePoint);
            if (Character.isISOControl(codePoint) || kind == Character.FORMAT
                    || kind == Character.LINE_SEPARATOR || kind == Character.PARAGRAPH_SEPARATOR)
                visible.append(' ');
            else visible.appendCodePoint(codePoint);
        });
        if (detail.codePointCount(0, detail.length()) > DETAIL_LIMIT) visible.append(" [truncated]");
        return GUIDANCE + " Conflict: " + visible;
    }
}
