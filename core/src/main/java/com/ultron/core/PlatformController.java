package com.ultron.core;

import java.util.List;

/** OS-specific boundary. Shared agent logic never assumes OS APIs are interchangeable. */
public interface PlatformController extends AutoCloseable {
    String platformId();
    List<Capability> capabilities();
    Observation observe() throws Exception;
    ActionResult execute(PlatformAction action) throws Exception;
    default void emergencyStop() {}
    default void resetEmergencyStop() {}
    default boolean isPermissionGranted(String capabilityId) { return capabilities().stream()
        .filter(c -> c.id().equals(capabilityId)).findFirst()
        .map(c -> c.status() == CapabilityStatus.AVAILABLE).orElse(false); }
    @Override default void close() throws Exception {}
}
