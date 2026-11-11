# ULTRON 8.5 — Unified Upgrade

This release upgrades the existing hardened 8.0.1 foundation incrementally. Working architecture, safety gates, verification, duplicate-dispatch protection, memory, research routing, and accessibility automation are preserved.

## Included

### UI / Visual Engine
- Central visual-intensity policy tied to Light / Medium / Extreme operating modes.
- Light keeps normal/subtle colors and motion.
- Medium increases saturation, glow, and activity.
- Extreme increases saturation, glow, and animation intensity.
- Existing Command Center, voice, logs, status, and settings surfaces remain intact.
- Status capability dashboard expanded with planner, verification/recovery, vision, research, and emergency-stop state.

### Privileged device controls
- Added a private local privileged capability gate.
- Gate is not exposed as a normal UI setting or public wake-word alias.
- Device locking uses Android device-administration APIs when the required permission is configured.
- Reboot is restricted to device-owner configuration.
- Unsupported power operations remain unavailable rather than bypassing Android security.
- Privileged actions are confirmation-gated where destructive.

### Agent / Android control foundation
- Existing Observe → Act → Verify → Recover and duplicate-dispatch protection retained.
- Existing task planner, task context, memory, OCR/accessibility, and emergency-stop infrastructure retained.
- Capability reporting expanded for the unified agent architecture.

### Research
- Existing automatic knowledge-request routing and research/synthesis pipeline retained.
- Unified release keeps research available independently of automation mode.

## Version
- Version name: 8.5.0
- Version code: 85

## Build verification

The source tree and archive were checked after modification. A full Gradle Android build was attempted, but the build environment could not resolve `services.gradle.org` to download Gradle 8.13. Therefore this package is **not claimed as a successfully compiled APK** in this environment.
