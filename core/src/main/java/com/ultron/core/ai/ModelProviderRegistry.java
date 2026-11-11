package com.ultron.core.ai;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry; provider IDs are unique and discovery never implies consent to use cloud. */
public final class ModelProviderRegistry {
    private final Map<String, ModelProvider> providers = new ConcurrentHashMap<>();
    public void register(ModelProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (provider.id() == null || provider.id().isBlank()) throw new IllegalArgumentException("Provider ID required");
        if (providers.putIfAbsent(provider.id(), provider) != null) throw new IllegalStateException("Provider already registered: " + provider.id());
    }
    public Optional<ModelProvider> find(String id) { return Optional.ofNullable(providers.get(id)); }
    public List<ModelCapabilities> capabilities() {
        return providers.values().stream().map(ModelProvider::capabilities)
            .sorted(Comparator.comparing(ModelCapabilities::providerId)).toList();
    }
}
