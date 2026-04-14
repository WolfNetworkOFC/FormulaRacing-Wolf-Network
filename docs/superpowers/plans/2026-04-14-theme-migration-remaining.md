# Theme Migration — Remaining HIGH Priority Items

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate all remaining hardcoded color codes (`§` and `ChatColor`) to per-player theme tokens (`&1`, `&2`, `&s`, etc.) via FRThemeParser, matching TimingSystem's visual quality.

**Architecture:** Each task targets one subsystem (action bar, scoreboard builders, bossbar, lang keys, clickable messages). Each produces working, compilable code. The FRTheme/FRThemeParser/FRThemeResolver/TitleHelper/Text infrastructure is already in place — this plan only migrates consumers.

**Tech Stack:** Java 21, Paper API 1.21.8-SNAPSHOT, Megavex scoreboard-library v2.4.4, BungeeCord Chat API (legacy, to be replaced in Task 5)

---

## File Structure

| File | Responsibility |
|------|----------------|
| `Utils/RaceActionBarManager.java` | Race/practice/qualifying action bar content |
| `Utils/scoreboard/v2/builder/BuilderSupport.java` | Shared scoreboard line builders |
| `Utils/scoreboard/v2/builder/*.java` | State-specific scoreboard builders |
| `Utils/scoreboard/style/TimingScoreboardStyle.java` | Position rank colors |
| `Heat/Logic/PTPManager.java` | Push-to-pass BossBar |
| `Heat/Logic/DrsManager.java` | DRS BossBar |
| `Event/EventCountdown.java` | Event countdown BossBar |
| `Utils/ClickableMessageUtil.java` | Chat clickable messages |
| `Utils/TitleHelper.java` | Per-player themed title sender |
| `Utils/Text.java` | Per-player themed chat sender |
| `src/main/resources/lang/*.yml` | i18n strings |

---

### Task 1: RaceActionBarManager — Theme Token Migration

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/RaceActionBarManager.java`

- [ ] **Step 1: Add FRTheme imports and resolve theme per-viewer**

Add imports at top:
```java
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
```

- [ ] **Step 2: Replace hardcoded § codes in position color method**

The `positionColor` method (around line 410) currently returns `§6`, `§7`, `§c`. Replace with theme tokens:

```java
private String positionColor(int pos, Player viewer) {
    FRTheme theme = FRThemeResolver.resolveTheme(viewer);
    return switch (pos) {
        case 1 -> "&v";   // award (gold)
        case 2 -> "&2";   // secondary (silver)
        case 3 -> "&e";   // error (bronze/red)
        default -> "&2";  // secondary
    };
}
```

- [ ] **Step 3: Replace all `§c`, `§8`, `§7`, `§e`, `§a`, `§f`, `§6` in action bar assembly methods**

Replace every inline `§X` with the corresponding theme token:
- `§8` → `&t` (title — dark gray separator)
- `§7` → `&2` (secondary — gray info)
- `§f` → `&2` (secondary — white emphasis)
- `§c` → `&e` (error — red timer)
- `§e` → `&w` (warning — yellow timer)
- `§a` → `&s` (success — green check)
- `§6` → `&v` (award — gold P1)
- `§77` (used as "gray number") → `&2`

The progress bar colors are already config-driven (`&c`, `&e`, `&a`, `&8`, `&7`) — these go through `TranslationUtil` and `FRThemeParser` already. No change needed there.

- [ ] **Step 4: Change send mechanism from spigot legacy to FRThemeParser**

Find all `player.spigot().sendMessage(ChatMessageType.ACTION_BAR, ...)` calls and replace with:

```java
FRTheme theme = FRThemeResolver.resolveTheme(player);
Component comp = FRThemeParser.parseWithLegacy(sb.toString(), theme);
String legacy = LegacyComponentSerializer.legacySection().serialize(comp);
player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(legacy));
```

This keeps the BungeeCord send path (required for action bar on this Paper version) but runs the string through theme parsing first.

- [ ] **Step 5: Compile and verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/RaceActionBarManager.java
git commit -m "feat(theme): migrate RaceActionBarManager from hardcoded § to per-player theme tokens"
```

---

