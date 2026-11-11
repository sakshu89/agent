# ULTRON 8.8 — Shared Agent Core milestone

This incremental patch extends 8.7 without replacing the Android app or desktop controllers.

## Added shared contracts
- `core/agent/AgentTool` + `ToolRegistry`: discoverable, reusable, platform-neutral tools.
- `AgentPlan`, `AgentStep`, `PlanProvider`: bounded plans with a provider-neutral contract for local and cloud models.
- `AgentOrchestrator`: shared observe → permission → execute once → verify loop for all platform adapters.
- `Skill` + `SkillRegistry`: reusable declarative workflows that still pass through permissions.
- `ScreenUnderstandingProvider`: common UI/OCR/accessibility representation for Android Accessibility/ML Kit, Windows UI Automation/OCR, and Linux AT-SPI/OCR adapters.
- `ResearchProvider`: common source/result contract for platform-specific research implementations.

## Safety
- Hard limit of 20 steps per plan/skill.
- Duplicate step IDs blocked.
- Confirmation-required tools cannot execute without permission.
- Each step is observed before and after; unverified actions are never blindly replayed.
- Emergency stop checked before and after action dispatch.

## Important integration boundary
These are shared interfaces and an executable orchestration foundation, not completed native integrations. Existing Android accessibility/ML Kit and Windows UI Automation/Linux AT-SPI/Wayland/X11 adapters still need to be wired into these contracts. The desktop `Robot` controller remains limited under Wayland. Browser automation and real research providers must also be connected. Local/cloud model adapters should implement `PlanProvider` and continue to obey `HybridModelRouter` privacy/consent rules.

## Validation commands
- `./gradlew :core:test`
- `./gradlew :desktop:installDist`
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`

Do not mark an OS integration complete until tested on that OS with native permissions granted.
