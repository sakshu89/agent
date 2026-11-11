package com.ultron.core.security;

import java.util.*;

/** Central deny-by-default policy for consequential operations and untrusted tool metadata. */
public final class PermissionPolicy {
    public enum Risk { READ_ONLY, LOW, MEDIUM, HIGH, CRITICAL }
    public enum Decision { ALLOW, REQUIRE_CONFIRMATION, DENY }
    private static final Set<String> CRITICAL = Set.of("send_message", "send_email", "publish", "post", "purchase", "pay", "transfer", "delete_account", "change_password", "disable_security");
    private static final Set<String> HIGH = Set.of("delete_file", "overwrite_file", "install_app", "uninstall_app", "share_file", "change_permissions");
    private PermissionPolicy() {}
    public static Decision evaluate(String operation, Risk declaredRisk, boolean userConfirmed,
                                    boolean authenticatedSession, boolean cloudDataConsent) {
        if (operation == null || operation.isBlank() || declaredRisk == null) return Decision.DENY;
        String op = operation.trim().toLowerCase(Locale.ROOT);
        if (op.contains("shell") || op.contains("exec") || op.contains("arbitrary_command") || op.contains("credential_export")) return Decision.DENY;
        if (CRITICAL.contains(op)) return authenticatedSession && userConfirmed ? Decision.ALLOW : Decision.REQUIRE_CONFIRMATION;
        if (HIGH.contains(op) || declaredRisk == Risk.CRITICAL || declaredRisk == Risk.HIGH)
            return userConfirmed ? Decision.ALLOW : Decision.REQUIRE_CONFIRMATION;
        if (declaredRisk == Risk.MEDIUM) return userConfirmed ? Decision.ALLOW : Decision.REQUIRE_CONFIRMATION;
        if (declaredRisk == Risk.READ_ONLY || declaredRisk == Risk.LOW) return Decision.ALLOW;
        return Decision.DENY;
    }
    /** Cloud permission is independent of action permission and never inferred from model mode. */
    public static boolean maySendToCloud(boolean privateData, boolean explicitConsent, boolean providerConfigured) {
        return !privateData && explicitConsent && providerConfigured;
    }
}