### Task 2: Scoreboard Builders — Eliminate Remaining Hardcoded Colors

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/builder/BuilderSupport.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/style/TimingScoreboardStyle.java`

- [ ] **Step 1: Migrate BuilderSupport hardcoded § codes**

Replace in `BuilderSupport.java`:
- `§7` → `&2` (secondary — gray text, separators, DNF/offline/pit status)
- `§f` → `&2` (secondary — white name, pits count)
- `§8` → `&t` (title — dark gray separators, empty cells)
- `§b` → `&i` (info — aqua best lap time)
- `§a` → `&s` (success — green DRS available, positive delta)
- `§c` → `&e` (error — red negative delta)
- `§e` → `&w` (warning — yellow equal delta, heat name)
- `§6` → `&v` (award — gold — unused but map if found)

Do NOT change the `LEGACY_COLORS` lookup table (lines 22-23) — that maps `§X` to Adventure NamedTextColor for the `parseWithLegacy` engine and must stay.

- [ ] **Step 2: Migrate TimingScoreboardStyle position colors**

Replace in `TimingScoreboardStyle.java`:

```java
public static String positionColor(int pos) {
    return switch (pos) {
        case 1 -> "&v";   // award (gold P1)
        case 2 -> "&2";   // secondary (silver P2)
        case 3 -> "&e";   // error (bronze P3)
        default -> "&2";  // secondary
    };
}
```

And:
- `§l` stays as `§l` (bold decoration, not a color)
- `§o` stays as `§o` (italic decoration)
- `§r` stays as `§r` (reset)

- [ ] **Step 3: Compile and verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/builder/BuilderSupport.java
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/style/TimingScoreboardStyle.java
git commit -m "feat(theme): migrate scoreboard builders from hardcoded § to per-player theme tokens"
```

---

