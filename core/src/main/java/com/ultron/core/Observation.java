package com.ultron.core;

import java.time.Instant;
import java.util.Map;

public record Observation(String summary, String fingerprint, Instant observedAt, Map<String, String> metadata) {
    public Observation {
        summary = summary == null ? "" : summary;
        fingerprint = fingerprint == null ? "" : fingerprint;
        observedAt = observedAt == null ? Instant.now() : observedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
