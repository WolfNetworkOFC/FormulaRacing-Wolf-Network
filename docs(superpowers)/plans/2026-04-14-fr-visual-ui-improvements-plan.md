# P0 Visual UI Improvements — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify the color system across all visual output (scoreboard, action bar, timer), add per-player compact scoreboard mode, and replace hardcoded i18n fallbacks with proper lang-file entries.

**Architecture:** A new `Utils/Theme/` package provides `FRTheme` (data holder), `FRThemeDefaults` (config-loaded defaults), `FRThemeResolver` (static player-theme lookup), and `FRThemeParser` (token → `Component` parser). `TimingScoreboardStyle` gets consistent position colors. All visual renderers (`BuilderSupport`, `RaceActionBarManager`, `TimerUtils`) migrate from raw `§` codes to theme-resolved `Component` via the parser.

**Tech Stack:** Kyori Adventure `TextColor`/`Component`, existing `TranslationUtil`, existing `fr_players.compactScoreboard` column (already exists), existing Megavex scoreboard adapter.

---

## File Map

### New Files
- `src/main/java/dev/EfraGroup/formulaRacing/Utils/Theme/FRTheme.java`
- `src/main/java/dev/EfraGroup/formulaRacing/Utils/Theme/FRThemeDefaults.java`
- `src/main/java/dev/EfraGroup/formulaRacing/Utils/Theme/FRThemeResolver.java`
- `src/main/java/dev/EfraGroup/formulaRacing/Utils/Theme/FRThemeParser.java`

### Modified Files
- `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/style/TimingScoreboardStyle.java` — align position colors
- `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/builder/BuilderSupport.java` — use FRThemeParser + TranslationUtil
- `src/main/java/dev/EfraGroup/formulaRacing/Utils/RaceActionBarManager.java` — use FRThemeParser for colors
- `src/main/java/dev/EfraGroup/formulaRacing/Utils/TimerUtils.java` — use FRThemeParser for colors
- `src/main/java/dev/EfraGroup/formulaRacing/Database/DatabaseManager.java` — add compact mode getter/setter
- `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/RaceScoreboardV2Manager.java` — pass compact flag to context
- `src/main/java/dev/EfraGroup/formulaRacing/Gui/SettingsMenu.java` — add compact toggle button
- `src/main/resources/lang/en_US.yml` — add scoreboard fallback entries
- `src/main/resources/lang/pt_PT.yml` — add scoreboard fallback entries
- `src/main/resources/lang/pt_BR.yml` — add scoreboard fallback entries
- `src/main/java/dev/EfraGroup/formulaRacing/Command/FRCommand.java` OR existing command file — add `/fr compact` toggle

---

## Task 1: Create Theme Foundation Classes

**Files:**
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/Theme/FRTheme.java`
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/Theme/FRThemeDefaults.java`
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/Theme/FRThemeResolver.java`
- Create: `src/main/java/dev/EfraGroup/formulaRacing/Utils/Theme/FRThemeParser.java`

- [ ] **Step 1: Create FRTheme.java**

```java
package dev.EfraGroup.formulaRacing.Utils.Theme;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.NamedTextColor;

@Getter
public class FRTheme {
    private final TextColor primary;
    private final TextColor secondary;
    private final TextColor success;
    private final TextColor warning;
    private final TextColor error;
    private final TextColor broadcast;
    private final TextColor award;
    private final TextColor title;
    private final TextColor info;
    private final TextColor accent;

    public FRTheme(TextColor primary, TextColor secondary, TextColor success,
                   TextColor warning, TextColor error, TextColor broadcast,
                   TextColor award, TextColor title, TextColor info, TextColor accent) {
        this.primary = primary;
        this.secondary = secondary;
        this.success = success;
        this.warning = warning;
        this.error = error;
        this.broadcast = broadcast;
        this.award = award;
        this.title = title;
        this.info = info;
        this.accent = accent;
    }

