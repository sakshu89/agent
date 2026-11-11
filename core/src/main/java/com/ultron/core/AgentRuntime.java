package com.ultron.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded observe/plan/permission/act/verify/recover executor.
 * It deliberately performs at most one dispatch per invocation; callers must re-plan
 * after an unverified action rather than blindly repeating input.
 */
public final class AgentRuntime {
    public interface PermissionGate { boolean authorize(PlatformAction action); }
    public interface Listener { void onState(AgentState state, String detail); }

    private final PlatformController platform;
    private final PermissionGate permissions;
    private final Listener listener;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile AgentState state = AgentState.IDLE;

    public AgentRuntime(PlatformController platform, PermissionGate permissions, Listener listener) {
        this.platform = Objects.requireNonNull(platform);
        this.permissions = Objects.requireNonNull(permissions);
        this.listener = listener == null ? (s, d) -> {} : listener;
    }

    public synchronized ActionResult run(PlatformAction action) {
        Objects.requireNonNull(action);
        if (stopped.get()) return fail(AgentState.STOPPED, "Emergency stop is active.");
        try {
            set(AgentState.OBSERVING, "Observing platform state.");
            Observation before = platform.observe();
            set(AgentState.PLANNING, "Checking capability and action requirements.");
            Capability capability = platform.capabilities().stream()
                .filter(c -> c.id().equals(action.capabilityId())).findFirst().orElse(null);
            if (capability == null || capability.status() != CapabilityStatus.AVAILABLE)
                return fail(AgentState.FAILED, capability == null ? "Capability is not registered." :
                    "Capability unavailable: " + capability.status());
            if (!permissions.authorize(action))
                return fail(AgentState.WAITING_FOR_PERMISSION, "User permission/confirmation is required.");
            if (stopped.get()) return fail(AgentState.STOPPED, "Emergency stop activated before action.");
            set(AgentState.EXECUTING, "Executing one bounded platform action.");
            ActionResult result = platform.execute(action);
            if (stopped.get()) return fail(AgentState.STOPPED, "Stopped during execution.");
            set(AgentState.VERIFYING, "Verifying action result.");
            Observation after = platform.observe();
            boolean changed = !before.fingerprint().equals(after.fingerprint());
            if (!result.success()) return fail(AgentState.FAILED, result.message());
            if (!changed)
                return fail(AgentState.FAILED, "Action dispatched but the observed desktop state did not change; no retry was attempted.");
            set(AgentState.COMPLETED, result.message().isBlank() ? "Action verified." : result.message());
            return result;
        } catch (Exception e) {
            return fail(AgentState.FAILED, "Platform error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public void emergencyStop() { stopped.set(true); platform.emergencyStop(); set(AgentState.STOPPED, "Emergency stop requested."); }
    public void resetStop() { platform.resetEmergencyStop(); stopped.set(false); set(AgentState.IDLE, "Emergency stop reset by user."); }
    public AgentState state() { return state; }
    private ActionResult fail(AgentState s, String message) { set(s, message); return new ActionResult(false, message, ""); }
    private void set(AgentState s, String detail) { state = s; listener.onState(s, detail); }
}
