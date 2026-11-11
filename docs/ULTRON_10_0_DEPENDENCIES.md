# ULTRON 10.0 — Direct dependency inventory

This is a source-declared direct dependency inventory, not a resolved Gradle dependency tree or legal clearance. Transitive dependencies and exact resolved versions must be generated with Gradle in a network-enabled build environment.

| Module | Direct dependency / plugin | Declared version | License review |
|---|---|---:|---|
| Root | Android Gradle Plugin | 8.13.0 | Verify upstream license and plugin metadata |
| Root | Kotlin Android / Compose plugins | 2.2.20 | Verify upstream license and plugin metadata |
| Core tests | JUnit Jupiter | 5.11.4 | Verify resolved artifacts |
| Desktop | Gson | 2.13.2 | Verify resolved artifact metadata |
| Android | AndroidX Core KTX | 1.17.0 | Verify resolved artifact metadata |
| Android | Activity Compose | 1.11.0 | Verify resolved artifact metadata |
| Android | Lifecycle Runtime KTX | 2.9.3 | Verify resolved artifact metadata |
| Android | Work Runtime KTX | 2.10.4 | Verify resolved artifact metadata |
| Android | ML Kit Text Recognition | 16.0.1 | Verify Google SDK terms and artifact metadata |
| Android | Compose BOM | 2025.09.00 | Verify resolved artifact metadata |
| Android | OkHttp | 5.1.0 | Verify resolved artifact metadata |
| Android | Gson | 2.13.2 | Verify resolved artifact metadata |
| Android | Kotlin Coroutines Android | 1.10.2 | Verify resolved artifact metadata |
| Android | JNA Android AAR | 5.13.0 | Verify artifact and native packaging terms |
| Android | Vosk Android | 0.3.47 | Verify upstream model/runtime license terms |

## Legal/release blockers
- No root `LICENSE` file was present in the audited ULTRON source archive. Do not assume the project may be redistributed under a particular open-source license.
- Model weights and Vosk model archives are not bundled; their licenses must be reviewed independently when users import them.
- Generate a resolved dependency report and an SBOM (CycloneDX or SPDX) in CI before public distribution.
- Review all transitive licenses, native binaries, notices and attribution requirements. This inventory alone is not legal advice or proof of license compliance.
