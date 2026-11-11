package com.ultron.core.ai;

public record ModelRoute(Backend backend, boolean mayFallbackToCloud, String reason) {
    public enum Backend { LOCAL, CLOUD }
}