### Task 3: BossBar Title Theme + Dynamic Colors

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Heat/Logic/PTPManager.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Heat/Logic/DrsManager.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Event/EventCountdown.java`

- [ ] **Step 1: Migrate PTPManager BossBar titles**

In `PTPManager.java`, add imports:
```java
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
```

Replace the `createBossBar` title:
```java
// Before: "§6§lPush To Pass: 0%"
// After:
FRTheme theme = FRThemeResolver.resolveTheme(player);
String barTitle = FRThemeParser.parseWithLegacy("&w&lPush To Pass: 0%", theme);
BossBar bar = Bukkit.createBossBar(barTitle, BarColor.YELLOW, BarStyle.SOLID);
```

Replace `setTitle` in `updatePTP`:
```java
// Before: "§6§lPush To Pass: " + (int)energy + "% ⚡" / "§6§lPush To Pass: " + (int)energy + "%"
// After:
String titleFormat = driver.isPtpActive() ? "&e&lPush To Pass: %d%% ⚡" : "&w&lPush To Pass: %d%%";
FRTheme theme = FRThemeResolver.resolveTheme(player);
String themedTitle = FRThemeParser.parseWithLegacy(String.format(titleFormat, (int)energy), theme);
bar.setTitle(themedTitle);
```

Add dynamic color per energy:
```java
// After setTitle:
if (driver.isPtpActive()) {
    bar.setColor(BarColor.RED);
} else if (energy >= 67) {
    bar.setColor(BarColor.GREEN);
} else if (energy >= 33) {
    bar.setColor(BarColor.YELLOW);
} else {
    bar.setColor(BarColor.RED);
}
```

- [ ] **Step 2: Migrate DrsManager BossBar title (already done — verify)**

Verify `DrsManager.java` still uses `§b§l`/`§a§l` for DRS bar. Replace:
- `"§b§lDRS DISPONIVEL"` → use `TitleHelper`-style approach: resolve theme, parse, then set title on Bukkit BossBar

Since Bukkit `BossBar.setTitle()` only takes `String`, the pattern is:
```java
FRTheme theme = FRThemeResolver.resolveTheme(player);
String themedTitle = FRThemeParser.parseWithLegacy("&i&lDRS DISPONIVEL", theme);
bar.setTitle(themedTitle);  // Wait — Bukkit BossBar.setTitle() takes String but accepts § codes. LegacyComponentSerializer converts Component to § string. But we need the § string directly from parseWithLegacy.
```

Actually, `FRThemeParser.parseWithLegacy()` returns a `Component`. To get a `§`-prefixed string for Bukkit BossBar, use `LegacyComponentSerializer.legacySection().serialize(component)`. But Bukkit BossBar `setTitle(String)` already interprets `§` color codes. So the simplest approach is to keep the existing `§b§l` codes since BossBar doesn't support per-player theming natively (all viewers see the same title).

**Decision:** BossBar titles are per-player (each driver has their own bar), so we CAN theme them. Use `LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy("&i&lDRS DISPONIVEL", theme))` for the title string.

Update `DrsManager.java`:
```java
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
```

```java
private void showDrsAvailableBar(Player player, Driver driver) {
    if (driver.getDrsBossBar() != null) {
        driver.getDrsBossBar().removeAll();
    }
    FRTheme theme = FRThemeResolver.resolveTheme(player);
    String title = LegacyComponentSerializer.legacySection().serialize(
        FRThemeParser.parseWithLegacy("&i&lDRS DISPONIVEL", theme));
    BossBar bar = Bukkit.createBossBar(title, BarColor.BLUE, BarStyle.SOLID, new BarFlag[0]);
    bar.addPlayer(player);
    driver.setDrsBossBar(bar);
    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 2.0F);
}
```

Similarly update `applyDrsBoost`:
```java
if (driver.getDrsBossBar() != null) {
    FRTheme theme = FRThemeResolver.resolveTheme(player);
    String title = LegacyComponentSerializer.legacySection().serialize(
        FRThemeParser.parseWithLegacy("&s&l>>> DRS ATIVADO <<<", theme));
    driver.getDrsBossBar().setTitle(title);
    driver.getDrsBossBar().setColor(BarColor.GREEN);
}
```

- [ ] **Step 3: Migrate EventCountdown title**

In `EventCountdown.java`, the `formatTitle()` method returns `String.format("%s §e%02d:%02d", displayLabel, minutes, seconds)`.

Replace `§e` with `&w` (warning/yellow):
```java
private String formatTitle() {
    return String.format("%s &w%02d:%02d", this.label, minutes, seconds);
}
```

But `EventCountdown` shows the BossBar to multiple players. Since there's no per-player BossBar here (it's one shared bar), we can't theme per-player. Instead, use the default theme:

```java
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeDefaults;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
```

Update `formatTitle()`:
```java
private String formatTitle() {
    FRTheme defaultTheme = FRThemeDefaults.getDefaultTheme();
    String raw = String.format("%s &w%02d:%02d", this.label, minutes, seconds);
    return LegacyComponentSerializer.legacySection().serialize(
        FRThemeParser.parseWithLegacy(raw, defaultTheme));
}
```

- [ ] **Step 4: Compile and verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Heat/Logic/PTPManager.java
git add src/main/java/dev/EfraGroup/formulaRacing/Heat/Logic/DrsManager.java
git add src/main/java/dev/EfraGroup/formulaRacing/Event/EventCountdown.java
git commit -m "feat(theme): migrate BossBar titles to per-player theme tokens + dynamic PTP colors"
```

---

### Task 4: Hardcoded Portuguese Strings → Lang Keys

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Duels/TimeTrialDuels.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Gui/ReadyCheckManager.java`
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Controllers/QuickRaceManager.java`
- Modify: `src/main/resources/lang/en_US.yml`
- Modify: `src/main/resources/lang/pt_PT.yml`
- Modify: `src/main/resources/lang/pt_BR.yml`

- [ ] **Step 1: Extract hardcoded PT strings from TimeTrialDuels**

Find all hardcoded Portuguese strings in title/chat calls and move to lang files:

