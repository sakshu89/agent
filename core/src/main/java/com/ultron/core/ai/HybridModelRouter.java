package com.ultron.core.ai;

import java.util.Objects;

/** Local-first routing policy shared by Android and desktop clients. Never uploads private data implicitly. */
public final class HybridModelRouter {
    private HybridModelRouter() {}

    public static ModelRoute choose(ModelMode mode, ModelRequest request, boolean cloudConfigured) {
        Objects.requireNonNull(request, "request");
        mode = mode == null ? ModelMode.LOCAL : mode;
        boolean permitted = request.cloudAllowed() && !request.containsPrivateData() && cloudConfigured;
        return switch (mode) {
            case LOCAL -> new ModelRoute(ModelRoute.Backend.LOCAL, false, "Local mode selected.");
            case CLOUD -> permitted
                ? new ModelRoute(ModelRoute.Backend.CLOUD, false, "Cloud mode selected with cloud permission.")
                : new ModelRoute(ModelRoute.Backend.LOCAL, false, "Cloud blocked: not configured, not allowed, or private data present.");
            case AUTO -> permitted
                ? new ModelRoute(ModelRoute.Backend.LOCAL, true, "Local-first; cloud fallback is permitted.")
                : new ModelRoute(ModelRoute.Backend.LOCAL, false, "Local-first; cloud fallback is blocked by privacy/configuration.");
        };
    }

    /** Only transport/server failures may trigger fallback; invalid user/tool data must not loop. */
    public static boolean shouldFallback(ModelRoute route, int httpStatus, boolean transportFailure) {
        if (route == null || !route.mayFallbackToCloud()) return false;
        return transportFailure || httpStatus == 408 || httpStatus == 429 || httpStatus >= 500;
    }
}
