# Scoreboard V2 (Race/Heat) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deliver a modular Scoreboard V2 for race/event heats using Megavex as primary provider, with safe fallback and no ActionBar redundancy.

**Architecture:** Keep race state logic separated from rendering and provider APIs. Build a V2 pipeline with orchestrator + state view-model builders + renderer + provider adapter. Roll out behind config flags with per-viewer fail-safe fallback.

**Tech Stack:** Java 21, Paper/Spigot API, Maven, Megavex Scoreboard Library, FastBoard (fallback), existing FormulaRacing managers.

---

### Task 1: Add V2 configuration contract

**Files:**
- Modify: `src/main/resources/config.yml`

**Step 1: Add config keys**

```yaml
scoreboard:
  max-rows: 30
  v2:
    enabled: false
    interval: 500ms
    canary-percentage: 0
    fallback-fastboard-enabled: true
```

**Step 2: Validate key naming consistency**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 3: Commit**

```bash
git add src/main/resources/config.yml
git commit -m "feat: add scoreboard v2 configuration flags"
```

### Task 2: Introduce provider-agnostic adapter interface

**Files:**
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/provider/ScoreboardAdapter.java`

**Step 1: Add interface**

```java
public interface ScoreboardAdapter {
    void create(Player player);
    void updateTitle(Player player, String title);
    void updateLines(Player player, List<String> lines);
    void delete(Player player);
    boolean isHealthy(Player player);
}
```

**Step 2: Compile check**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 3: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/provider/ScoreboardAdapter.java
git commit -m "feat: add scoreboard v2 provider interface"
```

### Task 3: Implement FastBoard fallback adapter

**Files:**
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/provider/FastBoardAdapter.java`

**Step 1: Implement adapter with internal map**

```java
private final Map<UUID, FastBoard> boards = new HashMap<>();
```

Methods should create/update/delete safely with null checks and no thrown exceptions.

**Step 2: Compile check**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 3: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/provider/FastBoardAdapter.java
git commit -m "feat: add fastboard fallback adapter for scoreboard v2"
```

### Task 4: Implement Megavex primary adapter

**Files:**
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/provider/MegavexAdapter.java`
- Modify: `pom.xml` (if Megavex dependencies are missing)

**Step 1: Add Megavex deps (if needed)**

```xml
<dependency>
  <groupId>net.megavex</groupId>
  <artifactId>scoreboard-library-api</artifactId>
  <version>2.4.4</version>
</dependency>
```

**Step 2: Implement Megavex sidebar lifecycle**

Create sidebar per player, update title/lines, and close on delete.

**Step 3: Compile check**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 4: Commit**

```bash
git add pom.xml src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/provider/MegavexAdapter.java
git commit -m "feat: add megavex provider adapter for scoreboard v2"
```

### Task 5: Define ViewModel contract and core model

**Files:**
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/model/ScoreboardViewModel.java`
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/model/ScoreboardContext.java`

**Step 1: Add immutable model**

```java
public record ScoreboardViewModel(String title, List<String> lines, boolean compact) {}
```

**Step 2: Add context model**

Include: `Heats heat`, `Player viewer`, `Driver driverOrNull`, `List<Driver> sortedDrivers`, `HeatState state`.

**Step 3: Compile check**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 4: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/model/*.java
git commit -m "feat: add scoreboard v2 view model contracts"
```

### Task 6: Add state builder interface and baseline builders

**Files:**
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/builder/StateViewModelBuilder.java`
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/builder/RacingViewModelBuilder.java`
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/builder/QualifyingViewModelBuilder.java`
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/builder/PracticeViewModelBuilder.java`
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/builder/FinishedViewModelBuilder.java`

**Step 1: Add builder interface**

```java
public interface StateViewModelBuilder {
    boolean supports(HeatState state);
    ScoreboardViewModel build(ScoreboardContext context);
}
```

**Step 2: Implement basic builders**

Each builder returns prioritized lines only, no ActionBar duplicates.

**Step 3: Compile check**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 4: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/builder/*.java
git commit -m "feat: add scoreboard v2 state view-model builders"
```

### Task 7: Add renderer and compact/window policy

