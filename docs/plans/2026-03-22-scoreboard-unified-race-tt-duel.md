# FormulaRacing Unified Scoreboard Design (Race + TT + Duel)

## Status
- Disposition: APPROVED
- Scope: unify scoreboard ownership and rendering for Race, Time Trial, and Duel
- Backend decision: MegaVex only (remove FastBoard usage)

## Understanding Summary
- Build one scoreboard ownership pipeline to prevent conflicts between Race, Duel, and TT updates.
- Keep current visual content per mode; this is an architecture/stability change, not a UI redesign.
- Prioritize deterministic behavior when multiple contexts are active for the same player.
- Priority rule is fixed: Race > Duel > TT.
- Target users are players in race sessions, duel sessions, and solo time trials.
- Main goal is production stability: no double sidebar, no random takeover, minimal flicker.
- Reference architecture is TimingSystem's single-backend ownership model.

## Assumptions
- No new privacy/security constraints are introduced by this change.
- Existing translations and line formatting can remain as-is for this phase.
- ActionBar behavior is unchanged and remains out of scope.
- Existing event/session lifecycle hooks are enough to drive show/hide transitions.
- Main-thread-safe render apply path remains mandatory for Bukkit/Paper compatibility.

## Explicit Non-Goals
- No redesign of scoreboard style/content hierarchy.
- No new scoreboard features (compact mode, animations, new widgets).
- No ActionBar refactor.

## Final Architecture

### 1) Unified Orchestrator (Single Writer)
- Introduce `UnifiedScoreboardService` as the only class allowed to create/update/delete sidebar instances.
- All modules (Race, Duel, TT) must call intent APIs (`requestShow`, `requestHide`, `refreshContext`) instead of writing directly.
- Keep per-player state: active mode, available mode contexts, render hash, health flags.

### 2) Mode Providers (Read/Build Only)
- Implement provider contracts:
  - `RaceScoreboardProvider`
  - `DuelScoreboardProvider`
  - `TimeTrialScoreboardProvider`
- Providers return a `ScoreboardViewModel` (title + lines) and never touch scoreboard backend APIs.

### 3) Deterministic Ownership Policy
- Resolve active mode by strict priority: Race > Duel > TT.
- On `requestHide(activeMode)`, automatically promote next eligible mode.
- Ignore duplicate `requestShow` calls when effective output is unchanged.

### 4) Backend Layer (MegaVex Only)
- Standardize rendering through MegaVex adapter path already used by V2.
- Remove FastBoard writes from TT/Duel pipelines.
- Keep local fallback rendering in MegaVex path for provider errors (simplified lines), not backend switching.

## Data Flow
1. Domain event occurs (heat join, duel start, TT start, finish/reset/disconnect).
2. Domain module posts intent to `UnifiedScoreboardService`.
3. Service updates `PlayerScoreboardState`, resolves active mode by priority.
4. Service asks active provider for `ScoreboardViewModel`.
5. Service applies diff/hash check and renders via MegaVex adapter.
6. On hide/stop, service transitions ownership and re-renders if needed.

## Error Handling and Reliability
- Provider failure is isolated per player; one failure cannot break global update loop.
- If provider throws, render minimal fallback for that player and emit concise diagnostic log.
- If player is offline or invalid, state is cleaned and sidebar is closed.
- Render updates are idempotent to reduce flicker/churn.

## Non-Functional Requirements

### Performance
- Stable update cadence tuned for production behavior (current operational profile).
- Re-render only on effective changes (title/lines hash compare).
- No heavy DB or formatting loops inside repeated hot update paths without caching.

### Scale
- Supports concurrent activity across Race, Duel, and TT without ownership conflicts.
- Per-player ownership state prevents cross-mode overwrite storms.

### Security and Privacy
- No new sensitive data introduced.
- Existing visibility/permission model remains unchanged.

### Availability and Reliability
- Degrade gracefully per player on provider failure.
- Deterministic takeover rules avoid undefined UI state.

### Maintenance and Ownership
- Clear boundaries: domain providers vs orchestration vs backend adapter.
- Future scoreboard changes occur in provider logic without backend rewrites.

## Migration Strategy (Direct Cutover)
- Replace direct scoreboard writes in TT/Duel with orchestrator intents.
- Keep Race V2 content logic, but route ownership through unified service.
- Remove FastBoard-backed TT/Duel write paths.
- Validate transitions in one integrated cutover, no mixed ownership runtime.

## Validation Strategy
- Priority tests: Race dominates Duel/TT, Duel dominates TT.
- Transition tests: TT -> Duel, Duel -> Race, Race finish -> TT resume.
- Lifecycle tests: late join, disconnect/reconnect, forced reset, duel timeout.
- Stability tests: no double sidebar, no rapid flicker under frequent updates.
- Regression checks: existing displayed content per mode remains functionally equivalent.

## Decision Log
1. Decision: unify all three flows (Race + TT + Duel).
   - Alternatives: race-only, race+tt, arbitration-only.
   - Why: conflict source spans all three writers.

2. Decision: fixed priority Race > Duel > TT.
   - Alternatives: Duel-first, last-write-wins, config-driven priority.
   - Why: race session is top gameplay context and must be deterministic.

3. Decision: production-stable NFR profile.
   - Alternatives: max-performance-first, observability-heavy.
   - Why: operational stability is the primary pain point.

4. Decision: direct cutover (no phased feature-flag rollout).
   - Alternatives: phased rollout with flag.
   - Why: explicit operator preference for immediate consolidation.

5. Decision: no redesign/no new features/no ActionBar refactor.
   - Alternatives: include visual refresh and HUD changes now.
   - Why: YAGNI and reduced migration risk.

6. Decision: MegaVex-only backend; remove FastBoard for TT/Duel.
   - Alternatives: keep dual backend.
   - Why: single backend model (TimingSystem-aligned) reduces conflict and maintenance overhead.

7. Decision: single-writer orchestrator architecture.
   - Alternatives: keep independent writers with arbitration hooks.
   - Why: strongest guarantee against concurrent ownership conflicts.

## Multi-Agent Brainstorming Handoff
- Skill invoked: `multi-agent-brainstorming`.
- Skeptic/Challenger findings:
  - Risk: hidden race conditions between frequent TT/Duel updates and race state changes.
  - Resolution: single writer + deterministic priority + idempotent updates.
- Constraint Guardian findings:
  - Risk: render churn and tick overhead if every request forces full redraw.
  - Resolution: hash/diff rendering, scoped failure isolation, stable cadence.
- User Advocate findings:
  - Risk: confusing scoreboard switching if ownership changes are not predictable.
  - Resolution: strict documented priority and automatic promotion on hide.
- Integrator/Arbiter decision: APPROVED.
- Rationale: understanding lock complete, objections resolved, constraints explicit, decision log complete.

## Risks Acknowledged
- Direct cutover has higher short-term regression risk than phased rollout.
- Existing TT/Duel classes require careful decoupling from direct backend calls.
- Transition hooks must be audited to avoid stale mode state after abnormal session ends.

## Implementation Gate
- Design is finalized and approved.
- Next step is implementation planning and execution in controlled commits.
