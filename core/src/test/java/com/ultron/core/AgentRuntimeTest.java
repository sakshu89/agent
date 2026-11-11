package com.ultron.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentRuntimeTest {
    static class FakePlatform implements PlatformController {
        int calls = 0;
        @Override public String platformId() { return "test"; }
        @Override public List<Capability> capabilities() {
            return List.of(new Capability("test.input","test",CapabilityStatus.AVAILABLE));
        }
        @Override public Observation observe() {
            return new Observation("state", "fingerprint-" + calls, null, Map.of());
        }
        @Override public ActionResult execute(PlatformAction action) {
            calls++;
            return new ActionResult(true,"ok","result-" + calls);
        }
    }

    @Test void permissionDeniedNeverDispatches() {
        FakePlatform p = new FakePlatform();
        AgentRuntime runtime = new AgentRuntime(p, a -> false, null);
        ActionResult result = runtime.run(new PlatformAction("test.input","click",Map.of(),true));
        assertFalse(result.success());
        assertEquals(0,p.calls);
        assertEquals(AgentState.WAITING_FOR_PERMISSION,runtime.state());
    }

    @Test void authorizedActionRunsOnceAndVerifies() {
        FakePlatform p = new FakePlatform();
        AgentRuntime runtime = new AgentRuntime(p, a -> true, null);
        ActionResult result = runtime.run(new PlatformAction("test.input","click",Map.of(),true));
        assertTrue(result.success());
        assertEquals(1,p.calls);
        assertEquals(AgentState.COMPLETED,runtime.state());
    }

    @Test void unavailableCapabilityNeverDispatches() {
        FakePlatform p = new FakePlatform() {
            @Override public List<Capability> capabilities() {
                return List.of(new Capability("test.input","test",CapabilityStatus.PERMISSION_REQUIRED));
            }
        };
        AgentRuntime runtime = new AgentRuntime(p, a -> true, null);
        assertFalse(runtime.run(new PlatformAction("test.input","click",Map.of(),false)).success());
        assertEquals(0,p.calls);
    }
}
