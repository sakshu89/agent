package com.ultron.core.ai;

/** Conservative, provider-reported feature set. Unknown capabilities must remain false. */
public record ModelCapabilities(
    String providerId,
    boolean local,
    boolean streaming,
    boolean toolCalling,
    boolean jsonMode,
    boolean vision,
    int contextWindowTokens,
    int maxOutputTokens
) {
    public ModelCapabilities {
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("providerId required");
        if (contextWindowTokens < 0 || maxOutputTokens < 0) throw new IllegalArgumentException("Token limits cannot be negative");
    }
}
