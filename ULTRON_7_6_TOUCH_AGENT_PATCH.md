# ULTRON 7.6.0 — Visual Touch + Universal Screen Action Patch

## What changed
- Added on-device ML Kit OCR fallback for explicit `tap/click/press/select <target>` commands.
- Accessibility-node matching remains the primary path; OCR is used only when the target is not exposed in the accessibility tree.
- OCR taps use the exact detected text bounds and the existing serialized gesture guard, so ULTRON does not fire duplicate taps.
- Coordinate gestures wait for Android `GestureResultCallback` completion/cancellation.
- OCR fallback does not blindly retry when the accessibility tree is unchanged. A completed gesture is treated as a completed visual touch.
- This specifically addresses OEM Settings/WebView screens where visible controls such as `About phone` are not present in the accessibility node tree.
- Bumped app version to 7.6.0 (versionCode 77).

## Existing behavior preserved
- Deterministic back/home/scroll actions.
- Universal named-screen tap tool.
- Messaging safety confirmation.
- Anti-double-tap and click-rate guards.
- Raw voice transcript behavior.

## Build note
A full Gradle build could not be executed in this environment because Gradle 8.13 is not cached and the environment cannot resolve `services.gradle.org`.
