# AGENTS.md — FormulaRacing

## Project Summary

Minecraft Paper/Folia plugin for ice boat racing (F1-style). Built with Maven, Java 21, targets Paper API 1.21.8.

## Folia Compatibility

✅ Fully compatible with Folia 1.21.8.

- `plugin.yml` has `folia-supported: true` and uses Paper API 1.21.8-R0.1-SNAPSHOT
- `SchedulerHelper` uses `GlobalRegionScheduler`, `RegionScheduler`, `AsyncScheduler`, `Entity.getScheduler()` — no direct `Bukkit.getScheduler()` calls
- NMS-based collisionless boat entities removed; replaced with Paper API (`world.spawnEntity` + `setCollidable` via reflection)
- `BukkitRunnable` fully replaced by `SchedulerHelper`/`ScheduledTask`
- `Bukkit.getWorlds()` and `Bukkit.getOnlinePlayers()` are used in non-hot paths (work on Folia 1.21.8 as snapshot returns)

**Hard dependency:** WorldEdit ≥ 7.3.0 (7.2.x does not support Folia).

## Build & Deploy

```bash
mvn package                      # Build shaded JAR + auto-upload to server + git auto-commit
mvn package -DskipAutoGitCommit  # Build only, skip deploy+commit
```

Output: `target/formularacing-${project.version}.jar`. The `package` phase bundles shade → SFTP upload → PowerShell git auto-commit in sequence. Do not run `mvn package` casually — it deploys to production.

No tests exist. There is no `test` directory and no test runner configured.

## Architecture

| Package | Purpose |
|---------|---------|
| `Command/` | ACF (Aikar Command Framework) command handlers |
| `Listener/` | Bukkit event listeners — **VehicleMoveEvent is high-frequency** |
| `Controllers/` | Game-logic managers (QuickRace, RaceEvent, Spectator, etc.) |
| `Heat/` | Race heat/session system; `Heat/Logic/` has RaceSession, QualifyingSession, PracticeSession, DRS/ERS/PTP |
| `Event/` | Organized race events (signups, schedule, countdown, results) |
| `Round/` | Round lifecycle (PracticeRound, QualificationRound) |
| `TimeTrial/` | Solo time-trial controller + session |
| `Duels/` | 1v1 time-trial duels |
| `Database/` | SQLite (default) or MySQL persistence |
| `Gui/` | Inventory-based menus; `Gui/Framework/` has BaseGui abstraction |
| `Collisionless/` | Paper API-based collision-less boat spawning (`setCollidable` via reflection) |
| `BoatUtils/` | OpenBoatUtils client mod integration |
| `Config/` | YAML config managers |
| `Cosmetics/` | Boat trail cosmetics |
| `Participant/` | Driver, Spectator, Subscriber domain models |
| `Utils/` | Utilities; `Utils/scoreboard/v2/` is the Megavex scoreboard subsystem |

Root files (`FormulaRacing.java`, `APIFormulaRacing.java`, `NMSHandler.java`, `RegionBox.java`, etc.) are core domain objects, not utilities.

## Critical Constraints

- **VehicleMoveEvent fires every tick for every moving boat.** Never do heavy work in movement-related listeners. Use `DebugManager` for logging — never `Bukkit.getLogger()` in hot paths.
- **In Folia, there is no single main thread.** Use `SchedulerHelper.runTaskFor(entity, ...)` for entity-bound work, `SchedulerHelper.runTaskAt(location, ...)` for location-bound work, and `SchedulerHelper.runTask(plugin, ...)` for global work. Never use `Bukkit.getScheduler()` or `BukkitRunnable`.
- **`Bukkit.getWorlds()` and `Bukkit.getOnlinePlayers()` work on Folia 1.21.8** as snapshot returns — safe for config/menu loading, avoid in hot paths.
- **TPS must stay at 20.** Profile before adding logic to listeners or schedulers.
- **Paper API 1.21.8.** Verify method availability before using Paper-only APIs — not all Paper methods exist in Spigot.

## Dependencies

- **Hard dependency:** WorldEdit ≥ 7.3.0 (track region selection; 7.2.x não suporta Folia)
- **Soft dependencies:** DecentHolograms, PlayerPoints, HeadDatabase, LuckPerms, Geyser/Floodgate (Bedrock support)
- **Shaded (relocated):** ACF → `dev.efragroup.libs.acf`, TaskChain → `dev.efragroup.libs.taskchain`
- **Scoreboard:** Megavex scoreboard-library v2.4.4 (the unified v2 system; `ScoreboardTimeTrialUtils` is legacy v1)

## i18n

Language files: `src/main/resources/lang/{pt_PT, en_US, pt_BR}..yml`. Use `TranslationUtil` for player-facing strings. Never hardcolor-code messages in Java — use the lang keys.

## Database

Config chooses SQLite (default, `formula_racing.db`) or MySQL. `DatabaseManager` is the single entry point; `EventsDatabaseManager` handles event-specific tables. Both are initialized in `FormulaRacing.onEnable()`.

## Debugging Protocol

1. **Hypothesis-first.** State the suspected cause before changing code.
2. **Surgical logging.** Use `DebugManager` flags (toggled via `config.yml` debug section or `/formularacingreload`). Never add more than 2–3 debug points at once.
3. **Dry-run.** Mentally simulate execution flow and check for null players, unloaded chunks, event priority conflicts.

## Git Conventions

Nearly all commits are auto-generated from the Maven deploy cycle (`auto: deploy formularacing-0.2`). Manual commits exist (`manual changes`) but are rare. The branch is `master`.

## Reference Implementation

When implementing or modifying features, always use **TimingSystem** source code as the reference base. Its code lives at `C:\Users\vitor\OneDrive\Documentos\Git Repos\TimingSystem`. Read the relevant TimingSystem classes first to understand patterns, conventions, and architecture before writing FormulaRacing code.

## Key Config Files

- `plugin.yml` — commands, permissions, hard/soft depends
- `config.yml` — runtime config (database, scoreboard, debug flags, daily race, podium)
- `pitstop_config.yml` — pit stop minigame configuration
- `src/main/resources/lang/*.yml` — localized messages