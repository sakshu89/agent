# Error-fix pass on 4.1.1

Static review of every source/config file (no Android SDK or network was available, so no APK build was run).
Pure-Kotlin files (CommandParser, AutomationStateMachine, AgentState) were compiled with kotlinc and parser smoke-tested.

- ScreenUnderstanding: OCR crashed/failed because ML Kit cannot read a HARDWARE bitmap. The screenshot is now copied to an ARGB_8888 bitmap, the HardwareBuffer is always closed, and recognizer exceptions are reported instead of leaving the request hanging.
- AssistantController: Privacy mode still wrote a session and a title built from the user's words. Nothing is persisted now while it is on.
- AssistantController: in MEDIUM mode ULTRON asked "confirm send?" and only then refused. The mode is checked before the confirmation prompt.
- AssistantController/LlmClient: the internal "Continue fulfilling..." tool follow-up could trigger a Google scrape of that whole prompt. Follow-ups never web-search now (`allowWebSearch`).
- LlmClient: every "what is / how does" question failed with an error when Google blocked the scrape. Stable questions now fall back to the local model; only real current-info questions (news, price, weather...) still error.
- ModeManager: MEDIUM blocked "play the first video" and "open Instagram chats" (low-impact); now allowed.
- proguard-rules.pro: keep rules for the Gson-serialised task models; R8 release builds would otherwise break the task queue.
- VoskModelManager: `result` initialised up front (definite-assignment safety).

## CI compile fix (UltronHud.kt)
`GlassButton` had `filled: Boolean` as its last parameter, so `GlassButton("...", accent) { ... }` passed the lambda as `filled`
("No value passed for parameter 'onClick'", UltronHud.kt:499/554/558). `onClick` is now the last parameter and the other call sites use named arguments.
