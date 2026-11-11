# ULTRON 7.3.0 — Media-only wake mode

- Normal hands-free speech is conversational/free by default.
- While Android reports active media/game playback, ULTRON requires the wake word "Ultron" (or configured alias) before processing a phrase.
- Playback is detected using AudioManager.isMusicActive plus active playback configurations on API 26+.
- The mode is universal across apps because it watches Android audio playback rather than app package names.
- A one-time migration changes the old default wake-word requirement to OFF for normal hands-free use and enables media-only wake gating.
- The existing "Only respond when I say Ultron" setting remains available; if enabled, it still gates normal hands-free speech too.
- This is a logical wake gate. Android third-party apps do not get a privileged hardware hotword detector; without a dedicated offline wake-word engine, the recognizer may still briefly capture audio, but ULTRON will not send non-wake media speech into the assistant pipeline.
