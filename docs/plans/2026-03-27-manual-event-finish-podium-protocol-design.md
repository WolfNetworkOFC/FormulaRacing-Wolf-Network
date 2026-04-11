# Manual Event-Finish Podium Protocol Design

## Understanding Summary

- Build an automatic podium protocol triggered only after manual `/event finish` succeeds.
- Scope is ceremony behavior, not event finish validation rules.
- Audience is only event participants: subscribers and event spectators.
- Ceremony reveals standings from Top N down to Top 1 (default Top 15).
- At Top 3 reveal, each winner is teleported to configured podium slots (P3, P2, P1).
- If Top 3 player is offline, reveal name only (no replacement, no teleport).
- At ceremony end, all ceremony participants are teleported to lobby.

## Explicit Non-Goals

- No change to `/event finish` validation and round-completion rules.
- No fallback to track spawn/current location when podium config is invalid.
- No global server-wide ceremony for non-event players.
- No change to event ranking model beyond reading FINAL round result.

## Assumptions

- `Events.finish()` remains the single trigger point for this protocol.
- FINAL round result list is available and ordered by official position.
- Typical ceremony load is 20-50 players.
- All Bukkit world/entity teleports occur on main thread.
- Config persistence remains file-based (`config.yml`) with plugin save/reload flow.

## Constraints and NFR

### Performance
- Ceremony logic runs with one scheduled repeating task per ceremony.
- No database or blocking IO in reveal ticks.

### Scale
- Support 20-50 participants smoothly with default settings.

### Security / Access
- In-game podium config commands require admin permission.

### Reliability
- Fail-fast when any required location is invalid.
- Ceremony failure must not revert an already finalized event.

### Maintenance
- All ceremony behavior configurable (timing, locations, effects, messages).
- No hardcoded coordinates in Java code.

## Current State Snapshot

- `Events.finish()` already calls `podiumManager.startCeremony(this, results)` for FINAL rounds.
- Existing `PodiumManager` currently:
  - uses hardcoded locations,
  - teleports all online players,
  - reveals from `min(results.size(), 15)` to 1,
  - teleports Top 3 when online,
  - returns no one to lobby,
  - gives/removes snowballs globally.

## Proposed Design

### 1) Podium Ceremony State Machine

`PodiumManager` uses explicit execution states:

1. `IDLE`
2. `VALIDATING`
3. `AUDIENCE_TELEPORT`
4. `REVEALING`
5. `FINISHING`
6. back to `IDLE`

Only one active ceremony is allowed at a time.

### 2) Ceremony Session Snapshot

Create a per-run `PodiumCeremonySession` object with:

- `eventId`
- immutable `resultsSnapshot`
- immutable `ceremonyParticipantsSnapshot` (subscribers + event spectators)
- `topLimitUsed`
- `currentRevealPosition`
- scheduler task id/reference
- start timestamp

Snapshot prevents mid-ceremony changes from altering order or target set.

### 3) Participant Selection

Participants include:

- `event.getSubscribers().keySet()`
- `plugin.getSpectatorManager().getSpectatorsInEvent(event.getId())`

Unique union by UUID.

No filtering by other gameplay contexts; force-teleport to audience is intentional.

### 4) Ranking Source and Reveal Order

- Source: FINAL round result list passed by `Events.finish()`.
- Use `topLimit = min(config.top-limit, results.size())`.
- Reveal positions in descending order: `topLimit -> 1`.
- Default timing: `reveal-interval-ticks = 50` (2.5 seconds).

### 5) Top 3 Teleport Logic

On reveal position:

- P3 -> teleport to `locations.p3`
- P2 -> teleport to `locations.p2`
- P1 -> teleport to `locations.p1`

If online player exists, teleport and apply optional effects.
If offline, reveal and log only.

### 6) Start/End Teleport Contracts

Start:

- Teleport ceremony participants to `locations.audience`.
- Give snowball if enabled.

End:

- Remove snowball from ceremony participants online.
- Teleport ceremony participants to `locations.lobby`.
- Clear session/task and unlock manager.

### 7) Configuration Model (`config.yml`)

