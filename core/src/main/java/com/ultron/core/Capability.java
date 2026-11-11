package com.ultron.core;

import java.util.Objects;

public record Capability(String id, String description, CapabilityStatus status) {
    public Capability {
        Objects.requireNonNull(id); Objects.requireNonNull(description); Objects.requireNonNull(status);
    }
}