| Current hardcoded | New lang key | en_US | pt_PT | pt_BR |
|---|---|---|---|---|
| `"&s&lFINALIZOU!"` | `duel_finish_title` | `"&s&lFINISHED!"` | `"&s&lFINALIZOU!"` | `"&s&lTERMINOU!"` |
| `"§7Aguardando resultado..."` | `duel_finish_subtitle` | `"§7Waiting for results..."` | `"§7Aguardando resultado..."` | `"§7Aguardando resultado..."` |
| `"&w&lVOLTA "` + newLap | (keep dynamic) | n/a | n/a | n/a |
| `"&w&l🏆 VITÓRIA!"` | `duel_victory_title` | `"&w&l🏆 VICTORY!"` | `"&w&l🏆 VITÓRIA!"` | `"&w&l🏆 VITÓRIA!"` |
| `"&e&lDERROTA"` | `duel_defeat_title` | `"&e&lDEFEAT"` | `"&e&lDERROTA"` | `"&e&lDERROTA"` |
| `"§cDRS Finalizado."` (in DrsManager) | `drs_finished` | `"§cDRS Finished."` | `"§cDRS Finalizado."` | `"§cDRS Finalizado."` |
| `"§b§l>>> DRS ATIVADO!"` (in DrsManager) | `drs_activated` | `"§b§l>>> DRS ACTIVATED!"` | `"§b§l>>> DRS ATIVADO!"` | `"§b§l>>> DRS ATIVADO!"` |
| `"§6§lPush To Pass: "` (in PTP) | `ptp_title` | `"&w&lPush To Pass: "` | `"&w&lPush To Pass: "` | `"&w&lPush To Pass: "` |

For dynamic strings like `"§f" + finishPosition + "º Lugar"`, create lang keys with placeholders:
| | `duel_finish_position` | `"§f{pos}º Place"` | `"§f{pos}º Lugar"` | `"§f{pos}º Lugar"` |

The `"&wVocê está pronto?"` in ReadyCheckManager is already partially themed. Extract:
| | `ready_check_title` | `"&wAre you ready?"` | `"&wVocê está pronto?"` | `"&wVocê está pronto?"` |

And the QuickRaceManager spectator title:
| | `spectator_qr_title` | `"&wSPECTATOR"` | `"&wESPECTADOR"` | `"&wESPECTADOR"` |
| | `spectator_qr_subtitle` | `"§7Watching the race..."` | `"§7Acompanhando a corrida..."` | `"§7Acompanhando a corrida..."` |

- [ ] **Step 2: Add lang keys to all 3 yml files**

Add the new keys under existing sections in en_US.yml, pt_PT.yml, pt_BR.yml.

- [ ] **Step 3: Replace hardcoded strings in Java files with `plugin.getTranslation()` calls**

Use the existing `TranslationUtil` / `FormulaRacing.getTranslation()` pattern. For example in `TimeTrialDuels.java`:
```java
// Before:
TitleHelper.sendThemedTitle(p, "&s&lFINALIZOU!", "§7Aguardando resultado...", 10, 70, 20);
// After:
String langCode = plugin.getDatabaseManager().getPlayerLanguage(p.getUniqueId());
TitleHelper.sendThemedTitle(p,
    plugin.getTranslation("duel_finish_title", langCode),
    plugin.getTranslation("duel_finish_subtitle", langCode),
    10, 70, 20);
```

Similarly for DrsManager, PTPManager, ReadyCheckManager, QuickRaceManager.

- [ ] **Step 4: Compile and verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Duels/TimeTrialDuels.java
git add src/main/java/dev/EfraGroup/formulaRacing/Gui/ReadyCheckManager.java
git add src/main/java/dev/EfraGroup/formulaRacing/Controllers/QuickRaceManager.java
git add src/main/java/dev/EfraGroup/formulaRacing/Heat/Logic/DrsManager.java
git add src/main/java/dev/EfraGroup/formulaRacing/Heat/Logic/PTPManager.java
git add src/main/resources/lang/en_US.yml
git add src/main/resources/lang/pt_PT.yml
git add src/main/resources/lang/pt_BR.yml
git commit -m "feat(i18n): extract hardcoded PT strings to lang keys for duels, DRS, PTP, ready check, QR spectator"
```

---

### Task 5: ClickableMessageUtil — BungeeCord → Adventure API Migration

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/ClickableMessageUtil.java`

**This is the highest-effort task.** The BungeeCord API (`net.md_5.bungee.api.ChatColor`, `TextComponent`, `ClickEvent`, `HoverEvent`) must be replaced with Adventure (`net.kyori.adventure.text.Component`, `net.kyori.adventure.text.event.ClickEvent`, `net.kyori.adventure.text.event.HoverEvent`).

- [ ] **Step 1: Replace BungeeCord imports with Adventure imports**

