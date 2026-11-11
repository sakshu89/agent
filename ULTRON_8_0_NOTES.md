# ULTRON 8.0 — Universal Android Agent Core

Version: **8.0.1** / versionCode **81**

## What changed

ULTRON 8.0 adds an app-agnostic agent layer above the existing 7.1 command and accessibility stack.

- Universal Goal → Plan → Execute → Verify → Recover → Re-plan guidance for complex requests.
- Runtime capability registry instead of topic-specific or app-specific planning rules.
- Bounded task context containing the current goal, completed steps, failures, and recoveries.
- Short-lived duplicate dispatch guard that suppresses immediate repeated command dispatches while preserving intentional later repeats.
- Planner context is supplied to the local LLM so multi-step tasks can be reasoned about without pretending an action already happened.
- Task context follows the existing privacy mode and is not written while privacy mode is enabled.
- Generic knowledge-request routing remains universal and ordinary research stays confirmation-free.
- Research, navigation, media, screen observation, and system-control commands can operate in Medium mode; consequential messaging remains Extreme/confirmation-gated.
- Existing Accessibility + OCR screen observation and Observe → Act → Verify → Recover protections remain in place.
- Existing LLM tool loop remains bounded and feeds verified results back before selecting the next action.

## Safety

The agent layer does not bypass Android permissions or existing safety checks. Purchases, money transfers, deletion, external messaging, account/security changes, and other consequential actions remain confirmation/safety gated.

## 8.0.1 reliability hardening

- Fixed the universal agent loop so an unverified target is never selected again in the same recovery pass.
- Fixed duplicate-click suppression so a suppressed click is reported as a failed dispatch rather than a false success.
- Click verification now fails closed when a state change is required; it never re-clicks an unchanged target automatically.
- Message sending uses a single dispatch followed by explicit message evidence verification, avoiding duplicate sends after timeouts.
- Resumable agent tasks never automatically retry a consequential message-send step. Safe steps have at most two bounded attempts.
- Added bounded task-context buckets so failures/recoveries cannot grow without limit.
- Added the universal search/form submit fallback: IME action first, then a freshly observed visible submit/search control.
- Fixed operating-mode gating so ordinary research remains available in Light mode while disallowed Android actions remain blocked.
- Expanded generic knowledge-request recognition without adding topic-specific rules.
- Updated GitHub Actions artifact/workflow labels from the old 3.0 names to 8.0.

## Verification

Static source checks and archive integrity are performed during packaging. Gradle compilation is reported separately and is only claimed when the Gradle task actually succeeds. In the current build environment the Gradle wrapper cannot download Gradle 8.13 because `services.gradle.org` is unreachable, so Android compilation remains **unverified**, not falsely reported as successful.
