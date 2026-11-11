package com.ultron.core.ai;

import java.util.Map;

/** Provider-neutral request metadata. Prompts remain owned by the calling platform. */
public record ModelRequest(String task, boolean containsPrivateData, boolean cloudAllowed,
                           boolean requiresTools, Map<String, String> metadata) {
    public ModelRequest {
        task = task == null ? "general" : task;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