```yml
podium:
  enabled: true
  top-limit: 15
  reveal-interval-ticks: 50
  final-delay-ticks: 60
  snowball:
    enabled: true
  effects:
    sounds: true
    particles: true
  messages:
    reveal-title: "&b#{pos}"
    reveal-subtitle: "&f{player}"
    reveal-chat: "&7#{pos} &e{player}"
    offline-top3-chat: "&7#{pos} &e{player} &8(offline)"
    start-chat: "&6Cerimonia do podio iniciada!"
    end-chat: "&eCerimonia encerrada."
  locations:
    audience: { world: "world", x: 0.0, y: 80.0, z: 0.0, yaw: 0.0, pitch: 0.0 }
    p1:       { world: "world", x: 0.0, y: 82.0, z: -2.0, yaw: 180.0, pitch: 0.0 }
    p2:       { world: "world", x: -2.0, y: 81.0, z: -2.0, yaw: 180.0, pitch: 0.0 }
    p3:       { world: "world", x: 2.0, y: 80.0, z: -2.0, yaw: 180.0, pitch: 0.0 }
    lobby:    { world: "world", x: 0.0, y: 70.0, z: 0.0, yaw: 0.0, pitch: 0.0 }
```

### 8) In-Game Configuration Commands

Add admin command set:

- `/podium set audience`
- `/podium set p1`
- `/podium set p2`
- `/podium set p3`
- `/podium set lobby`
- `/podium show`
- `/podium reload`
- `/podium test <event>` (optional but recommended)

Behavior:

- `set`: uses sender location, validates world, writes into `config.yml`, saves immediately.
- `show`: prints all current values and validity status.
- `reload`: reloads podium block from disk.

### 9) Failure Handling

- If manager is already active, reject new start and log reason.
- If required location invalid/missing world, abort before first teleport.
- If no valid results, abort and log.
- If player disconnects mid-ceremony, skip safely.
- If scheduler task is cancelled unexpectedly, force cleanup to `IDLE`.

### 10) Compatibility Notes

- Keep existing trigger contract (`Events.finish()` call site).
- Preserve current default behavior intent (Top 15 descending + Top 3 teleport), but scope to event participants and config-driven locations.

## Test Strategy

1. Happy path with 15+ final results and online Top 3.
2. Top 3 offline cases (one or multiple).
3. Invalid location config (missing world) -> fail-fast abort.
4. Ceremony with 30-40 participants.
5. Commands set/show/reload persistence after restart.
6. Event finish still succeeds when ceremony disabled or aborted.

## Multi-Agent Brainstorming Review (Required Handoff)

### Skeptic / Challenger Findings
- Risk: global mutable state may lock ceremony forever after task exceptions.
- Risk: participant context force-teleport can disrupt unrelated gameplay sessions.
- Resolution: enforce `finally` cleanup path and explicit product decision to force-teleport regardless of context.

### Constraint Guardian Findings
- Risk: per-tick expensive calls or repeated lookups can cause lag spikes.
- Risk: invalid location fallback could cause unsafe teleports.
- Resolution: snapshot once, single scheduler task, no fallback policy, fail-fast validation.

### User Advocate Findings
- Risk: reveal cadence too fast for readability.
- Risk: unclear ceremony end state.
- Resolution: default interval changed to 2.5s; explicit end teleport to lobby and end message.

### Arbiter Disposition
- **APPROVED**
- Rationale: objections were resolved without expanding scope; design stays aligned with manual event-finish objective and operational constraints.

## Decision Log

1. **Trigger scope**: only manual `/event finish` successful flow.
   - Alternatives: include automatic finish, force-finish variants.
   - Why: maintain operator control and avoid unexpected ceremonies.

2. **Audience scope**: subscribers + event spectators only.
   - Alternatives: all online players, top3+staff only.
   - Why: ceremony remains event-scoped and less disruptive.

3. **Reveal order**: Top N down to Top 1.
   - Alternatives: Top 3 only, ascending reveal.
   - Why: explicit user requirement and ceremonial progression.

4. **Default top limit**: 15.
   - Alternatives: 10 or full standings.
   - Why: balanced ceremony duration and information depth.

5. **Default reveal interval**: 2.5s (50 ticks).
   - Alternatives: 1.5s, 2.0s, 3.0s.
   - Why: better readability and ceremonial feel.

6. **Top 3 offline behavior**: reveal name, no teleport, no replacement.
   - Alternatives: promote next finisher, cancel ceremony.
   - Why: preserve official standings integrity.

7. **Location source**: global `config.yml` plus in-game set commands.
   - Alternatives: hardcode, per-track DB only.
   - Why: operationally simple and editable live.

8. **Failure policy**: invalid config aborts ceremony immediately.
   - Alternatives: fallback to track spawn/current location.
   - Why: safety and predictable behavior.

9. **Snowball**: keep enabled by default and configurable.
   - Alternatives: remove feature entirely.
   - Why: preserve desired celebratory behavior.

10. **End location**: all ceremony participants return to lobby.
    - Alternatives: remain in place, send to track spawn.
    - Why: explicit user requirement and clean event closure.
