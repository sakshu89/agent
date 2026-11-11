# ULTRON 8.7 — Hybrid Local/Cloud Agent

This is an incremental upgrade of the supplied ULTRON source. OpenJarvis was used as an architectural reference (provider abstraction, agent/tool separation, local-first routing, explicit privacy boundaries and measurable execution), not copied as a replacement application.

## Implemented in this source package

- Shared `:core` model-mode and routing primitives (`LOCAL`, `CLOUD`, `AUTO`).
- Local-first `AUTO` policy. Cloud fallback is only permitted when explicitly enabled, configured and not blocked by privacy/sensitive-content checks.
- Android Settings → Hybrid Intelligence configuration for local/cloud/auto selection.
- Android cloud endpoint/model configuration and AES-GCM API-key encryption using Android Keystore.
- Android LLM requests use the selected provider while preserving the existing tool-call loop.
- Android `AUTO` falls back only for transport errors, HTTP 408/429 and HTTP 5xx. HTTP 400 response bodies are now logged (truncated) and surfaced to diagnose incompatible local-server request schemas.
- Windows/Linux desktop OpenAI-compatible model client and environment-based provider configuration.
- Desktop natural-language requests are converted into a narrow allowlisted action schema, then still pass through ULTRON's existing permission confirmation, capability checks, emergency stop and verification runtime.
- Desktop privacy-mode environment switch and sensitive-content guard.
- Added routing tests and a setup guide.

## Configure Android

Open Settings → Hybrid Intelligence.

- `LOCAL`: local endpoint only.
- `CLOUD`: configured HTTPS cloud endpoint only. If unavailable or blocked, ULTRON reports an error instead of silently switching providers.
- `AUTO`: local first, with cloud fallback only for permitted, non-sensitive prompts and eligible transport/server failures.

Cloud endpoint should be a base URL such as `https://api.openai.com/v1`. ULTRON appends `/chat/completions`. The provider must support OpenAI-compatible chat completions, streaming and tool calls.

## Configure Windows/Linux

Set environment variables before launching ULTRON. See `desktop/.env.example` and `HYBRID_AI_SETUP.md`. Do not commit API keys. Desktop secrets are read from the process environment; use the operating system's secret manager or a protected launcher to supply them.

## Validation performed

- Java 17 compilation passed for shared core, platform controllers and desktop model adapter.
- Java 17 syntax compilation passed for desktop UI against compile-only Gson API stubs; the real Gson dependency is declared in `desktop/build.gradle.kts`.
- Hybrid routing smoke test passed: local-first, sensitive-data cloud block, no fallback on HTTP 400, fallback eligibility on HTTP 503.
- ZIP integrity checked after packaging.

## Not yet verified / remaining work

- Full Gradle/Android build and JUnit suite could not run in this environment because Gradle 8.13 distribution download failed with `UnknownHostException: services.gradle.org`.
- Android Kotlin/Compose compilation and physical-device cloud/local requests need GitHub Actions or Android Studio validation.
- Desktop model routing and allowlisted natural-language action path compile at Java source level, but Windows and Linux runtime/provider tests remain outstanding.
- This is not yet a complete OpenJarvis feature-port. Deep-research agents, dynamic skill marketplace, trace-based learning/evaluation, full screenshot/OCR planning, desktop speech, cross-device pairing and persistent autonomous scheduling still need implementation.
- Privacy detection is a protective heuristic, not a complete DLP guarantee. Review cloud prompts and use Privacy Mode for sensitive work.