    public static FRTheme defaultTheme() {
        return new FRTheme(
                TextColor.color(0x7bf200),
                TextColor.color(NamedTextColor.WHITE),
                TextColor.color(0x7bf200),
                TextColor.color(NamedTextColor.YELLOW),
                TextColor.color(0xff7a75),
                TextColor.color(NamedTextColor.AQUA),
                TextColor.color(NamedTextColor.GOLD),
                TextColor.color(NamedTextColor.DARK_GRAY),
                TextColor.color(0xcc99ff),
                TextColor.color(0x00cc99)
        );
    }

    public Component label(String text) {
        return Component.text(text).color(primary);
    }

    public Component value(String text) {
        return Component.text(text).color(secondary);
    }

    public Component highlight(String text) {
        return Component.text(text).color(success);
    }

    public Component warn(String text) {
        return Component.text(text).color(warning);
    }

    public Component err(String text) {
        return Component.text(text).color(error);
    }

    public Component bracket(String text) {
        return Component.text("[").color(primary)
                .append(Component.text(text).color(secondary))
                .append(Component.text("]").color(primary));
    }

    public Component bracket(Component content) {
        return Component.text("[").color(primary)
                .append(content.color(secondary))
                .append(Component.text("]").color(primary));
    }
}
```

- [ ] **Step 2: Create FRThemeDefaults.java**

```java
package dev.EfraGroup.formulaRacing.Utils.Theme;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Plugin;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;

public class FRThemeDefaults {
    private static FRTheme instance;

    public static void load(FormulaRacing plugin) {
        FileConfiguration config = plugin.getConfig();
        instance = new FRTheme(
                parseHex(config.getString("theme.primary", "#7bf200")),
                parseHex(config.getString("theme.secondary", "#ffffff")),
                parseHex(config.getString("theme.success", "#7bf200")),
                parseHex(config.getString("theme.warning", "#ffff00")),
                parseHex(config.getString("theme.error", "#ff7a75")),
                parseHex(config.getString("theme.broadcast", "#00ffff")),
                parseHex(config.getString("theme.award", "#ffd700")),
                parseHex(config.getString("theme.title", "#555555")),
                parseHex(config.getString("theme.info", "#cc99ff")),
                parseHex(config.getString("theme.accent", "#00cc99"))
        );
    }

    public static FRTheme getDefaultTheme() {
        if (instance == null) {
            instance = FRTheme.defaultTheme();
        }
        return instance;
    }

    private static TextColor parseHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return TextColor.color(0x7bf200);
        }
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return TextColor.fromHexString("#" + clean);
        } catch (IllegalArgumentException e) {
            return TextColor.color(0x7bf200);
        }
    }
}
```

- [ ] **Step 3: Create FRThemeResolver.java**

```java
package dev.EfraGroup.formulaRacing.Utils.Theme;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FRThemeResolver {

    public static FRTheme resolveTheme(CommandSender sender) {
        if (sender instanceof Player player) {
            // Future: look up player-specific theme from DB
            // For now: always return default
            return FRThemeDefaults.getDefaultTheme();
        }
        return FRThemeDefaults.getDefaultTheme();
    }

    public static FRTheme resolveTheme(Player player) {
        return FRThemeDefaults.getDefaultTheme();
    }
}
```

- [ ] **Step 4: Create FRThemeParser.java**

```java
package dev.EfraGroup.formulaRacing.Utils.Theme;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

public class FRThemeParser {

    public static Component parse(String text, FRTheme theme) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        TextColor color = NamedTextColor.WHITE;
        List<TextDecoration> decorations = new ArrayList<>();
        Component result = Component.empty();

        String[] parts = text.split("&", -1);
        boolean first = true;

