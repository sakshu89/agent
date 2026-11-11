# ULTRON 7.7 — Privacy Wake Gate

- Hands-free microphone recognition can remain active locally for wake-word detection.
- Ordinary speech is never forwarded to the LLM/server.
- The wake word must be at the beginning of the utterance.
- Casual mentions of “Ultron” in a conversation do not wake ULTRON.
- The previous 200-second open follow-up window is removed.
- Short answers are accepted without repeating the wake word only while ULTRON is actively waiting for an answer/confirmation.
- Existing media wake-only behavior remains enabled.

This is intentionally privacy-first: local ASR may still produce diagnostics, but no LLM request is created unless the wake gate passes.
