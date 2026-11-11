# ULTRON 10.0 — Build report

## Source checks
- Source archive is derived from `ULTRON_9_0_PHASE1_INTELLIGENCE_SOURCE.zip`.
- Existing Android, core and desktop modules retained.
- Added core packages: `workflow`, `security`, `diagnostics`, `skills`.
- Updated Android version metadata to `10.0.0` / versionCode `100`.
- Updated desktop UI identity and CI artifact labels to `10.0`.

## Executed checks
- `javac --release 17` compiled every `core/src/main/java` source successfully.
- A standalone Java smoke test passed workflow-journal append/read, secret redaction, interrupted-task recovery marking, deny-by-default arbitrary-command policy and cloud-consent checks.
- Desktop Java sources passed a Java 17 syntax/type check against minimal Gson API stubs. This is not a substitute for compiling against the real Gson artifact or running Gradle.
- JUnit tests were added for workflow journal, permission policy, bounded trace and skill manifest; JUnit execution was not performed.
- Archive integrity was checked after packaging.

## Not claimed
- No Android APK, Windows installer or Linux package was built in the offline packaging environment.
- Gradle 8.13 distribution download was previously blocked by DNS/network resolution for `services.gradle.org`.
- JUnit test execution, Android instrumentation, real Windows UIA, Linux AT-SPI, Wayland and Android Accessibility tests are not certified by this report.

Run the commands in `ULTRON_10_0_RELEASE.md` and attach CI logs before treating the release as production-ready.
