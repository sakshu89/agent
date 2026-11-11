# ITRA integration build status

## Implemented in this integration pass

- Used the newly uploaded `agent-main (1).zip` as the Android application base (not the older ULTRON 4.1.1 archive).
- Rebranded user-facing Android app identity and assistant wake phrase to ITRA while preserving Kotlin package names, application ID `com.ultron.app`, preference/database paths and the existing app data footprint for upgrade compatibility.
- Added an optional Hermes Agent 0.23.1 companion bridge using Hermes `AIAgent.run_conversation()`.
- Bundled the exact supplied Hermes source archive and added setup/run scripts.
- Added Android settings for enabling/configuring the bridge; bridge token is encrypted with Android Keystore.
- Routed non-device reasoning requests to Hermes when enabled; bridge failure falls back to the existing local model.
- Explicit Android actions bypass Hermes and continue through the existing Android tool/permission/verification path.
- Bridge is bearer-token authenticated, loopback-only by default, request-size limited, and only allows LAN binding with an explicit opt-in.
- Verified Python bridge syntax and exercised health + authenticated streaming chat endpoints against a stub Hermes agent.
- Parsed Android manifest and resource XML successfully.

## Not verified here

- Android/Kotlin compilation and APK assembly. `./gradlew :app:compileDebugKotlin --no-daemon` was attempted, but the wrapper could not download Gradle 8.13 because `services.gradle.org` was unreachable in this environment.
- Hermes runtime installation on the target Android/Termux device. Some Hermes dependencies and browser/terminal integrations may require a desktop Linux host or additional platform setup.
- Real-device voice recognition, accessibility, messaging and Android action flows. Existing behavior was preserved; it still requires device testing.

## Build/test on a connected development machine

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Then prepare Hermes with `agent-engine/setup_hermes.sh`, configure a local OpenAI-compatible model endpoint, start `agent-engine/run_bridge.sh`, and enable the bridge in ITRA settings.
