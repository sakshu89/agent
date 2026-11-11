package com.ultron.core.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HybridModelRouterTest {
    @Test void autoIsLocalFirstAndAllowsCloudOnlyWhenExplicitlyPermitted() {
        var route = HybridModelRouter.choose(ModelMode.AUTO, new ModelRequest("chat", false, true, true, null), true);
        assertEquals(ModelRoute.Backend.LOCAL, route.backend());
        assertTrue(route.mayFallbackToCloud());
    }
    @Test void privateDataNeverRoutesToCloud() {
        var route = HybridModelRouter.choose(ModelMode.AUTO, new ModelRequest("chat", true, true, true, null), true);
        assertFalse(route.mayFallbackToCloud());
        assertEquals(ModelRoute.Backend.LOCAL, route.backend());
    }
    @Test void localModeNeverFallsBack() {
        var route = HybridModelRouter.choose(ModelMode.LOCAL, new ModelRequest("chat", false, true, false, null), true);
        assertFalse(route.mayFallbackToCloud());
    }
    @Test void badRequestDoesNotTriggerFallback() {
        var route = HybridModelRouter.choose(ModelMode.AUTO, new ModelRequest("chat", false, true, true, null), true);
        assertFalse(HybridModelRouter.shouldFallback(route, 400, false));
        assertTrue(HybridModelRouter.shouldFallback(route, 503, false));
    }
}
