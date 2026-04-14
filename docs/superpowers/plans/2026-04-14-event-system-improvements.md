# Event System Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve FormulaRacing's event/round/heat system to match or exceed TimingSystem quality, fixing critical data-loss bugs, adding state machines, and closing feature gaps.

**Architecture:** Incremental fixes to the existing codebase, preserving package structure (`dev.EfraGroup.formulaRacing`). Each task is self-contained and produces working, testable software. No restructuring or refactoring outside the scope of each task.

**Tech Stack:** Java 21, Paper API 1.21.8, SQLite (default), ACF commands, Maven

---

## File Structure (files to create or modify)

```
src/main/java/dev/EfraGroup/formulaRacing/
├── Database/
│   ├── EventsDatabaseManager.java          # MODIFY: add subscriber/heat-config persistence, update load methods
│   └── DatabaseManager.java               # MODIFY: add new CREATE TABLE for fr_event_signups
├── Event/
│   ├── EventStateMachine.java             # CREATE: state transition validation for Events
│   └── Events.java                        # MODIFY: use EventStateMachine in setState
├── Round/
│   ├── RoundStateMachine.java             # CREATE: state transition validation for Rounds
│   ├── Rounds.java                        # MODIFY: use RoundStateMachine in setRoundState
│   └── PracticeRound.java                # MODIFY: implement broadcastResults()
├── Heat/
│   └── HeatStateMachine.java             # MODIFY: add missing transitions for QUALIFYING
├── Controllers/
│   └── QualificationManager.java         # MODIFY: use proper addDriver flow in applyGridToFinalRound
└── Participant/
    └── Subscriber.java                    # MODIFY: add type field (SUBSCRIBER/RESERVE) for DB persistence
```

---

### Task 1: Fix HeatStateMachine — Add missing QUALIFYING transitions

**Priority:** CRITICAL — IllegalStateException crash at runtime when qualifying starts

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Heat/HeatStateMachine.java`

The current state machine blocks `SETUP→QUALIFYING`, `IDLE→QUALIFYING`, and `STARTING→QUALIFYING`, but `QualifyingSession.start()` (line 31) attempts these transitions. This causes `IllegalStateException`.

**Analysis of actual code paths:**

1. **PracticeRound.startHeat()** → calls `heat.startPractice()` → sets `PRACTICE` state (current state machine allows `SETUP→PRACTICE`, `IDLE→PRACTICE` — OK)
2. **QualificationRound.startHeat()** → calls `heat.loadHeat()` then `heat.startCountdown()` → transitions `SETUP→LOADED→STARTING` (OK)
3. **QualifyingSession.start()** (line 31) → sets `QUALIFYING` from states `{LOADED, IDLE, SETUP, STARTING}` — ONLY `LOADED→QUALIFYING` is missing from the state machine
4. After sprint race reset and re-qualify: `FINISHED→SETUP→...→QUALIFYING` — since `FINISHED→SETUP` exists, re-entering quali goes `FINISHED→SETUP→QUALIFYING` which also needs the `SETUP→QUALIFYING` transition

- [ ] **Step 1: Update TRANSITIONS map in HeatStateMachine**

Replace the entire `TRANSITIONS` map to add the missing transitions:

```java
private static final Map<HeatState, Set<HeatState>> TRANSITIONS = Map.ofEntries(
    Map.entry(HeatState.IDLE, Set.of(HeatState.SETUP, HeatState.PRACTICE)),
    Map.entry(HeatState.SETUP, Set.of(HeatState.LOADED, HeatState.PRACTICE, HeatState.QUALIFYING)),
    Map.entry(HeatState.PRACTICE, Set.of(HeatState.LOADED, HeatState.FINISHED)),
    Map.entry(HeatState.LOADED, Set.of(HeatState.STARTING, HeatState.SETUP, HeatState.QUALIFYING)),
    Map.entry(HeatState.STARTING, Set.of(HeatState.RACING, HeatState.LOADED, HeatState.QUALIFYING)),
    Map.entry(HeatState.QUALIFYING, Set.of(HeatState.FINISHED)),
    Map.entry(HeatState.RACING, Set.of(HeatState.FINISHED)),
    Map.entry(HeatState.FINISHED, Set.of(HeatState.SETUP))
);
```

**Why `Map.ofEntries` instead of `Map.of`:** `Map.of` only supports up to 10 entries and doesn't work well with Set values. `Map.ofEntries` is cleaner for 8+ entries.

Also add a `toString()` method for debugging:

```java
@Override
public String toString() {
    return "HeatStateMachine{transitions=" + TRANSITIONS + "}";
}
```

- [ ] **Step 2: Verify no IllegalStateException in qualifying flow**

Start a server with a qualifying round. Use `/heat start` to trigger the countdown → `QualifyingSession.start()` path. Confirm it no longer throws `IllegalStateException`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Heat/HeatStateMachine.java
git commit -m "fix: add missing QUALIFYING transitions to HeatStateMachine"
```

---

### Task 2: Fix heat config persistence — Read all columns in buildHeatFromResultSet and loadActiveEvents

