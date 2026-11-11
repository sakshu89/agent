# ULTRON 8.6 — Cross-platform microphone/media coexistence patch

## What this patch changes

### Android
- The local Whisper one-shot recorder now uses `MediaRecorder.AudioSource.VOICE_RECOGNITION`.
- Recording is stopped and released in a `finally` block, including error paths.
- Capture duration is bounded to 0.5–15 seconds.
- This path does not request audio focus, change `AudioManager` mode, change volume, or pause another app.

### Windows and Linux
- Adds `DesktopMicrophoneCapture`, a Java Sound microphone capture primitive.
- It opens the default capture endpoint and streams PCM frames on a daemon worker.
- It does not request playback audio focus, alter output volume, or issue media transport commands.
- The caller must close the capture when listening stops.

## Important current-project limitation

The supplied desktop application does not yet contain a voice-recognition/listening loop. This class is a safe microphone-capture foundation; it is not wired to a speech recognizer and does not claim to add hands-free voice commands to Windows/Linux. A recognizer and lifecycle integration are still required.

The Android project also uses Android `SpeechRecognizer` and Vosk in addition to local Whisper. Android's system `SpeechRecognizer` is implemented by the device's recognition service; ULTRON cannot force every vendor recognizer to preserve concurrent playback. The strongest controlled path is a direct local capture engine (Vosk/AudioRecord) that does not request audio focus. Device-specific playback coexistence must be verified on real hardware.

## Required regression tests

1. Start media playback in YouTube/Spotify.
2. Start ULTRON microphone capture and keep it listening for at least 2 minutes.
3. Verify playback does not pause, duck, or switch output device.
4. Speak a command and verify capture is released/paused only for ULTRON's own TTS if required.
5. Stop ULTRON and verify the microphone indicator disappears.
6. Repeat on Windows with shared-mode audio and Linux under both PipeWire/PulseAudio and ALSA where supported.

No universal guarantee is possible where an OS audio server, device driver, privacy policy, or third-party speech provider enforces exclusive capture or media interruption.