**Files:**
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/render/ScoreboardRenderer.java`
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/render/LineBudgetPolicy.java`

**Step 1: Implement budget policy**

Rules:
- always preserve critical context lines
- window classification around driver for large grids
- switch to compact when row budget exceeded

**Step 2: Implement renderer**

Deterministic truncation and final line list generation from `ScoreboardViewModel`.

**Step 3: Compile check**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 4: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/render/*.java
git commit -m "feat: add scoreboard v2 renderer and line budget policy"
```

### Task 8: Build V2 orchestrator with fail-safe chain

**Files:**
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/RaceScoreboardV2Manager.java`

**Step 1: Add update loop**

Use configurable `scoreboard.v2.interval`, group players by heat, shared per-heat cache.

**Step 2: Add fallback chain**

Flow:
1) Megavex render
2) simplified render
3) FastBoard fallback (if enabled)

**Step 3: Add add/remove methods parity with current manager**

`addPlayer/removePlayer/removeHeat/addSpectator/removeSpectator/shutdown`.

**Step 4: Compile check**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 5: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/RaceScoreboardV2Manager.java
git commit -m "feat: add scoreboard v2 orchestrator with fail-safe fallback"
```

### Task 9: Wire V2 manager in plugin bootstrap

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/FormulaRacing.java`

**Step 1: Instantiate manager conditionally**

If `scoreboard.v2.enabled`, initialize `RaceScoreboardV2Manager`, else keep existing manager.

**Step 2: Ensure shutdown lifecycle**

Call proper `shutdown()` in `onDisable()` for whichever manager is active.

**Step 3: Compile check**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 4: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/FormulaRacing.java
git commit -m "feat: wire scoreboard v2 manager behind feature flag"
```

### Task 10: Route race entry/exit integrations to active manager

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Heat/Heats.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Controllers/RaceEventManager.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Controllers/DailyRaceManager.java`

**Step 1: Replace direct assumptions with active-manager call path**

Existing add/remove scoreboard events must work for V1 and V2.

**Step 2: Compile check**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 3: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Heat/Heats.java src/main/java/dev/EfraGroup/formulaRacing/Controllers/RaceEventManager.java src/main/java/dev/EfraGroup/formulaRacing/Controllers/DailyRaceManager.java
git commit -m "refactor: route race scoreboard events to active scoreboard manager"
```

### Task 11: Add observability hooks for V2

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/RaceScoreboardV2Manager.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/DebugManager.java` (if needed)

**Step 1: Add counters/timers in manager**

Track:
- `updatesOk`
- `updatesFailed`
- `fallbackActivated`
- average render time per cycle

**Step 2: Add concise contextual logs**

Include: heat id, state, viewer type.

**Step 3: Compile check**

Run: `mvn -DskipTests compile`
Expected: compile success

**Step 4: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/RaceScoreboardV2Manager.java src/main/java/dev/EfraGroup/formulaRacing/Utils/DebugManager.java
git commit -m "chore: add scoreboard v2 observability counters and logs"
```

### Task 12: Validation and rollout checklist

**Files:**
- Modify: `SCOREBOARD_V2_DESIGN.md`
- Create: `docs/plans/scoreboard-v2-rollout-checklist.md`

**Step 1: Add rollout checklist**

Include:
- canary activation steps
- rollback toggle steps
- manual QA scenarios by heat state
- load-test checklist

**Step 2: Final verification**

Run:
- `mvn -DskipTests compile`
- manual server test in at least 2 heat states

Expected: no compile errors, stable scoreboards, no spammy errors.

**Step 3: Commit**

```bash
git add SCOREBOARD_V2_DESIGN.md docs/plans/scoreboard-v2-rollout-checklist.md
git commit -m "docs: add scoreboard v2 rollout and validation checklist"
```

---

## Global Verification Gates

After each task:
- compile must pass (`mvn -DskipTests compile`)
- no new main-thread heavy operations in update loop
- no ActionBar duplication introduced in scoreboard lines

Before enabling V2 in production:
- canary first
- observe failure counters and render time
- confirm fallback path works by forced fault injection (dev/staging)
