# ULTRON 7.1 — Universal Research & Web Reading Agent

## What changed
- Added a generic `Research` command for natural-language knowledge requests such as “tell me how X works”, “explain X”, “what is X”, “why does X…”, and similar forms.
- Research requests automatically open a normal Google web search without confirmation because ordinary information lookup is non-consequential.
- The existing Accessibility-driven automation path is reused; no topic-specific diode rule or app-specific selector is used.
- After opening the search, ULTRON re-observes the active screen, reads accessibility text, performs bounded scrolling, and uses the existing ML Kit OCR screen reader as a visual fallback.
- Added lightweight local answer synthesis to combine the most relevant readable lines into a concise spoken response.
- Existing TTS pipeline speaks the completed research answer.
- Existing 7.0 duplicate-click guards and Observe → Act → Verify → Recover loop remain unchanged.
- Risky actions remain governed by existing command/mode/confirmation handling; research itself does not request confirmation.

## Build note
This package was statically checked and ZIP integrity was verified. A Gradle compile is only reported as successful when the environment has the required Gradle/dependency artifacts available.