        for (String part : parts) {
            if (part.isEmpty()) {
                first = false;
                continue;
            }

            if (first) {
                result = result.append(Component.text(part));
                first = false;
                continue;
            }

            String option = part.substring(0, 1);
            String content = part.length() > 1 ? part.substring(1) : "";

            switch (option) {
                case "1" -> color = theme.getPrimary();
                case "2" -> color = theme.getSecondary();
                case "s" -> color = theme.getSuccess();
                case "w" -> color = theme.getWarning();
                case "e" -> color = theme.getError();
                case "b" -> color = theme.getBroadcast();
                case "a" -> color = theme.getAward();
                case "t" -> color = theme.getTitle();
                case "i" -> color = theme.getInfo();
                case "n" -> color = theme.getAccent();
                case "l" -> decorations.add(TextDecoration.BOLD);
                case "o" -> decorations.add(TextDecoration.ITALIC);
                case "r" -> {
                    decorations.clear();
                    color = NamedTextColor.WHITE;
                }
                default -> {
                    color = NamedTextColor.WHITE;
                    content = "&" + part;
                }
            }

            Component built = Component.text(content).color(color);
            for (TextDecoration dec : decorations) {
                built = built.decorate(dec);
            }
            result = result.append(built);
        }

        return result;
    }
}
```

- [ ] **Step 5: Initialize theme in FormulaRacing.onEnable()**

Find the `onEnable()` method in `FormulaRacing.java` and add near the end (before the leaderboard updater):
```java
FRThemeDefaults.load(this);
```

- [ ] **Step 6: Commit**

```
feat(theme): add FRTheme, FRThemeDefaults, FRThemeResolver, FRThemeParser

Foundation for unified color system across all visual output.
```

---

## Task 2: Align TimingScoreboardStyle Position Colors

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/style/TimingScoreboardStyle.java`

- [ ] **Step 1: Update positionColor() constants**

Replace the `positionColor(int pos)` method with hex-accurate constants matching TimingSystem:

```java
public static String positionColor(int pos) {
    return switch (pos) {
        case 1 -> "§6";  // gold/bronze
        case 2 -> "§f";  // white (silver shown as white with gray §7 prefix in rankTag)
        case 3 -> "§c";  // red/bronze accent
        default -> "§7"; // gray
    };
}
```

Also update `rankTag()` so that P2 uses `§7` (not white `§f`) for consistency with scoreboard convention:
```java
public static String rankTag(int pos, boolean fastestLap, boolean finished) {
    StringBuilder rank = new StringBuilder(positionColor(pos)).append(pos);
    if (pos < 10) {
        rank.append(' ');
    }
    if (fastestLap) {
        rank.append("§n");
    }
    if (finished) {
        rank.append("§o");
    }
    rank.append("§r");
    return rank.toString();
}
```

This aligns P2=P3=§7 gray in scoreboard. Note: the action bar will use theme-based colors separately in Task 4.

- [ ] **Step 2: Commit**

```
fix(scoreboard): align position colors in TimingScoreboardStyle

P2 uses §7 (gray) consistently. P1=gold §6, P2=gray §7, P3=red §c.
```

---

