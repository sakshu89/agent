# ULTRON 7.5.2 — Voice Accuracy / Complete-Utterance Patch

- Android SpeechRecognizer now requests partial results for diagnostics but never executes a partial transcript.
- Android recognition waits for a complete utterance using longer silence thresholds and up to 5 alternatives.
- Vosk intermediate `onResult` callbacks are ignored; only `onFinalResult` reaches ULTRON.
- Vosk tap-to-talk timeout increased to 12 seconds so longer commands are not cut off.
- Stale Android alternatives are cleared at the beginning of each new listening session.
- Existing media/wake-word and command behavior is preserved.

This improves transcript completeness and reduces early/truncated commands. Recognition accuracy still depends on the installed Android speech engine or imported Vosk model; no speech recognizer can guarantee literal 100% accuracy.
