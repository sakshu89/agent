package com.ultron.core;

import java.util.Map;

public record PlatformAction(String capabilityId, String operation, Map<String, String> arguments,
                             boolean requiresConfirmation) {
    public PlatformAction {
        if (capabilityId == null || capabilityId.isBlank()) throw new IllegalArgumentException("capabilityId required");
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation required");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