## Task 3: Update BuilderSupport — FRThemeParser + i18n Fallbacks

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/builder/BuilderSupport.java`
- Create: no new files, but will use existing TranslationUtil

**Note:** This task has two sub-parts: (A) use FRThemeParser for hardcoded color codes, (B) route fallbacks through TranslationUtil.

- [ ] **Step 1: Add imports to BuilderSupport**

Add to the top of `BuilderSupport.java`:
```java
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeDefaults;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import net.kyori.adventure.text.Component;
```

- [ ] **Step 2: Update createScoreboardFallbacks() — remove hardcoded strings, keep only lang keys**

In `BuilderSupport.java`, find the `createScoreboardFallbacks()` method. Change it so the map values are the lang keys (not the full strings). The actual string content will come from TranslationUtil at render time.

**Before** (lines 429-452):
```java
private static Map<String, String> createScoreboardFallbacks() {
    Map<String, String> fallback = new HashMap<>();
    fallback.put("scoreboard_title_practice", "§d§l FREE PRACTICE");
    fallback.put("scoreboard_title_qualifying", "§b§l QUALIFYING");
    // ... all hardcoded with § and text
```

**After:**
```java
private static Map<String, String> createScoreboardFallbacks() {
    Map<String, String> fallback = new HashMap<>();
    fallback.put("scoreboard_title_practice", "scoreboard_title_practice");
    fallback.put("scoreboard_title_qualifying", "scoreboard_title_qualifying");
    fallback.put("scoreboard_title_waiting", "scoreboard_title_waiting");
    fallback.put("scoreboard_title_race", "scoreboard_title_race");
    fallback.put("scoreboard_title_finished", "scoreboard_title_finished");
    fallback.put("scoreboard_v2_no_active_drivers", "scoreboard_v2_no_active_drivers");
    fallback.put("scoreboard_v2_best_lap", "scoreboard_v2_best_lap");
    fallback.put("scoreboard_v2_offline", "scoreboard_v2_offline");
    fallback.put("scoreboard_v2_drivers", "scoreboard_v2_drivers");
    fallback.put("scoreboard_v2_track", "scoreboard_v2_track");
    fallback.put("scoreboard_v2_laps", "scoreboard_v2_laps");
    fallback.put("scoreboard_v2_position", "scoreboard_v2_position");
    fallback.put("scoreboard_v2_time", "scoreboard_v2_time");
    fallback.put("scoreboard_status_dnf_short", "scoreboard_status_dnf_short");
    fallback.put("scoreboard_status_offline", "scoreboard_status_offline");
    fallback.put("scoreboard_status_in_pit", "scoreboard_status_in_pit");
    fallback.put("scoreboard_common_separator", "scoreboard_common_separator");
    fallback.put("scoreboard_common_footer", "scoreboard_common_footer");
    return fallback;
}
```

- [ ] **Step 3: Update tr() to use TranslationUtil + FRThemeParser**

Find the `tr(ScoreboardContext, String key, String... placeholders)` method in `BuilderSupport.java`. Currently it looks like:

```java
static String tr(ScoreboardContext context, String key, String... placeholders) {
    // calls plugin.getTranslation(key, lang, placeholders)
}
```

Add a new overloaded method or modify it to also parse color tokens:

```java
static String tr(ScoreboardContext context, String key, String... placeholders) {
    String lang = context.plugin().getTranslationUtil().getPlayerLanguage(context.viewer().getUniqueId());
    String translated = context.plugin().getTranslation(key, lang, placeholders);
    if (isMissingTranslation(translated)) {
        translated = applyFallback(key, placeholders);
    }
    return translated;
}

static Component trComponent(ScoreboardContext context, String key, String... placeholders) {
    String text = tr(context, key, placeholders);
    return FRThemeParser.parse(text, FRThemeDefaults.getDefaultTheme());
}
```

- [ ] **Step 4: Update applyFallback() to go through TranslationUtil + FRThemeParser**

Change `applyFallback()` so it:
1. Gets the lang key from the fallback map
2. Calls `TranslationUtil.getTranslated(langKey)` with the player's language
3. If still missing, uses a minimal hardcoded English fallback
4. Feeds result through `FRThemeParser.parse()`

```java
private static String applyFallback(String key, String... placeholders) {
    String langKey = SCOREBOARD_FALLBACKS.getOrDefault(key, key);
    String langCode = "en_US";
    // Try TranslationUtil if plugin is available
    String translated = langKey; // fallback to key itself
    try {
        FormulaRacing plugin = FormulaRacing.getInstance();
        if (plugin != null) {
            translated = plugin.getTranslation(langKey, langCode, placeholders);
        }
    } catch (Exception ignored) {
    }
    if (translated == null || translated.contains("[Lang Error]") || translated.equals(langKey)) {
        translated = MINIMAL_FALLBACK.getOrDefault(key, key);
        if (placeholders != null) {
            for (int i = 0; i < placeholders.length - 1; i += 2) {
                translated = translated.replace(placeholders[i], placeholders[i + 1]);
            }
        }
    }
    return translated;
}

private static final Map<String, String> MINIMAL_FALLBACK = Map.of(
    "scoreboard_title_practice", "&d&l FREE PRACTICE",
    "scoreboard_title_qualifying", "&b&l QUALIFYING",
    "scoreboard_title_waiting", "&6&l WAITING",
    "scoreboard_title_race", "&c&l RACE",
    "scoreboard_title_finished", "&a&l FINISHED",
    "scoreboard_v2_no_active_drivers", "&7No active drivers",
    "scoreboard_v2_best_lap", "&fBest lap: {time}",
    "scoreboard_v2_offline", "&7Offline",
    "scoreboard_v2_drivers", "&fDrivers: {count}",
    "scoreboard_v2_track", "&fTrack: {track}",
    "scoreboard_v2_laps", "&fLaps: {laps}",
    "scoreboard_v2_position", "&fPosition: {position}",
    "scoreboard_v2_time", "&fTime: {time}",
    "scoreboard_status_dnf_short", "DNF",
    "scoreboard_status_offline", "Offline",
    "scoreboard_status_in_pit", "In Pit",
    "scoreboard_common_separator", "&7------------------------",
    "scoreboard_common_footer", "&ewolfnetwork.com.br"
);
```

- [ ] **Step 5: Commit**

```
refactor(scoreboard): route all BuilderSupport fallbacks through TranslationUtil + FRThemeParser

- Fallback map now holds lang keys, not raw strings
- tr() uses TranslationUtil with player language
- MINIMAL_FALLBACK provides last-resort English strings
- Raw § codes moved to lang files
```

---

## Task 4: Update RaceActionBarManager — FRThemeParser

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/RaceActionBarManager.java`

- [ ] **Step 1: Add imports**

Add after existing imports:
```java
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeDefaults;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import net.kyori.adventure.text.Component;
```

- [ ] **Step 2: Add a helper method for building themed Components**

Add as a private method in `RaceActionBarManager`:

```java
private Component themed(String text) {
    return FRThemeParser.parse(text, FRThemeDefaults.getDefaultTheme());
}
```

- [ ] **Step 3: Replace hardcoded § position colors**

Find `getPositionColor()` (or where position color is hardcoded as `§a`, `§e`, `§6` etc.) and replace with a shared helper using `FRThemeDefaults`. The position colors in action bar (P1=§a green, P2=§e yellow, P3=§6 gold) should align with the shared `TimingScoreboardStyle.positionColor()` constants, but wrapped as `Component`:

```java
private String themedPositionColor(int pos) {
    return TimingScoreboardStyle.positionColor(pos);  // still returns § codes
}
```

Then in each action bar build method, wrap with `themed()`:
```java
// In buildRacingMessage() or wherever position color is applied:
String posColor = themedPositionColor(position);
String line = posColor + "...";
// Convert to Component via themed():
return themed(posColor + "&2 " + positionDisplay);
```

Actually, simpler approach: since `RaceActionBarManager` builds strings (not `Component`), the existing approach of using `§` codes in strings is fine for now, as long as the `§` values match `TimingScoreboardStyle`. The real migration is for `Component`-based rendering.

**Decision:** For `RaceActionBarManager`, just ensure `getPositionColor()` values match `TimingScoreboardStyle.positionColor()`. The manager already uses `ChatColor.translateAlternateColorCodes` for config-based progress bar colors. If any hardcoded `§` values differ from the scoreboard, align them.

Specifically, check:
- P1 should be `§6` (gold) — already correct in scoreboard, verify action bar
- P2 should be `§7` (gray) — check if action bar uses `§e` (yellow) or `§7`
- P3 should be `§c` (red) — check if action bar uses `§6` (gold)

Update any mismatching hardcoded values.

- [ ] **Step 4: Commit**

```
fix(actionbar): align position color codes with TimingScoreboardStyle

P1=§6 (gold), P2=§7 (gray), P3=§c (red) used consistently
across scoreboard and action bar.
```

---

## Task 5: Update TimerUtils — FRThemeParser

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/TimerUtils.java`

- [ ] **Step 1: Add imports and helper**

Add same imports as Task 4 and add the `themed()` helper.

- [ ] **Step 2: Find all hardcoded § color usages in TimerUtils**

Search for `§` in `TimerUtils.java`. Replace each occurrence with either:
- The `themed("&x...")` pattern if it needs per-player theme resolution
- Or keep raw `§` if it matches the theme defaults (e.g., progress colors that are the same for all players)

- [ ] **Step 3: Commit**

```
fix(timer): replace raw § color codes with FRThemeParser in TimerUtils
```

---

## Task 6: Add Scoreboard Lang File Entries

**Files:**
- Modify: `src/main/resources/lang/en_US.yml`
- Modify: `src/main/resources/lang/pt_PT.yml`
- Modify: `src/main/resources/lang/pt_BR.yml`

- [ ] **Step 1: Add entries to en_US.yml**

Find the end of `en_US.yml` and add the scoreboard entries before the final `---` or at the end of the file:

```yaml
scoreboard_v2_no_active_drivers: "&7No active drivers"
scoreboard_v2_best_lap: "&fBest lap: &2{time}"
scoreboard_v2_offline: "&7Offline"
scoreboard_v2_drivers: "&fDrivers: &2{count}"
scoreboard_v2_track: "&fTrack: &2{track}"
scoreboard_v2_laps: "&fLaps: &2{laps}"
scoreboard_v2_position: "&fPosition: &2{position}"
scoreboard_v2_time: "&fTime: &2{time}"
scoreboard_status_dnf_short: "DNF"
scoreboard_status_offline: "Offline"
scoreboard_status_in_pit: "In Pit"
scoreboard_title_practice: "&d&l FREE PRACTICE"
scoreboard_title_qualifying: "&b&l QUALIFYING"
scoreboard_title_waiting: "&6&l WAITING"
scoreboard_title_race: "&c&l RACE"
scoreboard_title_finished: "&a&l FINISHED"
scoreboard_common_separator: "&7------------------------"
scoreboard_common_footer: "&ewolfnetwork.com.br"
```

- [ ] **Step 2: Add entries to pt_PT.yml and pt_BR.yml**

Use the same keys with Portuguese translations (matching the existing hardcoded fallbacks in BuilderSupport):
- `"&7Sem pilotos ativos"`, `"&fMelhor volta: &2{time}"`, `"&7Offline"`, `"&fPilotos: &2{count}"`, `"&fPista: &2{track}"`, `"&fVoltas: &2{laps}"`, `"&fPosição: &2{position}"`, `"&fTempo: &2{time}"`, `"DNF"`, `"Offline"`, `"No Pit"`, `"&d&l TREINO LIVRE"`, `"&b&l QUALIFICAÇÃO"`, `"&6&l AGUARDANDO"`, `"&c&l CORRIDA"`, `"&a&l TERMINADO"`, `"&7------------------------"`, `"&ewolfnetwork.com.br"`

- [ ] **Step 3: Commit**

```
feat(i18n): add all scoreboard fallback strings to lang files

en_US, pt_PT, pt_BR now have keys for scoreboard titles, status,
and fallback messages that were previously hardcoded in BuilderSupport.
```

---

## Task 7: Add Compact Scoreboard Methods to DatabaseManager

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Database/DatabaseManager.java`

- [ ] **Step 1: Add getPlayerCompactMode()**

Find `getTimeTrialEnabled()` method (around line 1254) and add the compact mode getter after it:

```java
public synchronized boolean getPlayerCompactMode(UUID uuid) {
    if (compactScoreboardCache == null) {
        compactScoreboardCache = new ConcurrentHashMap<>();
    }
    if (compactScoreboardCache.containsKey(uuid)) {
        return compactScoreboardCache.get(uuid);
    }

    String sql = "SELECT compactScoreboard FROM fr_players WHERE uuid = ?";
    boolean compact = false;

    try {
        Connection conn = getOrConnect();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    compact = rs.getInt("compactScoreboard") == 1;
                }
            }
        }
    } catch (SQLException e) {
        handleSqlError(e);
    }

    compactScoreboardCache.put(uuid, compact);
    return compact;
}
```

- [ ] **Step 2: Add setPlayerCompactMode()**

```java
public synchronized void setPlayerCompactMode(UUID uuid, boolean compact) {
    String sql = "UPDATE fr_players SET compactScoreboard = ? WHERE uuid = ?";
    try {
        Connection conn = getOrConnect();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, compact ? 1 : 0);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            if (compactScoreboardCache == null) {
                compactScoreboardCache = new ConcurrentHashMap<>();
            }
            compactScoreboardCache.put(uuid, compact);
        }
    } catch (SQLException e) {
        handleSqlError(e);
    }
}
```

- [ ] **Step 3: Add compactScoreboardCache field**

Add to the cache fields at the top of `DatabaseManager`:
```java
private final Map<UUID, Boolean> compactScoreboardCache = new ConcurrentHashMap<>();
```

- [ ] **Step 4: Commit**

```
feat(settings): add getPlayerCompactMode and setPlayerCompactMode to DatabaseManager

Player preference for compact scoreboard layout persisted in fr_players table.
```

---

## Task 8: Wire Compact Mode Into ScoreboardV2Manager

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Utils/scoreboard/v2/RaceScoreboardV2Manager.java`

- [ ] **Step 1: Find addPlayer() and pass compact mode to context**

Find the `addPlayer(Player player, Heats heat)` method. Where it creates `ScoreboardContext`, add the compact flag:

```java
boolean compact = plugin.getDm().getPlayerCompactMode(player.getUniqueId());
ScoreboardContext context = new ScoreboardContext(
        plugin, heat, player, viewerDriver, spectator,
        sortedDrivers, maxRows, compact  // <-- add compact here
);
```

- [ ] **Step 2: Verify ScoreboardContext constructor accepts compact**

Check `ScoreboardContext.java` — it already has a `boolean compact` field and constructor parameter.

- [ ] **Step 3: Update BuilderSupport for compact mode**

In `BuilderSupport.java`, find the methods that build scoreboard lines. The compact effects should be applied in the builder methods. Specifically:

**`padName(String name, boolean compact)`** — if compact, truncate/pad to 4 chars instead of 13:
```java
static String padName(String name, boolean compact) {
    int width = compact ? 4 : 13;
    return padRight(name, width);
}
```

**In `commonSeparator()` and `commonFooter()`** — skip these when compact:
```java
static String commonSeparator(ScoreboardContext context) {
    if (context.compact()) return spacer(0);
    return tr(context, "scoreboard_common_separator");
}
```

**In `scoreboardTitle()`** — truncate to 8 chars when compact:
```java
static String scoreboardTitle(ScoreboardContext context, String defaultKey) {
    String title = tr(context, defaultKey);
    if (context.compact() && title.length() > 8) {
        title = title.substring(0, 8);
    }
    return title;
}
```

- [ ] **Step 4: Commit**

```
feat(scoreboard): wire compact mode from player settings to ScoreboardContext

- addPlayer() reads getPlayerCompactMode() and passes to context
- BuilderSupport respects compact flag: shorter names, no footer/separator,
  truncated title
```

---

## Task 9: Add Compact Toggle to SettingsMenu GUI

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/Gui/SettingsMenu.java`

- [ ] **Step 1: Add compact mode toggle button**

Find the `setupContent()` method. Add a new toggle button at slot 13 (center) alongside the existing time trial toggle, or replace the NOTE_BLOCK (sounds) button at slot 15:

```java
boolean compact = this.dm.getPlayerCompactMode(player.getUniqueId());
String compactName = this.plugin.getTranslation("gui_settings_compact_name", langCode, new String[0]);
this.addToggle(15, Material.MAP, compactName, compact, player, (event) -> {
    Player p = (Player)event.getWhoClicked();
    boolean newState = !this.dm.getPlayerCompactMode(p.getUniqueId());
    this.dm.setPlayerCompactMode(p.getUniqueId(), newState);
    this.setupContent(p);
    String stateStr = newState
            ? this.plugin.getTranslation("gui_settings_status_enabled", langCode, new String[0])
            : this.plugin.getTranslation("gui_settings_status_disabled", langCode, new String[0]);
    p.sendMessage(this.plugin.getTranslation("gui_settings_compact_toggled", langCode, new String[]{"{state}", stateStr}));
});
```

Note: `SettingsMenu` uses decompiled source. If `addToggle` method signature doesn't match exactly, adapt accordingly. The pattern from the existing time trial toggle (lines 47-54) should be followed.

- [ ] **Step 2: Commit**

```
feat(gui): add compact scoreboard toggle to SettingsMenu

Toggle button at slot 15 (MAP icon) persists to fr_players.compactScoreboard.
```

---

## Task 10: Add `/fr compact` Command

**Files:**
- Modify: existing command handler (likely `RaceCommand.java` or a new subcommand in the existing ACF command structure)

- [ ] **Step 1: Add compact subcommand**

Find the ACF command registration in `FormulaRacing.java` or the command class that handles `/fr`. Add a subcommand:

```java
@Subcommand("compact")
public void onCompact(Player player) {
    DatabaseManager dm = plugin.getDm();
    boolean current = dm.getPlayerCompactMode(player.getUniqueId());
    boolean newState = !current;
    dm.setPlayerCompactMode(player.getUniqueId(), newState);
    String lang = dm.getPlayerLanguage(player.getUniqueId());
    String state = newState
            ? plugin.getTranslation("gui_settings_status_enabled", lang)
            : plugin.getTranslation("gui_settings_status_disabled", lang);
    player.sendMessage(plugin.getTranslation("gui_settings_compact_toggled", lang, "{state}", state));
}
```

- [ ] **Step 2: Add lang keys for the command messages**

In all three lang files, add:
```yaml
gui_settings_compact_name: "&fCompact Scoreboard"
gui_settings_compact_toggled: "&7Compact mode: &f{state}"
gui_settings_status_enabled: "ENABLED"
gui_settings_status_disabled: "DISABLED"
```

(Note: `gui_settings_status_enabled` and `gui_settings_status_disabled` may already exist from the time trial toggle — if so, reuse them.)

- [ ] **Step 3: Commit**

```
feat(command): add /fr compact toggle command

Allows players to toggle compact scoreboard mode from chat.
```

---

## Task 11: Initialize FRThemeDefaults in FormulaRacing.onEnable()

**Files:**
- Modify: `src/main/java/dev/EfraGroup/formulaRacing/FormulaRacing.java`

- [ ] **Step 1: Call FRThemeDefaults.load() in onEnable()**

Find the end of `onEnable()` method. Add before any visual subsystem initialization:
```java
FRThemeDefaults.load(this);
```

This must happen before any scoreboard/action bar rendering occurs.

- [ ] **Step 2: Commit**

```
feat(theme): initialize FRThemeDefaults from config in onEnable

Theme defaults loaded from config.yml before any visual rendering.
```

---

## Spec Coverage Checklist

| Spec Section | Tasks |
|---|---|
| Theme system (FRTheme + FRThemeParser) | Task 1, 11 |
| Position color alignment | Task 2 |
| BuilderSupport i18n + FRThemeParser | Task 3, 6 |
| RaceActionBarManager color alignment | Task 4 |
| TimerUtils color migration | Task 5 |
| Compact mode (DB + context + builder + GUI + command) | Tasks 7, 8, 9, 10 |

All spec sections are covered. No gaps.
