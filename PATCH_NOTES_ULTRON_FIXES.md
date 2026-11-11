# ULTRON v4 patch notes

## Fixed / improved
- `play the first video` now maps to a dedicated action that taps the first visible YouTube result instead of searching YouTube for the words "first video".
- `open chats on Instagram` / `open messages on Instagram` now opens Instagram and attempts to tap its inbox control through the Accessibility service.
- `send a DM to Instagram <username>` and `send a DM on Instagram to <username>` now select Instagram as the messaging app and ask for the message text, rather than reusing the last messaging app (which could be WhatsApp).
- `search on Google and tell me ...` strips the conversational lead-in so the actual search terms are sent to Google.
- `shutdown` / `power off` now returns an honest Android limitation instead of allowing the LLM to claim the device was shut down.
- Removed a duplicate completion callback from the Accessibility Back action.

## Important notes
- Instagram UI automation depends on Instagram's current screen layout and ULTRON's Accessibility service being enabled. If Instagram changes its UI, the inbox control may need selector updates.
- Vosk's `Model removed` log is emitted by the explicit Remove Model action in Settings. The log alone does not indicate that Vosk removed its own model. Re-import the model if it was removed.
- The parser was compiled and smoke-tested independently. A full Android APK build could not be run in this environment because Gradle 8.13 was not cached and network access to services.gradle.org was unavailable.
