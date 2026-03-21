# FormulaRacing Scoreboard V2 Design

## Status
- Disposition: APPROVED
- Scope: Race/Event Heat scoreboard redesign (Phase 1)
- Out of scope: TimeTrial, Duels (future phase)

## Understanding Summary
- Build a new Scoreboard V2 for FormulaRacing inspired by TimingSystem, but with FormulaRacing identity.
- Primary goal is a mix of race usability and visual/design quality.
- Priority audience is drivers first.
- Performance target is smooth updates without harming TPS.
- Scale target is medium: 1-2 concurrent events, up to about 40 drivers (plus spectators).
- Reliability target is fail-safe behavior with graceful fallback.
- Architecture target is higher modularity, even if it requires larger refactor effort.

## Assumptions
- No special privacy/security restrictions for scoreboard-visible race data.
- Pit status for the current driver is shown only in Action Bar, not in scoreboard.
- Driver best lap is shown only in Practice/Qualifying scoreboard states.
- Spectator scoreboard emphasizes classification + gap to driver ahead.
- Scoreboard update interval is configurable in config.
- V2 provider starts with Megavex; FastBoard remains emergency fallback.

## Explicit Non-Goals (Phase 1)
- No implementation changes for TimeTrial scoreboard.
- No implementation changes for Duels scoreboard.
- No attempt to bypass hard client-side sidebar constraints.

## Final Architecture

### 1) Orchestrator Layer
- Responsibility: schedule updates, group viewers by heat, throttle cycles, isolate failures.
- Inputs: active heats, active viewers, heat state snapshots.
- Outputs: render jobs per viewer.
- Guarantees:
  - One failing viewer/heat does not break global update loop.
  - No heavy I/O in hot update path.

### 2) ViewModel Builders (State-Based)
- Responsibility: transform race state into view-ready model per state:
  - Setup/Loaded/Starting
  - Practice
  - Qualifying
  - Racing
  - Finished
- Rules:
  - Business/race rules stay here, not in renderer.
  - Shared fields normalized (position, lap context, classification window, status flags).

### 3) Renderer + Theme Layer
- Responsibility: convert ViewModel to final title + lines.
- Policy:
  - FormulaRacing visual identity.
  - Information hierarchy by state.
  - Deterministic truncation and line-budget management.
  - Compact mode when line budget is exceeded.

### 4) Provider Adapter Layer
- Contract: create/update/delete/health-check scoreboard instance.
- Primary implementation: Megavex adapter.
- Fallback implementation: FastBoard adapter (emergency path only).
- Result: provider swap is isolated from race/state logic.

## Information Architecture (No Action Bar Redundancy)

### Driver Scoreboard V2
- Keep on scoreboard:
  - Session context (heat/event short context, state, timer when relevant).
  - Classification around driver with dynamic window.
  - Relative competition context (gaps and nearby rivals).
- Do not duplicate from Action Bar:
  - Current driver pit status.
  - Real-time tactical HUD details already present in Action Bar.
- Show "best lap" only in Practice/Qualifying.

### Spectator Scoreboard V2
- Focus: classification and race progression readability.
- Gap policy: show gap to driver immediately ahead.
- No pilot-target camera dependency in Phase 1 layout logic.

## Performance, Scale, and Reliability Requirements

### Performance
- Configurable update cadence (`scoreboard.v2.interval`).
- Per-cycle cache for shared heat computations.
- Avoid repeated formatting work when source values are unchanged.

### Scale
- Design target: 1-2 concurrent heats, ~40 drivers total.
- Degradation strategy for larger grids:
  - Dynamic classification window.
  - Compact mode.
  - Priority lines over decorative lines.

### Reliability
- Fail-safe path:
  1. Render normal layout.
  2. On failure, degrade to simplified layout.
  3. If needed, fallback provider path.
- Error isolation at viewer/heat granularity.

### Security/Privacy
- No additional restrictions required in this phase.
- Keep existing permission and visibility model unchanged unless explicitly requested later.

### Maintainability/Ownership
- Clear boundaries (orchestrator/builders/renderer/provider).
- State behavior and layout policy become independently testable.
- Future migration for TimeTrial/Duels can reuse same pipeline.

