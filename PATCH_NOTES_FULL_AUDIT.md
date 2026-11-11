# Full audit fixes

Compile errors (from CI log)
- AssistantController: invalid `"\s+"` escape -> `"\\s+"`.
- AutomationManager: added missing `findMessageEvidence` (verifies the sent message is visible in the chat).
- UltronHud: `TopBar`/`StatusDot` now receive `mode`.

UI
- Settings switches/sliders now respond (UltronSettings values are Compose state).
- Voice/Status/Settings tabs no longer push the bottom buttons off-screen.
- Edge-to-edge (targetSdk 35): content respects system bars and the keyboard.
- Animation-scale slider and performance profile now affect the core animation; animation state read in draw phase (no per-frame recomposition).
- Storage usage computed off the main thread.

Logic
- Tap-to-talk no longer stalls while ULTRON is speaking; it can answer a pending "confirm?" prompt.
- LLM tool calls respect Light mode (previously bypassed it).
- "go to sleep"/blank input handled before the Light-mode gate.
- Hands-free timeout stops the foreground service and re-arms if it fired early.
- LLM replies are saved to the session; sessions are created lazily (no empty session per launch).
- Weather callbacks delivered on the main thread; HTTP responses closed.
- Web-search keywords match whole words ("know" no longer triggers "now").
- LLM read timeout is finite (90 s) instead of infinite.
- Google search never fires both success and error callbacks.
- Vosk model import releases the engine on the main thread before replacing files.
- Parser: "tell me that ...", "text me ..." are no longer treated as messages to a contact called "me".

Automation safety
- A second send request no longer cancels the running one and then fails as "busy".
- First-name contact fallback only accepts a single unambiguous match (prevents messaging the wrong person).
- Apps are launched through the accessibility service context.

Other
- SQLite foreign keys enabled; safe startActivity for settings/folder pickers.

Known limits (not changed)
- wake/mic sensitivity, noise suppression and owner verification settings are stored but not yet used by the audio pipeline.
- MEDIUM vs EXTREME modes are not yet differentiated (only LIGHT is gated).
- Google results are scraped from HTML and may break or hit a consent page; a search API is more reliable.
