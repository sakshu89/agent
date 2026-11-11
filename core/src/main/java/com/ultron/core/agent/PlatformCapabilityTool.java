package com.ultron.core.agent;

import com.ultron.core.*;
import java.util.Map;
import java.util.Objects;

/** Bridges a registered OS capability into the shared tool registry. */
public final class PlatformCapabilityTool implements AgentTool {
    private final PlatformController platform;
    private final Capability capability;
    private final String operation;
    private final boolean confirmationRequired;

    public PlatformCapabilityTool(PlatformController platform, Capability capability,
                                  String operation, boolean confirmationRequired) {
        this.platform = Objects.requireNonNull(platform);
        this.capability = Objects.requireNonNull(capability);
        if (capability.status() != CapabilityStatus.AVAILABLE)
            throw new IllegalArgumentException("Cannot register unavailable capability: " + capability.id());
        this.operation = Objects.requireNonNull(operation);
        this.confirmationRequired = confirmationRequired;
    }
    @Override public String id() { return capability.id(); }
    @Override public String description() { return capability.description(); }
    @Override public boolean requiresConfirmation(Map<String,String> arguments) { return confirmationRequired; }
    @Override public ActionResult execute(Map<String,String> arguments, Observation observation) throws Exception {
        return platform.execute(new PlatformAction(capability.id(), operation, arguments, confirmationRequired));
    }

    /** Registers only capabilities currently reported AVAILABLE by the native controller. */
    public static void registerAvailable(PlatformController platform, ToolRegistry registry) {
        for (Capability capability : platform.capabilities()) {
            if (capability.status() != CapabilityStatus.AVAILABLE) continue;
            String operation = switch (capability.id()) {
                case "desktop.launch" -> "launch_app";
                case "desktop.input" -> "click";
                default -> null;
            };
            if (operation != null && registry.find(capability.id()).isEmpty())
                registry.register(new PlatformCapabilityTool(platform, capability, operation, false));
        }
    }
}
