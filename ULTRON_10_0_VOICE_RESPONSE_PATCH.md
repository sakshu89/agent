# ULTRON 10.0 — Clean answers and voice interruption patch

## User-facing behavior
- The assistant's default answer style is now direct, concise, and conversational.
- It is instructed not to narrate search activity, tool calls, internal planning, raw URLs, or diagnostic traces.
- The diagnostics tab is labelled `DIAG` and shows warnings/errors only; routine INFO traffic and full assistant replies are not shown in the visible diagnostic feed.
- Assistant answer text is no longer copied into the diagnostic log as an `AI` entry.

## Voice interruption
- In hands-free mode, speech recognition remains active during text-to-speech.
- While ULTRON is speaking, ordinary recognized speech is ignored; exact short interrupt phrases (`stop`, `Ultron stop`, `stop speaking`, `be quiet`, `quiet`) are forwarded to the controller.
- The controller immediately flushes TTS without speaking a confirmation, so the interruption is silent.
- A shared matcher and unit tests cover accepted and rejected phrases.

## Limitations / validation
- Full duplex speech depends on the Android recognition service, microphone routing, and acoustic echo cancellation. Speaker echo may occasionally be recognized as speech; the strict phrase matcher reduces but cannot eliminate this risk.
- Vosk and Android SpeechRecognizer behavior must be tested on the target handset. No emulator or physical-device audio test was available here.
- Gradle verification was attempted but blocked because `services.gradle.org` could not be resolved in this environment. No APK build is claimed.
