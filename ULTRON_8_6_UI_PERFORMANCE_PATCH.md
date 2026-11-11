# ULTRON 8.6 — Adaptive UI & Performance Patch

## UI behavior
- Android retains its existing Compose-first, touch/voice-oriented `UltronHud` and Android permission flows.
- Windows and Linux use a responsive desktop Command Center with OS-native Swing Look & Feel, resizable layout, compact status header, activity/verification feed, command bar, keyboard shortcuts, and emergency stop.
- Native Look & Feel is selected at runtime, so controls follow the host OS where its Java runtime provides a native implementation. The ULTRON visual identity and task states remain consistent.
- Desktop permission prompts are marshalled onto Swing's Event Dispatch Thread.
- Activity history is capped at 1,200 lines to prevent unbounded UI memory growth.

## Performance changes
- Desktop agent actions run on a single reusable daemon executor rather than creating a new thread for every command. This bounds thread creation and serializes UI-issued actions.
- Desktop Robot input delay reduced from 45 ms to 15 ms. This reduces the configured per-input delay; actual speed remains dependent on the OS and target application.
- Screenshot verification sampling stride changed from 8 pixels to 12 pixels in each dimension, reducing sampled pixel/hash work by approximately 56% while retaining before/after observation and verification. This may miss very small localized visual changes; no automatic retry is introduced.
- Log retention is bounded; the UI does not retain an unlimited activity history.

## Honest performance statement
“100x faster” cannot be guaranteed from source edits alone. No 100x benchmark has been measured. These changes target measurable overhead and responsiveness without weakening permission checks, emergency stop, or one-action verification. Benchmark on representative Windows and Linux hardware before making numeric speed claims.

## Validation
- `javac` compilation of all `core` and `desktop` Java sources: PASS (OpenJDK 21).
- Gradle task `:core:compileJava :desktop:compileJava`: NOT RUN; Gradle wrapper could not download Gradle 8.13 because `services.gradle.org` DNS/network access failed (`UnknownHostException`).
- Android APK build and Windows/Linux packaged installers: NOT verified in this environment.