## Rollout Plan (Design-Level)
- Feature flag: `scoreboard.v2.enabled`.
- Configurable interval: `scoreboard.v2.interval`.
- Optional canary: percentage-based or targeted activation.
- Controlled fallback path always available.
- No hard cutover before stability metrics pass.

## Observability Requirements
- Metrics/counters per cycle:
  - updates_ok
  - updates_failed
  - fallback_activated
  - average_render_time_by_heat
- Logs must include context:
  - heatId
  - viewer type (driver/spectator)
  - heat state
  - short exception reason

## Validation Strategy (Pre-Implementation Acceptance)
- Functional validation by state: Setup/Practice/Qualifying/Racing/Finished.
- Grid-size validation: small and large classification windows.
- Redundancy validation: no duplicated Action Bar tactical data.
- Failure-path validation: simplified layout and provider fallback.
- Load validation against target scale.

## Decision Log
1. Focus V1 on usability + visual quality.
   - Alternatives: visual-only, parity-only, performance-only.
   - Why: product value requires both utility and design improvement.

2. Prioritize drivers first.
   - Alternatives: spectators first, balanced first.
   - Why: direct race gameplay impact is highest for drivers.

3. Similar to TimingSystem but keep FormulaRacing identity.
   - Alternatives: near-clone, inspiration-only.
   - Why: leverage proven UX without losing project identity.

4. Performance objective is smooth without TPS harm.
   - Alternatives: maximum refresh, conservative refresh.
   - Why: race stability is mandatory.

5. Scale target set to medium profile.
   - Alternatives: low/high profile.
   - Why: matches expected realistic operation.

6. Fail-safe fallback behavior is mandatory.
   - Alternatives: disable on error, aggressive retry only.
   - Why: avoid race disruption.

7. Architecture should be modular-first.
   - Alternatives: minimal change.
   - Why: maintainability and future extension.

8. Approach selected: hybrid rollout with modular target.
   - Alternatives: pure full refactor now, pure incremental patching.
   - Why: reduces production risk while moving to clean architecture.

9. Provider decision: V2 starts on Megavex.
   - Alternatives: keep FastBoard initially.
   - Why: test intended provider early and avoid migration rework.

10. Driver pit status remains Action Bar only.
    - Alternatives: scoreboard only, both.
    - Why: remove redundancy.

11. Driver best lap shown only in Practice/Qualifying.
    - Alternatives: always/never.
    - Why: relevant in session types where it informs decisions.

12. Spectator priority is classification + gaps.
    - Alternatives: target-driver view, minimal-only.
    - Why: best race comprehension for spectators.

13. Spectator gap metric is gap to driver ahead.
    - Alternatives: gap to leader, both.
    - Why: better local battle readability.

14. Update interval must be configurable.
    - Alternatives: fixed interval in code.
    - Why: operational tuning without rebuild.

15. Phase 1 scope excludes TimeTrial and Duels.
    - Alternatives: include all modes immediately.
    - Why: reduce complexity/risk for first rollout.

## Multi-Agent Brainstorming Handoff
- Skill invoked: `multi-agent-brainstorming`.
- Skeptic review outcome:
  - Risk flagged: duplicated HUD channels and visual noise.
  - Resolution: strict Action Bar vs Scoreboard responsibility split.
- Constraint guardian review outcome:
  - Risk flagged: update loop overhead and broad blast radius.
  - Resolution: per-heat caching, error isolation, configurable interval, fallback chain.
- User advocate review outcome:
  - Risk flagged: low readability for spectators in dense grids.
  - Resolution: dynamic window + ahead-gap policy + compact mode.
- Arbiter decision: APPROVED.
- Rationale: understanding locked, constraints explicit, risks acknowledged, decisions logged.

## Risks Acknowledged
- Provider migration complexity (Megavex lifecycle differences).
- Visual regressions during parallel rollout.
- Edge-case state transitions between heat phases.
- Operational tuning needed for interval and compact thresholds.

## Next Step Gate
- This document finalizes design only.
- Implementation should start only via explicit implementation plan and phased rollout tasks.
