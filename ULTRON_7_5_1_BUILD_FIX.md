# ULTRON 7.5.1 — Build Fix

Fixes the two Kotlin compilation errors reported by CI:

- `AssistantController.kt`: invalid `\s` Kotlin string escape changed to `\\s+` for the regex.
- `AutomationManager.kt`: removed the unavailable `AccessibilityNodeInfo.ACTION_IME_ENTER` symbol and use the API-30 action value with the existing runtime API guard.

The touch-reliability and universal keyboard-send changes from 7.5.0 are preserved.
