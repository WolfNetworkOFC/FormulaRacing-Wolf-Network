# Scoreboard V2 Rollout Checklist

## Pre-Flight
- Set `scoreboard.v2.enabled: true` in non-production first.
- Set `scoreboard.v2.interval: 500ms` as baseline.
- Set `scoreboard.v2.canary-percentage: 10` for initial canary.
- Keep `scoreboard.v2.fallback-fastboard-enabled: true`.

## Functional QA
- Validate Practice scoreboard:
  - classification visible
  - best lap shown only here
- Validate Qualifying scoreboard:
  - timer line
  - classification and gaps to ahead
- Validate Racing scoreboard:
  - no pit-status duplication from action bar
  - centered classification with leader pin and ellipsis
- Validate Finished scoreboard:
  - top finishers and total time format

## Spectator QA
- Confirm spectator classification visibility.
- Confirm gap policy = ahead driver.
- Confirm no crash on empty grid.

## Reliability QA
- Force render error (temporary null/invalid input in dev) and confirm:
  - simplified fallback lines are shown
  - race loop continues
- Confirm adapter fallback activates when primary is unhealthy.

## Performance QA
- Run 1-2 heats with high activity and watch logs:
  - `[ScoreboardV2] ok=... fail=... fallback=... avgRenderMicros=...`
- Verify no noticeable TPS degradation.

## Canary Progression
- 10% -> 25% -> 50% -> 100% only if fail/fallback rates are stable.
- If failure spikes, rollback by:
  - setting `scoreboard.v2.enabled: false`
  - reloading/restarting plugin.

## Production Cutover
- Set `scoreboard.v2.enabled: true`.
- Set `scoreboard.v2.canary-percentage: 100`.
- Keep fallback enabled for first stable window.
