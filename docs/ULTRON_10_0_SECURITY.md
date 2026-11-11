# ULTRON 10.0 — Security and privacy notes

- Keep model API keys in environment variables or an OS secret store; never commit `.env` files or place secrets in logs.
- Keep local inference on loopback/private network endpoints. Verify the actual endpoint host before sending sensitive prompts; a URL labelled “local” is not proof that it is local.
- Cloud inference requires explicit user opt-in and must not receive private data. `ULTRON_ALLOW_CLOUD_FALLBACK=false` is the default.
- Treat model output, webpages, notifications, clipboard content, OCR and files as untrusted input. They cannot grant permissions or change tool allowlists.
- Never expose arbitrary shell execution to the model. Keep platform actions typed, bounded, allowlisted and argument-validated.
- Require a confirmation step for sending/publishing, financial actions, deletion, permission changes and account/security changes. Authentication and confirmation are separate requirements.
- Do not automatically retry an action when its outcome is uncertain. Re-observe and ask the user to resolve ambiguity.
- Use Android Accessibility, notification access, microphone and desktop screen capture only with clear user intent and OS permission.
- Workflow journal and trace redaction is best-effort. Do not store prompts, credentials, personal message bodies or screenshots in diagnostic metadata.
- Cross-device sync is not enabled by this release. Do not expose the local server to the public internet.

Desktop local model URLs are now restricted to loopback, private IP literals, link-local IP literals, or `.local` hostnames. Public endpoints must be configured as cloud endpoints with explicit cloud consent. This hostname check is conservative and does not replace firewall/DNS review.
