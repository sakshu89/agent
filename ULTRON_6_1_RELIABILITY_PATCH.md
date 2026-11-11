# ULTRON 6.1 — Automation Reliability Patch

This patch upgrades ULTRON 5.0 to 6.1.0 and targets repeated/missed clicks and UI lag during accessibility automation.

## Reliability changes

- Serializes click execution with an in-flight guard.
- Suppresses identical target clicks during a short target-aware cooldown.
- Adds a global click rate limiter (maximum 5 dispatched clicks per second).
- Captures a lightweight accessibility-tree fingerprint before a click and observes again after the click.
- Gives the target app a short settle window before verification.
- Does **not** automatically re-click when the UI does not change. The agent must re-observe and make a new decision.
- Prevents overlapping tap requests while another tap is settling.
- Keeps the emergency-stop path through the existing task manager.
- Removes `FLAG_ACTIVITY_CLEAR_TASK` from normal app launches so ULTRON does not unnecessarily clear the target application's task stack.

## Important behavior

A click returning `true` means the click was dispatched safely; it does not mean ULTRON blindly assumes success. Existing task-specific verification remains responsible for confirming the desired outcome.

## Build note

The project was structurally checked after modification. A local Gradle compile could not be completed in this environment because the configured Gradle 8.13 distribution was not cached and network access to `services.gradle.org` was unavailable.
