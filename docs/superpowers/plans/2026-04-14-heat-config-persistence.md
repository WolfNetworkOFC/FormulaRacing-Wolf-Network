# Heat Config Persistence Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix heat configuration persistence so that DRS, Push-to-Pass, ghosting delta, reverse grid, realistic mode, driver swap, DRS downtime/power, and P2P power settings survive server restarts.

**Architecture:** Add a `configDirty` flag to `Heats` that tracks unsaved changes. When `loadHeat()` or `startCountdown()` is called, if `configDirty` is true, persist all settings via `updateHeatFullConfig()` in a single DB write. Each setter marks `configDirty = true` when called.

**Tech Stack:** Java 21, Paper API 1.21.8, SQLite/MySQL via EventsDatabaseManager

---

## File Structure

| File | Responsibility |
|------|---------------|
| `src/main/java/dev/EfraGroup/formulaRacing/Heat/Heats.java` | Add `configDirty` flag, `saveConfigIfDirty()`, call in loadHeat/startCountdown |
| `src/main/java/dev/EfraGroup/formulaRacing/Command/HeatCommand.java` | No changes needed — setters already mark dirty |

---

## Tasks

### Task 1: Add `configDirty` field and `saveConfigIfDirty()` method to Heats

**Modify:** `src/main/java/dev/EfraGroup/formulaRacing/Heat/Heats.java`

- Add field `private boolean configDirty = false;` after line ~71
- Add field `private boolean configInitialized = false;` — tracks whether this heat has been saved at least once (needed because new heats don't have DB id > 0 until created)
- Add method `public void markConfigDirty() { this.configDirty = true; }`
- Add method `public void saveConfigIfDirty()` that:
  - Only saves if `configDirty == true` and `this.id > 0` and `this.plugin != null`
  - Calls `updateHeatFullConfig()` with all current values
  - Sets `configDirty = false` after saving
- Add method `private boolean shouldSaveConfig() { return this.id > 0 && this.plugin != null && this.plugin.getRaceEventManager() != null; }`

**Code to add after line 71:**
```java
private boolean configDirty = false;
private boolean configInitialized = false;
```

**Code to add after `setRealistic()` (~line 128):**
```java
public void markConfigDirty() {
    this.configDirty = true;
}

public void saveConfigIfDirty() {
    if (!this.configDirty || !this.shouldSaveConfig()) {
        return;
    }
    this.plugin.getRaceEventManager()
        .getDatabaseManager()
        .updateHeatFullConfig(
            this.id,
            this.totalLaps,
            this.totalPits,
            this.timeLimit,
            this.startDelay,
            this.maxDrivers,
            this.lonely,
            this.canReset,
            true,
            this.collisionMode,
            this.drsEnabled,
            this.driverswap,
            this.drsdowntime,
            this.drsdownpower,
            this.reversegrid,
            this.deltaghosting,
            this.pushtopass,
            this.pushtopasspower,
            this.realistc
        );
    this.configDirty = false;
}

private boolean shouldSaveConfig() {
    return this.id > 0 
        && this.plugin != null 
        && this.plugin.getRaceEventManager() != null;
}
```

---

### Task 2: Call `saveConfigIfDirty()` in existing save points

**Modify:** `src/main/java/dev/EfraGroup/formulaRacing/Heat/Heats.java`

1. **In `loadHeat()`** — after line 459 (`setHeatState(HeatState.LOADED)`), add:
```java
this.saveConfigIfDirty();
```

2. **In `startCountdown()`** — after line 536 (`setHeatState(HeatState.STARTING)`), but **before** the existing `updateHeatFullConfig()` call at line 515. Actually, the existing call is already there, so we need to refactor: the existing `updateHeatFullConfig()` call is redundant once `saveConfigIfDirty()` works. Remove the existing call at lines 508-536 and let `saveConfigIfDirty()` handle it.

**OR simpler approach:** Keep existing `updateHeatFullConfig()` in `startCountdown()` as-is, and just add `saveConfigIfDirty()` call in `loadHeat()`. The `startCountdown()` path already saves, so the dirty flag ensures `loadHeat()` also saves any pending changes before transitioning to LOADED.

**Decision:** Add `saveConfigIfDirty()` call in `loadHeat()` only. The `startCountdown()` path already calls `updateHeatFullConfig()` directly, which will continue to work. The key fix is that `loadHeat()` now also triggers a save.

---

### Task 3: Mark `configDirty = true` in all setters that lack persistence

**Modify:** `src/main/java/dev/EfraGroup/formulaRacing/Heat/Heats.java`

Add `this.markConfigDirty();` call in each of these setters (after the field assignment):

| Setter | Line |
|--------|------|
| `setDrsEnabled()` | ~183 |
| `setPushtopass()` | ~171 |
| `setDeltaghosting()` | ~179 |
| `setreversegrid()` | ~131 |
| `setrealistc()` | ~128 |
| `setDriverSwap()` | ~159 |
| `setDrsdowntime()` | ~147 |
| `setDrsdownpower()` | ~151 |
| `setpushtopasspower()` | ~135 |

**Example change for `setDrsEnabled()`:**
```java
public void setDrsEnabled(boolean drsEnabled) {
    this.drsEnabled = drsEnabled;
    this.markConfigDirty();
}
```

Apply same pattern to all 9 setters listed above.

---

### Task 4: Set `configInitialized = true` after first DB save in `startCountdown()`

**Modify:** `src/main/java/dev/EfraGroup/formulaRacing/Heat/Heats.java`

In `startCountdown()`, after the existing `updateHeatFullConfig()` call (line 535), add:
```java
this.configDirty = false;
this.configInitialized = true;
```

This prevents double-saving if `loadHeat()` is called after `startCountdown()` already saved.

---

### Task 5: Verify build compiles

**Run:** `mvn compile -DskipTests`

Expected: BUILD SUCCESS

---

### Task 6: Update test procedure documentation

**Modify:** `docs/superpowers/test-procedures.md`

Update Test 2 procedure to clarify the fix:
- Add note that configs are now saved when `loadHeat()` or `startCountdown()` is called
- The test still passes as documented since it uses `/heat load` before restart

---

## Verification

After implementation:
1. Create heat
2. Configure all settings via commands
3. Run `/heat load <heat>` — configs are now persisted
4. Restart server
5. Check `/heat info <heat>` — all values should persist

---

## Files to Modify

| File | Lines | Change |
|------|-------|--------|
| `Heats.java` | 65-72 | Add `configDirty` and `configInitialized` fields |
| `Heats.java` | ~128 | Add `setRealistc()` dirty flag |
| `Heats.java` | ~131 | Add `setReversegrid()` dirty flag |
| `Heats.java` | ~135 | Add `setPushtopasspower()` dirty flag |
| `Heats.java` | ~147 | Add `setDrsdowntime()` dirty flag |
| `Heats.java` | ~151 | Add `setDrsdownpower()` dirty flag |
| `Heats.java` | ~159 | Add `setDriverSwap()` dirty flag |
| `Heats.java` | ~171 | Add `setPushtopass()` dirty flag |
| `Heats.java` | ~179 | Add `setDeltaghosting()` dirty flag |
| `Heats.java` | ~183 | Add `setDrsEnabled()` dirty flag |
| `Heats.java` | ~127 | Add `markConfigDirty()`, `saveConfigIfDirty()`, `shouldSaveConfig()` methods |
| `Heats.java` | ~459 | Call `saveConfigIfDirty()` in `loadHeat()` |
| `Heats.java` | ~535 | Set `configDirty = false` and `configInitialized = true` after save in `startCountdown()` |
