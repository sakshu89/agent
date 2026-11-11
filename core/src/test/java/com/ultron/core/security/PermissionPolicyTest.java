package com.ultron.core.security;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PermissionPolicyTest {
 @Test void criticalActionsRequireAuthenticatedConfirmation() {
  assertEquals(PermissionPolicy.Decision.REQUIRE_CONFIRMATION, PermissionPolicy.evaluate("send_message", PermissionPolicy.Risk.CRITICAL, false, true, false));
  assertEquals(PermissionPolicy.Decision.ALLOW, PermissionPolicy.evaluate("send_message", PermissionPolicy.Risk.CRITICAL, true, true, false));
  assertEquals(PermissionPolicy.Decision.REQUIRE_CONFIRMATION, PermissionPolicy.evaluate("send_message", PermissionPolicy.Risk.CRITICAL, true, false, false));
 }
 @Test void arbitraryCommandsAreDenied() { assertEquals(PermissionPolicy.Decision.DENY, PermissionPolicy.evaluate("arbitrary_command", PermissionPolicy.Risk.LOW, true, true, true)); }
 @Test void cloudRequiresConsentAndNoPrivateData() {
  assertFalse(PermissionPolicy.maySendToCloud(true,true,true));
  assertFalse(PermissionPolicy.maySendToCloud(false,false,true));
  assertTrue(PermissionPolicy.maySendToCloud(false,true,true));
 }
}
