# ULTRON 7.0 — Observe/Act/Verify + Self-Healing Upgrade

## What changed
- Added `UniversalAgentLoop`, a bounded Observe -> Act -> Verify execution loop.
- Generic screen taps now re-observe after an action instead of blindly repeating it.
- Candidate identity is tracked so the same UI node is not hammered during recovery.
- Recovery is bounded to one alternate pass for generic taps.
- Existing ULTRON 6.1 click guards remain active underneath the loop.
- Version bumped to 7.0.0 / versionCode 70.

## Reliability behavior
1. Observe the current accessibility tree.
2. Select a visible matching target.
3. Execute exactly one guarded action.
4. Wait briefly for the UI to settle.
5. Compare a fresh screen fingerprint.
6. If the UI changed, continue.
7. If nothing changed, re-observe once and allow a bounded alternate attempt.
8. Never enter an unbounded click/retry loop.

This layer is app-agnostic and applies to generic `tap` commands across apps.
