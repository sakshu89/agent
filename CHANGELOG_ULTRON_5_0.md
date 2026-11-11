# ULTRON Changelog

## 6.1.0 — Automation Reliability

- Added click serialization and duplicate-click suppression.
- Added target-aware click cooldown and click-rate limiting.
- Added lightweight screen fingerprint verification after dispatched clicks.
- Prevented automatic re-click loops when a screen does not change.
- Prevented overlapping tap requests while a tap is settling.
- Removed normal app-launch task-stack clearing.

# ULTRON 5.0 changelog

## Added
- Persistent explicit long-term memory with local SQLite storage.
- Memory retrieval injected into local-model context.
- Memory deletion and privacy-mode protection.
- Local authenticated HTTP integration server.
- Configurable local server port and localhost-only/private-LAN binding.
- Optional local Whisper-compatible multilingual transcription adapter.
- Recognition alternative/confidence capture and duplicate suppression.
- Fuzzy installed-app resolution for speech recognition errors.
- Official Android controls for ringer mode, media volume and brightness.
- Runtime diagnostic checks and diagnostic report generation.
- Generic local model package registry/import/removal.
- Agent per-step retry and timeout handling.
- ULTRON 5.0 versionCode/versionName.
- Status/Settings UI for diagnostics, Whisper configuration and local server controls.

## Preserved
- Compose HUD and existing UI identity.
- Vosk import/selection and Android speech fallback.
- AccessibilityService automation and OCR.
- Existing messaging confirmation and verification flow.
- Session history, personal instructions, notification allowlisting, web search and weather.
- Existing WorkManager task queue.

## Known incomplete / device-dependent
- A bundled native Whisper runtime/model is intentionally not included.
- Full continuous Whisper streaming/VAD is not implemented; Whisper adapter is tap-to-talk.
- Visual scene understanding beyond OCR/UI accessibility semantics remains limited.
- Cross-app selectors can change with target-app versions and must be tested on-device.
- Full Android Gradle build/device test remains to be run in Android Studio/CI with network access.
