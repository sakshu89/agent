package com.ultron.core.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelProviderRegistryTest {
    @Test void discoversCapabilitiesWithoutAssumingUnsupportedFeatures() {
        ModelProvider provider = new ModelProvider() {
            public String id() { return "local-test"; }
            public ModelCapabilities capabilities() { return new ModelCapabilities("local-test", true, false, false, false, false, 0, 0); }
            public String complete(ModelPrompt prompt) { return "ok"; }
        };
        ModelProviderRegistry registry = new ModelProviderRegistry();
        registry.register(provider);
        assertEquals(1, registry.capabilities().size());
        assertTrue(registry.find("local-test").orElseThrow().capabilities().local());
        assertFalse(registry.capabilities().get(0).toolCalling());
    }

    @Test void rejectsDuplicateProviderIds() {
        ModelProvider provider = new ModelProvider() {
            public String id() { return "same"; }
            public ModelCapabilities capabilities() { return new ModelCapabilities("same", true, false, false, false, false, 0, 0); }
            public String complete(ModelPrompt prompt) { return "ok"; }
        };
        ModelProviderRegistry registry = new ModelProviderRegistry();
        registry.register(provider);
        assertThrows(IllegalStateException.class, () -> registry.register(provider));
    }
}
