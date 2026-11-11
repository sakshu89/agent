# ULTRON 8.6 — Hybrid Local + Cloud AI

ULTRON now has a shared provider-routing policy in `:core` and platform adapters for Android and desktop.

## Modes

- `LOCAL`: always use the configured private/local OpenAI-compatible inference server. No cloud fallback.
- `CLOUD`: use the configured HTTPS cloud provider. Android refuses to silently fall back to local if cloud is not configured. Obvious sensitive content and Privacy Mode block cloud routing.
- `AUTO`: local-first. Cloud fallback is allowed only when a cloud key is configured, Privacy Mode is off, and the prompt/context does not match the sensitive-data guard. Fallback is limited to transport errors, HTTP 408/429 and HTTP 5xx; HTTP 400 is surfaced with the provider response body because it normally indicates a malformed/unsupported request.

## Android setup

Open ULTRON → Settings → Hybrid Intelligence.

1. Keep Local mode to use your local inference server.
2. For Cloud or Auto, enter an OpenAI-compatible HTTPS base URL, provider model ID and API key.
3. Save. The API key is encrypted using an AES-GCM key held by Android Keystore.
4. Select Auto for local-first routing. Select Cloud only when you intentionally want eligible prompts sent to the configured provider.

The endpoint field is a base URL, for example `https://api.openai.com/v1`; ULTRON appends `/chat/completions`.

## Windows / Linux setup

Desktop settings are read from environment variables (do not commit secrets):

```bash
ULTRON_AI_MODE=LOCAL
ULTRON_LOCAL_BASE_URL=http://127.0.0.1:8080/v1
ULTRON_LOCAL_MODEL=local-model
ULTRON_CLOUD_BASE_URL=https://api.openai.com/v1
ULTRON_CLOUD_MODEL=your-model-id
ULTRON_CLOUD_API_KEY=your-secret-key
ULTRON_ALLOW_CLOUD_FALLBACK=false
ULTRON_PRIVACY_MODE=false
```

Set `ULTRON_AI_MODE` to `LOCAL`, `CLOUD`, or `AUTO`. Set `ULTRON_PRIVACY_MODE=true` to block cloud routing. Set `ULTRON_ALLOW_CLOUD_FALLBACK=true` to explicitly enable local-first fallback on desktop. `CLOUD` mode requires a configured HTTPS endpoint, model and key. The desktop client is an OpenAI-compatible reasoning adapter; the existing desktop command UI still needs its natural-language planner connected to this client in a subsequent integration step.

## Provider compatibility

The configured provider must accept OpenAI-compatible `/chat/completions` requests, streaming responses on Android, and function/tool definitions for tool-using requests. Some local servers expose different subsets of the API. ULTRON now logs the HTTP error response body (truncated) so a 400 response can be diagnosed rather than reduced to a status code.

## Privacy and limitations

- Android API keys are encrypted at rest with Android Keystore; desktop API keys are supplied through the process environment and should be stored in the OS secret manager by the launcher/operator.
- The sensitive-content detector is a conservative heuristic, not a complete data-loss-prevention system. Review prompts before selecting Cloud mode.
- Cloud use requires internet access and a provider account/key. This patch does not include paid API credits.
- Model routing does not grant new OS permissions or bypass the existing execution confirmation and emergency-stop controls.