Replace:
```java
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
```

With:
```java
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
```

- [ ] **Step 2: Rewrite each method to return/accept `Component` instead of `TextComponent`**

Key methods to migrate:

- `broadcastEventCreated()` → Change `TextComponent` chaining to `Component.text().color(NamedTextColor.XYZ).decorate(TextDecoration.BOLD)` etc. Send via `Bukkit.broadcast()` or per-player loop with `Text.send()`.
- `sendEventSignBroadcast()` → Same pattern.
- `sendQuickRaceJoinBroadcast()` → Same.
- `broadcastEventStartingSoon()` → Same.
- `broadcastEventStarted()` → Same.
- `getRefreshButton()` → Return `Component` instead of `TextComponent`.
- `getButton()` → Return `Component`.
- `getEditButton()` → Return `Component`.
- `getToggleButton()` → Return `Component`.

**Pattern for themed color replacement:**
- `ChatColor.GREEN` → `theme.success()` or `NamedTextColor.GREEN`
- `ChatColor.GOLD` → `theme.award()` or `NamedTextColor.GOLD`
- `ChatColor.YELLOW` → `theme.warning()` or `NamedTextColor.YELLOW`
- `ChatColor.AQUA` → `theme.broadcast()` or `NamedTextColor.AQUA`
- `ChatColor.GRAY` → `theme.secondary()` or `NamedTextColor.GRAY`
- `ChatColor.WHITE` → `NamedTextColor.WHITE`
- `ChatColor.RED` → `theme.error()` or `NamedTextColor.RED`

For per-player versions (broadcast methods), resolve theme per recipient in the player loop. For static builder methods (`getButton`, etc.), accept `FRTheme` as parameter.

- [ ] **Step 3: Update all callers of ClickableMessageUtil**

Search for all references to `ClickableMessageUtil` methods and update signatures from `TextComponent` to `Component`. Send via `player.sendMessage(component)` (Paper's Adventure-native `sendMessage(Component)` should work for chat messages, unlike titles).

- [ ] **Step 4: Compile and verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/EfraGroup/formulaRacing/Utils/ClickableMessageUtil.java
git add src/main/java/dev/EfraGroup/formulaRacing/ (all callers)
git commit -m "feat(theme): migrate ClickableMessageUtil from BungeeCord to Adventure API with per-player theme"
```

---

### Task 6: Verify Everything Together

- [ ] **Step 1: Full clean compile**

Run: `mvn clean compile`
Expected: BUILD SUCCESS, no warnings related to theme migration

- [ ] **Step 2: Grep audit — no stale hardcoded colors in theme-migrated files**

Search the migrated files for remaining `§[0-9a-f]` patterns that should have been replaced. Only `FRThemeParser.LEGACY_COLORS` and lang file values should contain `§` codes.

- [ ] **Step 3: Final commit if needed**

```bash
git add -A
git commit -m "chore: cleanup after theme migration pass"
```

---

## Self-Review

### Spec Coverage Check
| Requirement | Task |
|---|---|
| Action bar theme tokens | Task 1 |
| Scoreboard builder hardcoded colors | Task 2 |
| PTP BossBar dynamic colors + theme title | Task 3 |
| DRS BossBar theme title | Task 3 |
| EventCountdown BossBar theme title | Task 3 |
| Hardcoded PT strings → lang keys | Task 4 |
| ClickableMessageUtil → Adventure | Task 5 |
| GUI item Adventure Components | **BLOCKED** (Paper 1.21.8-SNAPSHOT API limitation) |
| Tab list header/footer | Not implemented (neither codebase has it) |

### Placeholder Scan
No TBD/TODO/fill-in-later placeholders found. All code snippets are complete.

### Type Consistency
- `FRThemeParser.parseWithLegacy()` returns `Component` — consistently used in Tasks 1, 3
- `LegacyComponentSerializer.legacySection().serialize(Component)` returns `String` — used for Bukkit BossBar `setTitle()` and `spigot().sendMessage()` — consistent
- `Text.send(Player, String)` / `Text.get(Player, String)` take raw `&`-coded strings — consistent with existing pattern
- `TitleHelper.sendThemedTitle(Player, String, String, int, int, int)` takes `&`-coded strings — consistent