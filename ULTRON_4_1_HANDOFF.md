# ULTRON 4.1 handoff

## Added in this handoff
- LLM-callable safe screen controls: back, home, scroll up/down, skip ad, and tap a specifically named visible control.
- LLM-callable public website opening, restricted to HTTP/HTTPS URLs.
- Deterministic parser support for `open https://example.com`.
- Explicit multilingual response policy, ambiguity clarification, supported-task planning, action verification, and confirmation requirements for consequential actions.
- Settings UI disclosures for language dependencies and the current autonomy boundary.
- R8 + Android resource shrinking for release builds, with keep rules for Vosk/JNA JNI usage.

## Checks performed
- Compiled `CommandParser.kt` with the standalone Kotlin compiler.
- Ran parser smoke tests for URL opening, back, scroll, compound open-and-play, and WhatsApp message parsing; all returned expected command objects.
- Attempted `./gradlew :app:assembleDebug`, but the environment could not resolve `services.gradle.org` to download Gradle 8.13. No APK build or device test is claimed.

## Still required before calling this a complete autonomous agent
- Build and test debug + release APKs in Android Studio/CI with network access.
- Test accessibility selectors on the target phone and current versions of target apps.
- Add screenshot/OCR/screen-understanding with explicit privacy controls.
- Implement durable multi-step planner/executor with checkpoints, action-result feedback, cancellation, and recovery.
- Add configurable AI provider endpoint/model and encrypted credential storage.
- Add robust language selection and per-language offline speech model management.
- Add notification content processing only behind explicit opt-in, app allowlists, and local privacy controls.
- Add user-visible task queue, scheduled tasks, and permission-aware background execution.

ULTRON is a permission-respecting Android assistant foundation. It is not yet equivalent to an unrestricted desktop agent, and it must not claim actions it has not verified.
