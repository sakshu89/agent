# ULTRON 7.2 — Touch Reliability / Anti-Miss-Tap Patch

## Fixed
- Universal tap recovery no longer repeats an action after Android accepted the tap but the accessibility tree did not visibly change.
- Accessibility clicks and coordinate gestures are mutually exclusive: ULTRON never sends both for one action.
- Accessibility nodes are refreshed before execution when possible, reducing stale-node misses.
- Coordinate gesture fallback now waits for Android's gesture completion/cancellation callback instead of treating dispatch acceptance as touch completion.
- Duplicate target taps remain suppressed with a short target-aware cooldown.
- Global click rate limiting remains enabled.
- Generic screen actions now distinguish `NOT_DISPATCHED`, `DISPATCHED_VERIFIED`, and `DISPATCHED_UNVERIFIED`.
- Unverified dispatched taps are never blindly retried.
- Added deterministic `volume up` / `volume down` support and LLM tool arguments so those commands no longer fail with `system_control_tool has incomplete arguments`.

## Safety behavior
`OBSERVE -> LOCK -> DISPATCH -> SETTLE -> VERIFY -> NEXT`

If Android accepts a touch but ULTRON cannot prove a visible state change, ULTRON stops that action instead of tapping again.

## Build note
The project was statically checked after modification. A full Gradle build could not be run in this environment because the Gradle 8.13 distribution is not cached and external network access is unavailable.
