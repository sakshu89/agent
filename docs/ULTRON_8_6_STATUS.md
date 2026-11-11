# ULTRON 8.6 implementation status

## Implemented in this source upgrade

- Existing Android application retained; application version is 8.6.0 / versionCode 86.
- New Java 17 `core` module shared by Android and desktop.
- Shared platform contracts for capabilities, observation, actions, state and bounded execution.
- Shared `AgentRuntime` performs Observe → Plan/capability check → permission gate → one action → Verify. It never automatically retries an action.
- Android adapter contract delegates actual observation and execution to an explicitly supplied bridge and reports Android accessibility permission state.
- Windows desktop adapter uses Java AWT Robot for local screen observation and mouse/keyboard input; app launch is allowlisted.
- Linux desktop adapter uses Java AWT Robot and allowlisted application launch commands. Robot support depends on the active desktop session; Wayland/headless limitations are expected.
- Desktop Swing Command Center with visible confirmation for every action and Emergency STOP.
- AES-256-GCM authenticated encryption helper for payloads when an explicit 32-byte paired key is supplied.
- CI jobs for Android, Windows and Linux only.
- Unit tests for shared runtime permission handling, bounded dispatch, capability rejection and encrypted envelope integrity.

## Not yet complete; do not treat these as production-ready

- The Android adapter is a bridge boundary, not yet wired into every existing Android automation service.
- Cross-device discovery, pairing UI, key provisioning/revocation, authenticated network transport, reconnect logic and task/file-transfer orchestration are not implemented. `SecureEnvelope` is a cryptographic payload primitive, not a complete pairing/transport system.
- Windows/Linux window enumeration, accessibility-tree reading, advanced clipboard APIs, notifications, task monitoring, file picker/transfer and robust desktop-environment-specific control are not complete.
- Linux desktop automation is best-effort and may be unavailable under Wayland or headless sessions.
- The desktop command interface is intentionally small and does not execute arbitrary shell commands.
- Release signing, installer signing, auto-update, and platform-specific installer formats are not configured.
- Device-level Android runtime behavior still requires a physical device/emulator.

## Build truth

No build is marked successful until the corresponding GitHub Actions job completes. CI builds Android debug APK and packages desktop app images on Windows/Linux. It does not create a signed Android APK or a signed Windows installer.
