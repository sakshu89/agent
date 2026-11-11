# ULTRON 7.4 — Universal Keyboard Send

Messaging automation now uses a universal send strategy:

1. Find and click a visible Send control when one exists.
2. If no Send control exists, invoke the focused message field's IME editor action via `AccessibilityNodeInfo.ACTION_IME_ENTER`.
3. If the visible Send control was rejected, refresh the UI once and fall back to the keyboard action.
4. Verify the message after dispatch; do not blindly repeat the send.

This is app-agnostic and is not hard-coded for Snapchat.
