# ULTRON 4.1.1 implementation pass

This pass intentionally keeps model inference on a user-configured local OpenAI-compatible server. No cloud AI provider, API key, or remote inference provider was added.

## Implemented in source

- Local inference endpoint and model identifier are now configurable in Settings. `LocalServerConfig` accepts localhost and private IP literals only, rejects public endpoints and embedded credentials, and is read dynamically for each request.
- Operating modes now have differentiated action gates: LIGHT blocks automation; MEDIUM allows low-impact actions; EXTREME permits the broader action set. Android permission prompts and explicit send confirmation still apply.
- Bounded tool continuation: after a verified successful tool action, ULTRON can ask the local model what safe step is next. It stops after four tool actions, stops on failure, and message sending remains a separate explicit confirmation path.
- On-device OCR is wired as `read_screen_tool`. It captures the active display only on request through the AccessibilityService, runs bundled ML Kit Latin text recognition locally, and does not save the screenshot.
- Durable task infrastructure: SQLite stores task plans, step position, approval state, and failures; WorkManager runs/resumes queued plans after process restarts. Each task is capped at 30 steps. Send-message and marked consequential steps pause for approval. The queue is exposed as `AgentTaskQueue` for future UI/planner integration.
- Multiple offline Vosk models can be imported and switched without replacing the previous model. Models remain in private app storage.
- Notification listener now has an opt-in/allowlist settings UI, extracts only title/text for explicitly enabled and allowlisted packages, classifies events locally, and emits in-memory events. It does not persist notification content or send it to the LLM.
- Privacy mode now suppresses conversation content from session persistence and assistant-content logging. In-memory conversation context remains available while the process is alive.
- UI no longer presents microphone gain, noise suppression, or speaker verification as working controls; the underlying audio APIs in this project do not support those controls reliably.
- App version name updated to 4.1.0.

## Dependencies added

- WorkManager KTX for durable background execution.
- Bundled ML Kit Latin text recognition for offline OCR.

## Remaining work / limitations

- The task queue has storage and worker execution, but there is not yet a task-planning screen or an LLM-to-queue plan-review UI. The bounded interactive tool loop is currently the user-facing multi-step path.
- OCR is Latin-script text recognition, not general visual scene/icon understanding. Additional ML Kit script recognizers or a local vision model would be needed for other scripts and image semantics.
- Browser control is through existing Android app launch/accessibility interaction plus OCR, not a dedicated embedded DOM automation engine.
- Notification classification is heuristic and event delivery is in-memory; a user-facing inbox/summary view remains to be built.
- Multilingual offline ASR depends on the Vosk models the user imports. Offline TTS depends on the voices installed on the Android device.
- Microphone gain/noise suppression and speaker identity verification remain unsupported; the UI now says so rather than pretending they work.
- Failure handling checkpoints each task step and lets WorkManager retry after interruption, but cannot guarantee exactly-once behavior for external actions whose result is ambiguous. Duplicate-sensitive actions require additional app-specific verification.
- Private-LAN HTTP support requires cleartext networking at the Android network-security layer. The configured inference endpoint is still validated as localhost/private-network only; use HTTPS on untrusted Wi-Fi.

## Build verification

A debug build was attempted with the included Gradle wrapper. It could not start because this environment cannot resolve `services.gradle.org` to download Gradle 8.13. No successful APK build or device test is claimed. Run `./gradlew :app:assembleDebug` in Android Studio or CI with Gradle 8.13 and Android SDK 36 available.
