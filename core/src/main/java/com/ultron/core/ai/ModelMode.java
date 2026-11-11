package com.ultron.core.ai;

/** User-selected intelligence routing policy. */
public enum ModelMode {
    LOCAL, CLOUD, AUTO;
    public static ModelMode from(String value) {
        if (value == null) return LOCAL;
        try { return valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException ignored) { return LOCAL; }
    }
}
