# ITRA — Intelligent Task & Reasoning Agent

ITRA is the next product identity built on the uploaded `agent-main` ULTRON Android codebase, with Hermes Agent 0.23.1 as an optional local reasoning/tool-execution engine.

## Preserved from the supplied Android project

- Existing Android app architecture, Compose HUD, Vosk speech pipeline and Android TTS.
- AccessibilityService, notification listener, app resolver and native Android command tools.
- Existing memory, task queue, workflow/agent safety gates and local inference support.
- Existing package/application ID `com.ultron.app` is intentionally retained so this can upgrade the installed ULTRON build without losing app data. The visible app name and assistant wake phrase are ITRA.

## Hermes integration boundary

Hermes is a Python agent runtime, not an Android library. ITRA therefore talks to a small local OpenAI-compatible bridge which invokes the Hermes `AIAgent` API. Run Hermes and the bridge on the same Android device through Termux (or on a trusted LAN computer). The Android app connects to the bridge over loopback/private LAN. The bridge binds to loopback by default and requires a bearer token.

See `agent-engine/README.md` for setup. The supplied Hermes Agent 0.23.1 source archive is included at `agent-engine/hermes-agent-0.23.1.zip`. Run `agent-engine/setup_hermes.sh` to extract it and install its Python environment, then start the companion with `agent-engine/run_bridge.sh`.

## Build

Open this directory in Android Studio and run `:app:assembleDebug`. This environment cannot guarantee a complete Android build because Gradle plugin/dependency downloads require network access.

## Important

The bridge is an optional companion process, not embedded Python in the APK. ITRA continues to work with its existing local model when the Hermes bridge is disabled or unavailable. Device actions still execute through ITRA's Android tools and permission gates; Hermes terminal/browser tools run only in the companion runtime.
