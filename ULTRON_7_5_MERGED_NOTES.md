# ULTRON 7.5.0 — Touch Reliability + Universal Keyboard Send

Merged from ULTRON 7.4 Universal Send and ULTRON 7.2 Touch Reliability.

## Touch reliability
- Observe → Act → Verify execution loop.
- Duplicate click suppression and action cooldown.
- One action at a time with settle protection.
- Refuses blind duplicate taps when dispatch is unverified.
- Re-observes instead of hammering stale coordinates/nodes.

## Universal send
- Prefer a visible Send control when available.
- If no Send control exists, use the focused editor IME action (keyboard Send/Enter).
- Refresh and retry through the keyboard action only when the first dispatch was not accepted.
- Verify the message after sending.
- No Snapchat-specific logic; applies across apps exposing an editable message field.

## Media wake behavior
- Normal hands-free use remains free speech.
- While media/game playback is active, ULTRON requires the wake word before processing speech.