**Priority:** CRITICAL — DRS, P2P, collision mode, realistic, ghosting, reverse grid, driver swap, and all physics parameters are LOST on server restart

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Database/EventsDatabaseManager.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Database/DatabaseManager.java`

**Problem 1:** `createHeat()` (line 134) inserts 21 columns including `drs, driverswap, colisao, drsdowntime, drsdownpower, reversegrid, ghostingdelta, pushtopass, pushtopasspower, realistc`, but the `fr_heats` CREATE TABLE (line 390) only has 15 columns. These extra columns don't exist in the schema unless ALTER TABLE was run at some point.

**Problem 2:** `buildHeatFromResultSet()` (line 1098) and the `loadActiveEvents()` heat-loading loop (line 492) only read `totalLaps, totalPitstops, startDelay, maxDrivers, lonely, canReset` — missing all the new columns.

**Problem 3:** The `loadEvent(int)` method's heat loading (line 303) also misses these columns.

- [ ] **Step 1: Add missing columns to fr_heats CREATE TABLE in DatabaseManager.initDatabase()**

In `DatabaseManager.java`, find the `fr_heats` CREATE TABLE at line 390 and replace it with:

```java
"CREATE TABLE IF NOT EXISTS fr_heats (id INTEGER PRIMARY KEY AUTOINCREMENT, roundId INTEGER NOT NULL, heatNumber INTEGER NOT NULL, state TEXT NOT NULL, startTime INTEGER DEFAULT NULL, endTime INTEGER DEFAULT NULL, fastestLapUUID TEXT, totalLaps INTEGER DEFAULT NULL, totalPitstops INTEGER DEFAULT NULL, timeLimit INTEGER DEFAULT NULL, startDelay INTEGER DEFAULT NULL, maxDrivers INTEGER DEFAULT NULL, lonely INTEGER DEFAULT NULL, canReset INTEGER DEFAULT NULL, lapReset INTEGER DEFAULT NULL, colisao TEXT DEFAULT 'DISABLED', drs INTEGER DEFAULT 0, driverswap INTEGER DEFAULT 0, drsdowntime REAL DEFAULT 0.0, drsdownpower REAL DEFAULT 0.0, reversegrid INTEGER DEFAULT 0, ghostingdelta REAL DEFAULT 0.0, pushtopass INTEGER DEFAULT 0, pushtopasspower REAL DEFAULT 0.0, realistc INTEGER DEFAULT 0)"
```

Then add idempotent ALTER TABLE statements for columns that may already exist from prior migrations. Add these right after the `fr_heats` CREATE TABLE, following the same try/catch pattern already used for `fr_boatutils`:

```java
String[] heatAlterColumns = {
    "ALTER TABLE fr_heats ADD COLUMN colisao TEXT DEFAULT 'DISABLED'",
    "ALTER TABLE fr_heats ADD COLUMN drs INTEGER DEFAULT 0",
    "ALTER TABLE fr_heats ADD COLUMN driverswap INTEGER DEFAULT 0",
    "ALTER TABLE fr_heats ADD COLUMN drsdowntime REAL DEFAULT 0.0",
    "ALTER TABLE fr_heats ADD COLUMN drsdownpower REAL DEFAULT 0.0",
    "ALTER TABLE fr_heats ADD COLUMN reversegrid INTEGER DEFAULT 0",
    "ALTER TABLE fr_heats ADD COLUMN ghostingdelta REAL DEFAULT 0.0",
    "ALTER TABLE fr_heats ADD COLUMN pushtopass INTEGER DEFAULT 0",
    "ALTER TABLE fr_heats ADD COLUMN pushtopasspower REAL DEFAULT 0.0",
    "ALTER TABLE fr_heats ADD COLUMN realistc INTEGER DEFAULT 0"
};
for (String alterSql : heatAlterColumns) {
    try {
        stmt.executeUpdate(alterSql);
    } catch (SQLException ignored) {}
}
```

- [ ] **Step 2: Update `buildHeatFromResultSet()` to read all columns**

In `EventsDatabaseManager.java`, replace the `buildHeatFromResultSet()` method (lines 1098-1133) with:

```java
private Heats buildHeatFromResultSet(ResultSet rs) throws SQLException {
    int heatId = rs.getInt("id");
    int roundId = rs.getInt("roundId");
    int heatNumber = rs.getInt("heatNumber");
    Heats heat = new Heats(this.plugin, heatId, (Rounds)null, heatNumber);
    heat.setRoundId(roundId);
    heat.setHeatState(HeatState.valueOf(rs.getString("state")));
    long startTimestamp = rs.getLong("startTime");
    if (startTimestamp > 0L) {
        heat.setStartTime(Instant.ofEpochSecond(startTimestamp));
    }
    long endTimestamp = rs.getLong("endTime");
    if (endTimestamp > 0L) {
        heat.setEndTime(Instant.ofEpochSecond(endTimestamp));
    }
    String fastestLapUUID = rs.getString("fastestLapUUID");
    if (fastestLapUUID != null) {
        heat.setFastestLapUUID(UUID.fromString(fastestLapUUID));
    }
    int totalLaps = rs.getInt("totalLaps");
    heat.setTotalLaps(rs.wasNull() ? null : totalLaps);
    int totalPits = rs.getInt("totalPitstops");
    heat.setTotalPits(rs.wasNull() ? null : totalPits);
    int timeLimit = rs.getInt("timeLimit");
    heat.setTimeLimit(rs.wasNull() ? null : timeLimit);
    heat.setStartDelay(rs.getInt("startDelay"));
    int maxDrivers = rs.getInt("maxDrivers");
    heat.setMaxDrivers(rs.wasNull() ? null : maxDrivers);
    heat.setLonely(rs.getInt("lonely") == 1);
    heat.setCanReset(rs.getInt("canReset") == 1);

    // Load extended heat config columns added for v0.2
    try { heat.setCollisionMode(CollisionMode.valueOf(rs.getString("colisao"))); } catch (SQLException ignored) { heat.setCollisionMode(CollisionMode.DISABLED); }
    try { heat.setDrsEnabled(rs.getInt("drs") == 1); } catch (SQLException ignored) {}
    try { heat.setDriverswap(rs.getInt("driverswap") == 1); } catch (SQLException ignored) {}
    try { heat.setDrsdowntime(rs.getDouble("drsdowntime")); } catch (SQLException ignored) {}
    try { heat.setDrsdownpower(rs.getDouble("drsdownpower")); } catch (SQLException ignored) {}
    try { heat.setReversegrid(rs.getInt("reversegrid") == 1); } catch (SQLException ignored) {}
    try { heat.setDeltaghosting((int) rs.getDouble("ghostingdelta")); } catch (SQLException ignored) {}
    try { heat.setPushtopass(rs.getInt("pushtopass") == 1); } catch (SQLException ignored) {}
    try { heat.setPushtopasspower(rs.getDouble("pushtopasspower")); } catch (SQLException ignored) {}
    try { heat.setRealistc(rs.getInt("realistc") == 1); } catch (SQLException ignored) {}

    for (Driver driver : this.loadDriversByHeatId(heat.getId())) {
        heat.addDriverDirect(driver);
    }
    return heat;
}
```

Add the import at the top of the file:
```java
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
```

- [ ] **Step 3: Update `loadActiveEvents()` heat-loading to read all columns**

In `EventsDatabaseManager.java`, the `loadActiveEvents()` method (starting ~line 491) reads heat data into a `Map<String, Object>` and then constructs `Heats` objects. This map only captures the basic columns. Replace the heat data population loop and construction loop.

Find the section starting at approximately line 491 (the `while(rs.next())` inside the heat query) and replace the data map population AND the heat construction loop. The new code should read all columns directly instead of via intermediate maps:

Replace the inner `while(rs.next())` block that populates `heatData` (approximately lines 492-508) and the construction loop (lines 512-540) with:

```java
try (ResultSet rs = stmt.executeQuery()) {
    while(rs.next()) {
        int heatId = rs.getInt("id");
        int roundId = rs.getInt("roundId");
        int heatNumber = rs.getInt("heatNumber");
        Heats heat = new Heats(this.plugin, heatId, (Rounds)null, heatNumber);
        heat.setRoundId(roundId);
        heat.setHeatState(HeatState.valueOf(rs.getString("state")));

        long startTimeValue = rs.getLong("startTime");
        long endTimeValue = rs.getLong("endTime");
        if (startTimeValue > 0L) {
            heat.setStartTime(Instant.ofEpochSecond(startTimeValue));
        }
        if (endTimeValue > 0L) {
            heat.setEndTime(Instant.ofEpochSecond(endTimeValue));
        }

        String fastestLapUUID = rs.getString("fastestLapUUID");
        if (fastestLapUUID != null) {
            heat.setFastestLapUUID(UUID.fromString(fastestLapUUID));
        }

        int totalLaps = rs.getInt("totalLaps");
        heat.setTotalLaps(rs.wasNull() ? null : totalLaps);
        int totalPits = rs.getInt("totalPitstops");
        heat.setTotalPits(rs.wasNull() ? null : totalPits);
        int timeLimit = rs.getInt("timeLimit");
        heat.setTimeLimit(rs.wasNull() ? null : timeLimit);
        heat.setStartDelay(rs.getInt("startDelay"));
        int maxDrivers = rs.getInt("maxDrivers");
        heat.setMaxDrivers(rs.wasNull() ? null : maxDrivers);
        heat.setLonely(rs.getInt("lonely") == 1);
        heat.setCanReset(rs.getInt("canReset") == 1);

        try { heat.setCollisionMode(CollisionMode.valueOf(rs.getString("colisao"))); } catch (SQLException ignored) { heat.setCollisionMode(CollisionMode.DISABLED); }
        try { heat.setDrsEnabled(rs.getInt("drs") == 1); } catch (SQLException ignored) {}
        try { heat.setDriverswap(rs.getInt("driverswap") == 1); } catch (SQLException ignored) {}
        try { heat.setDrsdowntime(rs.getDouble("drsdowntime")); } catch (SQLException ignored) {}
        try { heat.setDrsdownpower(rs.getDouble("drsdownpower")); } catch (SQLException ignored) {}
        try { heat.setReversegrid(rs.getInt("reversegrid") == 1); } catch (SQLException ignored) {}
        try { heat.setDeltaghosting((int) rs.getDouble("ghostingdelta")); } catch (SQLException ignored) {}
        try { heat.setPushtopass(rs.getInt("pushtopass") == 1); } catch (SQLException ignored) {}
        try { heat.setPushtopasspower(rs.getDouble("pushtopasspower")); } catch (SQLException ignored) {}
        try { heat.setRealistc(rs.getInt("realistc") == 1); } catch (SQLException ignored) {}

        Rounds round = roundMap.get(roundId);
        if (round != null) {
            heat.setRound(round);
            heat.setTrackNameWS(round.getEvent().getTrackNameWS());
            round.getHeats().put(heatNumber, heat);
            this.loadDriversForHeat(heat);
        }
    }
}
```

Also remove the now-unused `heatData` list declaration and the construction loop that uses it.

- [ ] **Step 4: Update `loadEvent()` heat-loading similarly**

In `EventsDatabaseManager.loadEvent()` (the section around lines 303-351 that loads heats), apply the same extended column reading. Replace the `while(rs.next())` block and the construction loop with the same pattern as Step 3, reading all columns directly.

- [ ] **Step 5: Add `updateHeatFullConfig()` method to EventsDatabaseManager**

Add a new method to persist all heat configuration fields at once:

```java
public void updateHeatFullConfig(int heatId, Integer totalLaps, Integer totalPits, Integer timeLimit,
                                  Integer startDelay, Integer maxDrivers, boolean lonely, boolean canReset,
                                  boolean lapReset, CollisionMode collisionMode, boolean drsEnabled,
                                  boolean driverswap, double drsdowntime, double drsdownpower,
                                  boolean reversegrid, int deltaghosting, boolean pushtopass,
                                  double pushtopasspower, boolean realistc) {
    String sql = "UPDATE fr_heats SET totalLaps = ?, totalPitstops = ?, timeLimit = ?, startDelay = ?, " +
            "maxDrivers = ?, lonely = ?, canReset = ?, lapReset = ?, colisao = ?, drs = ?, " +
            "driverswap = ?, drsdowntime = ?, drsdownpower = ?, reversegrid = ?, ghostingdelta = ?, " +
            "pushtopass = ?, pushtopasspower = ?, realistc = ? WHERE id = ?";
    this.executeAsync(sql, "updateHeatFullConfig", (stmt) -> {
        try {
            if (totalLaps != null) { stmt.setInt(1, totalLaps); } else { stmt.setNull(1, java.sql.Types.INTEGER); }
            if (totalPits != null) { stmt.setInt(2, totalPits); } else { stmt.setNull(2, java.sql.Types.INTEGER); }
            if (timeLimit != null) { stmt.setInt(3, timeLimit); } else { stmt.setNull(3, java.sql.Types.INTEGER); }
            if (startDelay != null) { stmt.setInt(4, startDelay); } else { stmt.setNull(4, java.sql.Types.INTEGER); }
            if (maxDrivers != null) { stmt.setInt(5, maxDrivers); } else { stmt.setNull(5, java.sql.Types.INTEGER); }
            stmt.setInt(6, lonely ? 1 : 0);
            stmt.setInt(7, canReset ? 1 : 0);
            stmt.setInt(8, lapReset ? 1 : 0);
            stmt.setString(9, collisionMode.name());
            stmt.setInt(10, drsEnabled ? 1 : 0);
            stmt.setInt(11, driverswap ? 1 : 0);
            stmt.setDouble(12, drsdowntime);
            stmt.setDouble(13, drsdownpower);
            stmt.setInt(14, reversegrid ? 1 : 0);
            stmt.setDouble(15, (double) deltaghosting);
            stmt.setInt(16, pushtopass ? 1 : 0);
            stmt.setDouble(17, pushtopasspower);
            stmt.setInt(18, realistc ? 1 : 0);
            stmt.setInt(19, heatId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    });
}
```

- [ ] **Step 6: Build and verify**

Run `mvn compile` to verify compilation succeeds. Start the server, create a heat with custom configs (DRS, P2P, etc.), restart the server, and verify the heat config is preserved.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Database/DatabaseManager.java src/main/java/dev/EfraGroup/formulaRacing/Database/EventsDatabaseManager.java
git commit -m "fix: persist and load all heat configuration columns on server restart"
```

---

### Task 3: Add subscriber/reserve/spectator DB persistence

**Priority:** CRITICAL — All signup data is lost on server restart

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Database/DatabaseManager.java` — add CREATE TABLE
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Database/EventsDatabaseManager.java` — add CRUD methods
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Event/Events.java` — call DB methods on add/remove
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Controllers/RaceEventManager.java` — load signups in loadActiveEvents

- [ ] **Step 1: Add `fr_event_signups` table in DatabaseManager.initDatabase()**

Add after the `fr_laps` CREATE TABLE (line ~396):

```java
stmt.executeUpdate(
    "CREATE TABLE IF NOT EXISTS fr_event_signups (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
    "eventId INTEGER NOT NULL, uuid TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'SUBSCRIBER', " +
    "subscriptionTime INTEGER NOT NULL, confirmed INTEGER NOT NULL DEFAULT 0)"
);
```

Add idempotent ALTER TABLE for the `type` column (in case table already exists from a prior partial migration):

```java
try { stmt.executeUpdate("ALTER TABLE fr_event_signups ADD COLUMN type TEXT NOT NULL DEFAULT 'SUBSCRIBER'"); } catch (SQLException ignored) {}
try { stmt.executeUpdate("ALTER TABLE fr_event_signups ADD COLUMN confirmed INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
```

- [ ] **Step 2: Add Subscriber/Reserve CRUD methods to EventsDatabaseManager**

Add the following methods:

```java
public void addSignup(int eventId, UUID playerUUID, String type) {
    String sql = "INSERT INTO fr_event_signups (eventId, uuid, type, subscriptionTime) VALUES (?, ?, ?, ?)";
    this.executeAsync(sql, "addSignup", (stmt) -> {
        try {
            stmt.setInt(1, eventId);
            stmt.setString(2, playerUUID.toString());
            stmt.setString(3, type);
            stmt.setLong(4, System.currentTimeMillis());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    });
}

public void removeSignup(int eventId, UUID playerUUID) {
    String sql = "DELETE FROM fr_event_signups WHERE eventId = ? AND uuid = ?";
    this.executeAsync(sql, "removeSignup", (stmt) -> {
        try {
            stmt.setInt(1, eventId);
            stmt.setString(2, playerUUID.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    });
}

public List<Map<String, Object>> loadSignupsForEvent(int eventId) {
    String sql = "SELECT uuid, type, subscriptionTime, confirmed FROM fr_event_signups WHERE eventId = ?";
    List<Map<String, Object>> signups = new ArrayList<>();
    try {
        Connection conn = this.databaseManager.getOrConnect();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("uuid", UUID.fromString(rs.getString("uuid")));
                    data.put("type", rs.getString("type"));
                    data.put("subscriptionTime", rs.getLong("subscriptionTime"));
                    data.put("confirmed", rs.getInt("confirmed") == 1);
                    signups.add(data);
                }
            }
        }
    } catch (SQLException e) {
        this.plugin.getDebugManager().logDatabaseOperation("[EventsDB] Erro ao carregar signups: " + e.getMessage());
    }
    return signups;
}
```

- [ ] **Step 3: Load signups into Events during loadActiveEvents and loadEvent**

In the `loadActiveEvents()` method, after building the `Events` object and before calling `setCurrentRoundAutomatically()`, add:

```java
List<Map<String, Object>> signups = this.dbManager.loadSignupsForEvent(eventId);
for (Map<String, Object> signup : signups) {
    UUID playerUUID = (UUID) signup.get("uuid");
    String type = (String) signup.get("type");
    if ("RESERVE".equals(type)) {
        Subscriber sub = new Subscriber(playerUUID, eventId);
        sub.setConfirmed((Boolean) signup.get("confirmed"));
        event.getReserves().put(playerUUID, sub);
    } else {
        Subscriber sub = new Subscriber(playerUUID, eventId);
        sub.setConfirmed((Boolean) signup.get("confirmed"));
        event.getSubscribers().put(playerUUID, sub);
    }
}
```

Apply the same pattern in `loadEvent()`.

- [ ] **Step 4: Persist signups on add/remove in Events.java**

In `Events.addSubscriber()` (line ~128), after adding to the map, call:

```java
if (this.id > 0 && this.raceEventManager != null) {
    this.raceEventManager.getDatabaseManager().addSignup(this.id, playerUUID, "SUBSCRIBER");
}
```

In `Events.removeSubscriber()` (line ~181), after removing from the map, call:

```java
if (this.id > 0 && this.raceEventManager != null) {
    this.raceEventManager.getDatabaseManager().removeSignup(this.id, playerUUID);
}
```

Apply the same pattern for `moveToReserves()` (change type from SUBSCRIBER to RESERVE), `moveFromReserves()` (change type from RESERVE to SUBSCRIBER), and the `addSpectator()`/remove methods (type = "SPECTATOR").

For `moveToReserves` and `moveFromReserves`, we need an `updateSignupType` method:

```java
public void updateSignupType(int eventId, UUID playerUUID, String newType) {
    String sql = "UPDATE fr_event_signups SET type = ? WHERE eventId = ? AND uuid = ?";
    this.executeAsync(sql, "updateSignupType", (stmt) -> {
        try {
            stmt.setString(1, newType);
            stmt.setInt(2, eventId);
            stmt.setString(3, playerUUID.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    });
}
```

- [ ] **Step 5: Build and verify**

Run `mvn compile`. Start the server, create an event, sign up a player, restart the server, verify the subscriber is still present.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Database/DatabaseManager.java src/main/java/dev/EfraGroup/formulaRacing/Database/EventsDatabaseManager.java src/main/java/dev/EfraGroup/formulaRacing/Event/Events.java
git commit -m "feat: persist subscribers, reserves, and spectators to database"
```

---

### Task 4: Add EventStateMachine for event state transitions

**Priority:** MODERATE — Prevents invalid state transitions like RUNNING→SETUP

**Files:**
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Event/EventStateMachine.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Event/Events.java`

- [ ] **Step 1: Create EventStateMachine.java**

```java
package dev.EfraGroup.formulaRacing.Event;

import java.util.Map;
import java.util.Set;

public class EventStateMachine {
    private static final Map<EventState, Set<EventState>> TRANSITIONS = Map.of(
        EventState.SETUP, Set.of(EventState.RUNNING),
        EventState.RUNNING, Set.of(EventState.FINISHED, EventState.SETUP),
        EventState.FINISHED, Set.of(EventState.SETUP)
    );

    public static boolean canTransition(EventState from, EventState to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(EventState from, EventState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                "Illegal EventState transition: " + from + " -> " + to
            );
        }
    }
}
```

Note: RUNNING→SETUP is allowed for admin reset scenarios. FINISHED→SETUP allows re-creating events.

- [ ] **Step 2: Use EventStateMachine in Events.setState()**

In `Events.java`, modify `setState()` (line ~303):

```java
public void setState(EventState state) {
    if (this.state != null && this.id > 0) {
        EventStateMachine.validateTransition(this.state, state);
    }
    this.state = state;
    if (this.id > 0 && this.raceEventManager != null) {
        this.raceEventManager.getDatabaseManager().updateEventState(this.id, state);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Event/EventStateMachine.java src/main/java/dev/EfraGroup/formulaRacing/Event/Events.java
git commit -m "feat: add EventStateMachine to validate event state transitions"
```

---

### Task 5: Add RoundStateMachine for round state transitions

**Priority:** MODERATE — Prevents invalid state transitions like FINISHED→RUNNING

**Files:**
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Round/RoundStateMachine.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Round/Rounds.java`

- [ ] **Step 1: Create RoundStateMachine.java**

```java
package dev.EfraGroup.formulaRacing.Round;

import java.util.Map;
import java.util.Set;

public class RoundStateMachine {
    private static final Map<RoundState, Set<RoundState>> TRANSITIONS = Map.of(
        RoundState.SETUP, Set.of(RoundState.RUNNING),
        RoundState.RUNNING, Set.of(RoundState.FINISHED),
        RoundState.FINISHED, Set.of(RoundState.SETUP)
    );

    public static boolean canTransition(RoundState from, RoundState to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(RoundState from, RoundState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                "Illegal RoundState transition: " + from + " -> " + to
            );
        }
    }
}
```

- [ ] **Step 2: Use RoundStateMachine in Rounds.setRoundState()**

In `Rounds.java`, modify `setRoundState()` (line ~181):

```java
public void setRoundState(RoundState roundState) {
    if (this.roundState != null) {
        RoundStateMachine.validateTransition(this.roundState, roundState);
    }
    this.roundState = roundState;
    if (this.id > 0 && this.plugin.getRaceEventManager() != null) {
        this.plugin.getRaceEventManager().getDatabaseManager().updateRoundState(this.id, roundState);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Round/RoundStateMachine.java src/main/java/dev/EfraGroup/formulaRacing/Round/Rounds.java
git commit -m "feat: add RoundStateMachine to validate round state transitions"
```

---

### Task 6: Add soft-delete pattern to events, rounds, heats

**Priority:** MODERATE — Prevents data loss from hard deletes, enables audit trails

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Database/DatabaseManager.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Database/EventsDatabaseManager.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Controllers/RaceEventManager.java`

- [ ] **Step 1: Add `isRemoved` column to fr_events, fr_rounds, fr_heats tables**

In `DatabaseManager.initDatabase()`, add after the existing ALTER TABLE blocks:

```java
// Soft-delete columns
try { stmt.executeUpdate("ALTER TABLE fr_events ADD COLUMN isRemoved INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
try { stmt.executeUpdate("ALTER TABLE fr_rounds ADD COLUMN isRemoved INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
try { stmt.executeUpdate("ALTER TABLE fr_heats ADD COLUMN isRemoved INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
try { stmt.executeUpdate("ALTER TABLE fr_drivers ADD COLUMN isRemoved INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
```

Also update the CREATE TABLE statements to include `isRemoved INTEGER NOT NULL DEFAULT 0` in `fr_events`, `fr_rounds`, `fr_heats`, and `fr_drivers`.

- [ ] **Step 2: Update DELETE queries to use soft-delete in EventsDatabaseManager**

Replace all `DELETE FROM fr_heats WHERE ...`, `DELETE FROM fr_drivers WHERE ...`, etc. with `UPDATE ... SET isRemoved = 1 WHERE ...`.

In `deleteEvent()` (line ~596): Replace the cascade of DELETE queries with soft-delete updates:

```java
// Instead of DELETE, use soft delete
String[] updateSqls = {
    "UPDATE fr_drivers SET isRemoved = 1 WHERE heatId IN (SELECT id FROM fr_heats WHERE roundId IN (SELECT id FROM fr_rounds WHERE eventId = ?))",
    "UPDATE fr_laps SET ... -- laps can stay as-is since they're historical data, or add isRemoved too
    "UPDATE fr_heats SET isRemoved = 1 WHERE roundId IN (SELECT id FROM fr_rounds WHERE eventId = ?)",
    "UPDATE fr_rounds SET isRemoved = 1 WHERE eventId = ?",
    "UPDATE fr_events SET isRemoved = 1, state = 'FINISHED' WHERE id = ?"
};
```

Apply the same pattern for `deleteRound()` and `deleteHeat()`.

- [ ] **Step 3: Exclude soft-deleted records in SELECT queries**

Update `loadActiveEvents()` and `loadEvent()` to exclude `isRemoved = 1` records:

- `fr_events WHERE state IN (?, ?)` → `WHERE state IN (?, ?) AND (isRemoved = 0 OR isRemoved IS NULL)`
- `fr_rounds WHERE eventId IN (...)` → `AND (isRemoved = 0 OR isRemoved IS NULL)`
- `fr_heats WHERE roundId IN (...)` → `AND (isRemoved = 0 OR isRemoved IS NULL)`

The `OR isRemoved IS NULL` handles existing databases that don't have the column yet (before ALTER TABLE runs).

- [ ] **Step 4: Update RaceEventManager.removeEvent()**

In `RaceEventManager.removeEvent()`, instead of calling `dbManager.deleteEvent()`, call the new soft-delete method. Also remove the event from in-memory maps (`activeEvents`, `eventsByName`, `playerActiveEvent`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Database/DatabaseManager.java src/main/java/dev/EfraGroup/formulaRacing/Database/EventsDatabaseManager.java src/main/java/dev/EfraGroup/formulaRacing/Controllers/RaceEventManager.java
git commit -m "feat: add soft-delete pattern for events, rounds, heats, and drivers"
```

---

### Task 7: Fix QualificationManager.applyGridToFinalRound to use proper addDriver flow

**Priority:** MODERATE — Prevents inconsistent state from bypassing validation

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Controllers/QualificationManager.java`

- [ ] **Step 1: Refactor applyGridToFinalRound()**

Replace the current implementation (lines 73-94) with proper flow using `HeatDriverCommandService` and `addDriver()`:

```java
private void applyGridToFinalRound(Rounds finalRound, List<QualificationResult> results) {
    // Clear existing drivers properly via DB
    for (Heats heat : finalRound.getHeats().values()) {
        heat.getDrivers().clear();
        if (heat.getId() > 0) {
            plugin.getRaceEventManager().getDatabaseManager().clearHeatDriversSync(heat.getId());
        }
    }

    Heats finalHeat = finalRound.getHeat(1).orElse(null);
    if (finalHeat == null) {
        plugin.getDebugManager().logRaceSystem("ERRO: Heat final não encontrado!");
        return;
    }

    for (int i = 0; i < results.size(); i++) {
        QualificationResult result = results.get(i);
        int gridPosition = i + 1;
        UUID driverUUID = result.getDriverUUID();

        // Check maxDrivers limit
        if (finalHeat.getMaxDrivers() != null && finalHeat.getMaxDrivers() > 0
                && finalHeat.getDrivers().size() >= finalHeat.getMaxDrivers()) {
            plugin.getDebugManager().logRaceSystem(
                "Aviso: Driver " + driverUUID + " excedeu maxDrivers (" + finalHeat.getMaxDrivers() + ")"
            );
            continue;
        }

        // Use addDriver which handles DriverLookup registration and position shifting
        finalHeat.addDriver(driverUUID, gridPosition);
    }

    plugin.getDebugManager().logRaceSystem("Grid de largada definido com " + results.size() + " drivers.");
}
```

This ensures:
- `DriverLookup` registration happens correctly
- Max driver limits are respected
- Position shifting works properly
- Duplicate player checks are enforced

- [ ] **Step 2: Verify qualification flow**

Start a qualifying heat, have drivers complete laps, then start the final round. Verify grid positions are set correctly and `DriverLookup` contains all drivers.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Controllers/QualificationManager.java
git commit -m "fix: use proper addDriver flow in QualificationManager.applyGridToFinalRound"
```

---

### Task 8: Implement PracticeRound.broadcastResults()

**Priority:** MINOR — Practice results are never announced

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Round/PracticeRound.java`

- [ ] **Step 1: Implement broadcastResults()**

Replace the stub at line 32 with a real implementation that broadcasts top lap times:

```java
@Override
public void broadcastResults() {
    plugin.getDebugManager().logRaceSystem("Processando resultados de practice...");

    for (Heats heat : heats.values()) {
        List<Driver> sortedDrivers = heat.getDrivers().values().stream()
            .filter(d -> d.getBestLapTime() > 0)
            .sorted(Comparator.comparingLong(Driver::getBestLapTime))
            .collect(Collectors.toList());

        if (sortedDrivers.isEmpty()) {
            continue;
        }

        String heatLabel = heats.size() > 1 ? " (Heat " + heat.getHeatNumber() + ")" : "";

        for (int i = 0; i < sortedDrivers.size(); i++) {
            Driver driver = sortedDrivers.get(i);
            Player player = Bukkit.getPlayer(driver.getUuid());
            if (player != null) {
                String pos = String.valueOf(i + 1);
                String time = TimerUtils.formatTime(driver.getBestLapTime());
                plugin.sendMessage(player, "practice_result", new String[]{"{position}", pos, "{time}", time});
            }
        }

        // Broadcast top 3 to all spectators
        if (this.event != null) {
            for (int i = 0; i < Math.min(3, sortedDrivers.size()); i++) {
                Driver driver = sortedDrivers.get(i);
                String name = Bukkit.getOfflinePlayer(driver.getUuid()).getName();
                String time = TimerUtils.formatTime(driver.getBestLapTime());
                String message = "P" + (i + 1) + ": " + name + " - " + time + heatLabel;
                for (Spectator spectator : this.event.getSpectators().values()) {
                    Player spectatorPlayer = Bukkit.getPlayer(spectator.getUuid());
                    if (spectatorPlayer != null) {
                        spectatorPlayer.sendMessage(message);
                    }
                }
            }
        }
    }

    plugin.getDebugManager().logRaceSystem("Practice finalizado! Resultados anunciados.");
}
```

Add imports:
```java
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Participant.Spectator;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
```

Note: Check if `Driver.getBestLapTime()` exists. If the method name differs (e.g., `getBestLap()` or the lap tracking uses a different structure), adjust accordingly based on the `Driver` class.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Round/PracticeRound.java
git commit -m "feat: implement PracticeRound.broadcastResults() with lobby and spectator announcements"
```

---

### Task 9: Add updateHeatFullConfig hook in Heats state changes

**Priority:** MODERATE — Ensure config changes are persisted when heats transition states

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Heat/Heats.java`

- [ ] **Step 1: Persist full heat config when heat is created or loaded**

In `Heats.startCountdown()` (line ~357), add a call to persist the heat config before transitioning to STARTING, so any runtime config changes are saved:

After the existing state validation, add:

```java
// Persist full heat config before starting countdown
if (this.id > 0 && plugin != null && plugin.getRaceEventManager() != null) {
    plugin.getRaceEventManager().getDatabaseManager().updateHeatFullConfig(
        this.id, this.totalLaps, this.totalPits, this.timeLimit,
        this.startDelay, this.maxDrivers, this.lonely, this.canReset,
        true, this.collisionMode, this.drsEnabled,
        this.driverswap, this.drsdowntime, this.drsdownpower,
        this.reversegrid, this.deltaghosting, this.pushtopass,
        this.pushtopasspower, this.realistc
    );
}
```

Note: The `lapReset` parameter is hardcoded to `true` since it's not a stored field on `Heats` (or adjust if it is). Verify the field exists or remove from the call.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Heat/Heats.java
git commit -m "fix: persist full heat config before countdown starts"
```

---

### Task 10: Clean up decompiled-named fields and methods

**Priority:** MINOR — Code quality / readability

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Heat/Heats.java` — rename fields
- Modify all callers of the renamed fields (search across entire codebase)

This task is invasive and involves renaming public fields/methods, so it should be done when no other task is in progress. The renames are:

| Current Name | New Name | Scope |
|---|---|---|
| `getrealistc()` / `isRealistc()` | `isRealistic()` | Heats + all callers |
| `realistc` field | `realistic` | Heats.java |
| `setpushtopasspower()` | `setPushToPassPower()` | Heats + callers |
| `pushtopasspower` field | `pushToPassPower` | Heats.java |
| `deltaghosting` field | `deltaGhosting` | Heats.java |
| `driverswap` field | `driverSwap` | Heats.java |
| `getpushtopass()` / `isPushtopass()` | `isPushToPass()` | Heats + callers |
| `setpushtopass()` | `setPushToPass()` | Heats + callers |
| `pushtopass` field | `pushToPass` | Heats.java |

Since this is a broad refactor affecting many files, it is marked as **optional** and should only be done in a separate commit with thorough search-replace and compilation verification.

- [ ] **Step 1: Use IDE refactoring or search-replace to rename each field/method one at a time**

For each rename:
1. Search all occurrences across `src/main/java/`
2. Replace all occurrences
3. Run `mvn compile` to verify no compilation errors
4. Commit individually

- [ ] **Step 2: Update database column names in EventsDatabaseManager**

The DB columns (`realistc`, `pushtopass`, `pushtopasspower`, `ghostingdelta`, `driverswap`, `colisao`) should remain as-is since they're already deployed. Only the Java field/method names change. The DB mapping in `buildHeatFromResultSet()` and `updateHeatFullConfig()` must still use the old column names.

- [ ] **Step 3: Final compile and commit**

```bash
mvn compile
git add -A
git commit -m "refactor: rename decompiled field/method names to Java conventions"
```

---

## Implementation Order

Tasks should be executed in this order based on priority and dependencies:

1. **Task 1** (HeatStateMachine fix) — stand-alone, CRITICAL, prevents crashes
2. **Task 2** (Heat config persistence) — stand-alone, CRITICAL, prevents data loss
3. **Task 3** (Subscriber persistence) — stand-alone, CRITICAL, prevents data loss
4. **Task 4** (EventStateMachine) — stand-alone, MODERATE
5. **Task 5** (RoundStateMachine) — stand-alone, MODERATE
6. **Task 7** (QualificationManager fix) — stand-alone, MODERATE
7. **Task 9** (Heat config persist on countdown) — depends on Task 2
8. **Task 6** (Soft-delete) — MODERATE, requires careful DB migration
9. **Task 8** (PracticeRound broadcast) — stand-alone, MINOR
10. **Task 10** (Naming cleanup) — optional, MINOR, invasive

Tasks 1–3 can be done in parallel. Tasks 4–5 can be done in parallel. Task 6 depends on Task 2 being merged first (since both modify DB schema). Task 9 depends on Task 2.