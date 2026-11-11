package com.ultron.core.ai;

/** Provider-neutral prompt envelope. Callers own classification and consent decisions. */
public record ModelPrompt(String system, String user, ModelRequest request) {
    public ModelPrompt {
        system = system == null ? "" : system;
        user = user == null ? "" : user;
        if (request == null) throw new IllegalArgumentException("request required");
    }
}
