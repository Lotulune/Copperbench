package dev.copperbench.core.application;

import java.util.UUID;

/** Stable field-level error at the definition conversion/persistence boundary. */
public final class ElementFieldException extends IllegalArgumentException {
    private final String code;
    private final String path;
    private final UUID elementId;
    public ElementFieldException(String code, String path, UUID elementId, String reason) {
        super(reason); this.code = code; this.path = path; this.elementId = elementId;
    }
    public String code() { return code; }
    public String path() { return path; }
    public UUID elementId() { return elementId; }
}
