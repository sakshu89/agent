# ULTRON 3.0

This is an upgrade of the existing ULTRON Android project, not a replacement project.

## Implemented in this revision
- Gold/amber AMOLED futuristic HUD with animated holographic core.
- CORE, VOICE, LOGS, STATUS and SETTINGS screens.
- Idle/listening/thinking core animation states.
- Persistent wake/mic/noise/speech/personality/privacy/performance controls.
- Natural conversation history: recent turns are supplied to the LLM.
- Time-aware startup greeting, once per local calendar day.
- Open-Meteo current weather integration using coarse/fine device location.
- Existing Vosk offline recognition and Android SpeechRecognizer fallback retained.
- Existing notification listener, accessibility automation and messaging confirmation retained.
- Existing web-grounded LLM path retained; current questions are grounded before generation.
- Android permission handling for microphone, notifications and location.

## Important limitations
- Owner voice verification remains an experimental setting. The current project does not contain a speaker-embedding model, so ULTRON must not claim biometric-grade speaker identification.
- Android controls microphone/background behavior. The foreground microphone service is retained and Android restrictions are respected.
- Accessibility automation can only operate on UI elements exposed by the target app; ULTRON reports failure when it cannot verify an action.
- Messaging is confirmation-gated. Calls are not implemented.
- The LLM backend remains configurable through `BuildConfig.LLM_BASE_URL`; do not put private API keys in source code.

## Build
Open in Android Studio and run `./gradlew :app:assembleDebug` or use Android Studio's Build > Make Project.
The supplied execution environment could not download Gradle 8.13 from `services.gradle.org`, so an APK is not falsely reported as built here.

## Runtime setup
1. Configure the local/cloud OpenAI-compatible `/v1/chat/completions` endpoint used by the project.
2. Import a compatible Vosk model through ULTRON Settings.
3. Grant microphone permission for voice features.
4. Grant notification access if notification features are desired.
5. Enable ULTRON Accessibility Service for app navigation/messaging automation.
6. Grant location permission only if weather/travel features are desired.
