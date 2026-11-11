# ULTRON 5.0 — implementation documentation

## Architecture

ULTRON 5.0 remains an incremental upgrade of the supplied Android project.
The existing Compose UI, deterministic command parser, Vosk engine, Android speech fallback,
AccessibilityService, OCR, local LLM client, session storage, notification listener and WorkManager
agent queue were retained.

### Runtime pipeline

1. Voice/typed input
2. Speech normalization + wake-word handling
3. Deterministic command parser
4. Local memory/context lookup
5. Local LLM tool planning when deterministic parsing is insufficient
6. Permission-aware command executor
7. Accessibility/OCR verification where needed
8. Persistent task/session logging

Core AI remains local-first. The configured LLM endpoint is restricted to localhost/private-network
addresses. Web search remains an explicit optional path for current-information questions.

## New 5.0 modules

- `memory/LongTermMemoryManager.kt` — explicit local memories with category/key/value records,
  search, update-by-key, and deletion.
- `diagnostics/DiagnosticManager.kt` — runtime checks for permissions, accessibility, models,
  SQLite stores and storage; report generation.
- `model/LocalModelManager.kt` — private-storage model package registry/import/removal for future
  model engines without bundling large models in the APK.
- `WhisperLocalConfig.kt` + `WhisperLocalEngine.kt` — optional local Whisper-compatible HTTP
  transcription adapter. It rejects public endpoints and is used for tap-to-talk only when enabled.
- `server/LocalApiServer.kt` + `LocalServerService.kt` — authenticated local HTTP integration boundary,
  localhost-only by default.
- Expanded system controls for ringer mode, media volume and brightness, using official Android APIs
  and special-access prompts where required.
- App-name fuzzy matching for likely speech-recognition mistakes.
- Recognition alternatives/confidence capture for Android SpeechRecognizer and duplicate-result suppression.
- Agent step timeout/retry handling (up to three attempts per step, 60 seconds per attempt).
- Memory context is supplied to the local model only when privacy mode is off.

## Whisper limitation

ULTRON does not bundle a large Whisper weight file or a native whisper.cpp runtime into the APK.
Instead, 5.0 provides a real local HTTP adapter for a Whisper-compatible service running on the same
phone or a private LAN. This keeps the APK small and allows users to select the runtime/model they
prefer. Hands-free continuous recognition still uses Vosk or Android's offline-capable recognizer.

## Local API

Default integration server:

- Bind: `127.0.0.1`
- Port: `8765`
- Authentication: generated Bearer token
- `GET /v1/health` — unauthenticated health check
- `GET /v1/status` — authenticated runtime status
- `POST /v1/command` — authenticated JSON `{ "text": "..." }`; the text enters the same ULTRON
  command pipeline and therefore remains subject to operating mode, confirmations and Android permissions.

The server can be bound to the private LAN from Settings, but authentication remains required.

## Long-term memory commands

- `remember that my preferred music app is Spotify`
- `save this: use WhatsApp for family messages`
- `forget preferred music app`

Memory is explicit and local. Privacy mode prevents conversation-memory writes and model context
from using stored memories.

## System control examples

- `my phone silent kar do`
- `set volume to 50 percent`
- `set brightness to 30 percent`

Android special access is never bypassed.

## Model installation

### Vosk

Use the existing Settings → Import Vosk Model flow. Import a compatible Vosk ZIP, then select the
installed model. The model remains in private app storage.

### Whisper

1. Run a Whisper-compatible local transcription service.
2. Configure its local/private transcription URL in Settings → Offline Whisper.
3. Enable `local Whisper for tap-to-talk`.
4. Tap-to-talk records a short 16 kHz mono WAV and posts it only to the configured local endpoint.

No public/cloud endpoint is accepted by the configuration validator.

## Permissions

Core permissions are requested only when needed. Accessibility, notification access, Modify System
Settings and Notification Policy Access remain user-controlled Android special settings.

## Safety

Messaging remains confirmation-gated by default. The persistent task queue also requires explicit
approval for message-send and confirmation-required steps. Screen taps are restricted to non-sensitive
visible controls; the LLM is instructed not to use taps for purchases, account/security changes,
deletions or other consequential actions.

## Build verification

The project was inspected source-by-source and parser smoke tests were run locally. A full Android
Gradle build could not be completed in the current execution environment because Gradle 8.13 was not
cached and the wrapper could not reach `services.gradle.org`. No APK build success is claimed.

Recommended validation in Android Studio or CI with network access:

```bash
./gradlew clean assembleDebug
./gradlew test
./gradlew assembleRelease
```
