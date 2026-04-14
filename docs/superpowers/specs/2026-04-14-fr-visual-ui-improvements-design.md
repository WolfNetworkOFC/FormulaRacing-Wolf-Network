# P0 Visual Interface Improvements — Design Spec

**Date:** 2026-04-14
**Status:** Approved
**Depends on:** None (this spec is self-contained)

---

## 1. Theme System (`FRTheme`)

### Goal
Create a unified, per-player color/theme abstraction — matching TimingSystem's `Theme` + `MessageParser` pattern — to replace all raw `§` section codes scattered throughout the codebase.

### New Files

#### `Utils/Theme/FRTheme.java`
Immutable data class (Lombok `@Getter` + constructor) holding 10 named `TextColor` fields:

| Field | Default | Purpose |
|-------|---------|---------|
| `primary` | `TextColor.color(0x7bf200)` | Labels, dividers |
| `secondary` | `TextColor.color(NamedTextColor.WHITE)` | Values, content |
| `success` | `TextColor.color(0x7bf200)` | Positive gaps, deltas |
| `warning` | `TextColor.color(NamedTextColor.YELLOW)` | Warnings |
| `error` | `TextColor.color(0xff7a75)` | Errors, negative |
| `broadcast` | `TextColor.color(NamedTextColor.AQUA)` | Broadcasts |
| `award` | `TextColor.color(NamedTextColor.GOLD)` | Awards/highlights |
| `title` | `TextColor.color(NamedTextColor.DARK_GRAY)` | Titles |
| `info` | `TextColor.color(0xcc99ff)` | Informational |
| `accent` | `TextColor.color(0x00cc99)` | Accents, markers |

Style helper methods (all return `Component`):
- `label(String)` → primary color
- `value(String)` → secondary color
- `highlight(String)` → success color
- `warn(String)` → warning color
- `err(String)` → error color
- `bracket(String)` → `[text]` with primary brackets, secondary content
- `bracket(Component)` → same for pre-built components
- `spacer()` → thin colored bar using primary

#### `Utils/Theme/FRThemeDefaults.java`
Holds the global default theme values loaded from `config.yml`. Provides:
- `loadDefaults(FormulaRacing plugin)` — reads `theme.*` keys from config
- `getDefaultTheme()` — returns the default `FRTheme` instance for console/non-player contexts

#### `Utils/Theme/FRThemeResolver.java`
Static utility:
- `resolveTheme(Player)` — looks up player-specific theme override from DB (future), falls back to `FRThemeDefaults.getDefaultTheme()`
- For now: always returns default theme; player-specific overrides are a future P1 item

#### `Utils/Theme/FRThemeParser.java`
Parses `&` color tokens in strings and produces `Component`:
- `parse(String text, FRTheme theme)` → `Component`
- Token mapping: `&1`→primary, `&2`→secondary, `&s`→success, `&w`→warning, `&e`→error, `&b`→broadcast, `&a`→award, `&t`→title, `&i`→info, `&n`→accent
- Decorations: `&l`=bold, `&o`=italic, `&n`=underline, `&r`=reset (resets color to white + clears decorations)
- Unrecognized tokens passed through literally

### Modified Files

#### `Utils/scoreboard/style/TimingScoreboardStyle.java`
Refactor `positionColor(int pos)` to return hex color strings (consistent with theme defaults):
- P1 → `"&#cd7f32"` (bronze/gold), P2 → `"&#c3c3c3"` (silver), P3 → `"&#cd7f32"` (bronze), else → `"&#ffffff"` (white)

`rankTag()` remains but delegates to theme for decoration colors.

#### `Utils/scoreboard/v2/builder/BuilderSupport.java`
- Replace all raw `§` hardcoded strings with calls to `FRThemeParser.parse(key, theme)` for the fallbacks path
- Replace `applyFallback()` to use `TranslationUtil` + `FRThemeParser` instead of raw strings
- The fallback map becomes: key → i18n lang key (e.g., `"scoreboard_v2_no_active_drivers"` → `"scoreboard_v2_no_active_drivers"`)

#### `Utils/RaceActionBarManager.java`
- Replace hardcoded `§` codes in message formatting with theme-resolved `Component` via `FRThemeParser`
- Progress bar colors already read from config — keep as-is but optionally feed through theme

#### `Utils/TimerUtils.java`
- Same pattern: replace raw `§` codes with `FRThemeParser` calls

### Config Changes

Add to `config.yml`:
```yaml
theme:
  primary: "#7bf200"
  secondary: "#ffffff"
  success: "#7bf200"
  warning: "#ffff00"
  error: "#ff7a75"
  broadcast: "#00ffff"
  award: "#ffd700"
  title: "#555555"
  info: "#cc99ff"
  accent: "#00cc99"
```

---

## 2. Compact Scoreboard Mode

### Goal
Allow players to toggle a compact scoreboard layout (shorter names, fewer dividers, smaller title) for better visibility on small screens.

### Changes

#### `DatabaseManager`
Add methods:
- `getPlayerCompactMode(UUID uuid)` → `boolean` (default `false`)
- `setPlayerCompactMode(UUID uuid, boolean value)` → `void`
- New column: `player_settings.compact_scoreboard BOOLEAN DEFAULT 0`

#### `ScoreboardContext` (model)
- Already has `boolean compact` field — ensure it flows correctly from player settings

#### `ScoreboardV2Manager.addPlayer()`
Resolve player's compact mode from DB and pass it to `ScoreboardContext`:
```java
boolean compact = plugin.getDm().getPlayerCompactMode(player.getUniqueId());
```

#### `BuilderSupport`
Compact mode effects:
- **Name padding:** `padName()` uses 4 chars instead of 13
- **Dividers:** `|` separator between position and name is omitted
- **Title:** `scoreboardTitle()` truncates to 8 chars
- **Footer:** `commonFooter()` is omitted
- **Separator:** `commonSeparator()` is omitted
- **Status text:** shortened (e.g., `"DNF"` instead of `"Did Not Finish"`)

#### `SettingsMenu` GUI
Add toggle button:
- Item: `MapItem.STONE_BUTTON` with name `"Compact Scoreboard"` and lore showing ON/OFF state
- Click: calls `DatabaseManager.setPlayerCompactMode()`, refreshes the GUI

#### New command: `/fr compact`
- Toggles compact mode for the player
- Sends confirmation message: `"Compact mode: ENABLED/DISABLED"`

---

## 3. i18n Fallbacks → Proper Lang Files

### Goal
Eliminate hardcoded Portuguese/English strings and `§` color codes from `BuilderSupport.createScoreboardFallbacks()`, moving them to the lang files with proper `&` token support.

### Changes

#### `src/main/resources/lang/en_US.yml`, `pt_PT.yml`, `pt_BR.yml`
Add entries for all scoreboard fallback strings:
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

#### `BuilderSupport.applyFallback()`
- Change to call `TranslationUtil.getTranslated(key)` for each fallback key
- Feed result through `FRThemeParser.parse()` to resolve `&` tokens with player theme colors
- If translation returns missing-key placeholder (`[Lang Error]`), fall back to raw English string from a minimal hardcoded map (English only — last resort, never in prod)

#### `BuilderSupport.createScoreboardFallbacks()`
- Keep map of key → lang key for reference, but all rendering goes through `TranslationUtil`
- The fallback map values become just the lang keys (not the strings themselves)
- Or: remove the map entirely and call `TranslationUtil.getTranslated(key, langCode)` directly in `applyFallback`

---

## Order of Implementation

1. **FRTheme + FRThemeParser + FRThemeDefaults + FRThemeResolver** — foundation
2. **Update `TimingScoreboardStyle`** — position colors use theme defaults
3. **Update `BuilderSupport`** — uses `FRThemeParser` for all color codes
4. **Update `RaceActionBarManager`** — uses `FRThemeParser` for colors
5. **Update `TimerUtils`** — uses `FRThemeParser` for colors
6. **Lang files** — add all scoreboard fallback entries with `&` tokens
7. **BuilderSupport i18n integration** — connect to TranslationUtil
8. **Database compact mode columns/ethods**
9. **Compact mode in builders and context**
10. **GUI toggle + command**

---

## Backward Compatibility

- All existing lang keys remain unchanged — `TranslationUtil.getTranslation()` continues to work
- `ScoreboardViewModel` continues to use `List<String>` (legacy section-code strings) until adapters are updated
- The `MegavexAdapter` already uses `LegacyComponentSerializer.legacySection()` to convert `§` strings to Adventure `Component` — this remains the rendering path
- Config `theme.*` keys are optional; if absent, defaults in `FRThemeDefaults` are used

## Color Inconsistency Fix

This also fixes the P0 inconsistency where position colors differed between scoreboard and action bar:
- `TimingScoreboardStyle.positionColor()` returns `"&#cd7f32"` (bronze) for P1, `"&#c3c3c3"` (silver) for P2, `"&#cd7f32"` (bronze) for P3, `"&#ffffff"` for others
- `RaceActionBarManager.getPositionColor()` will use the same constants once refactored through `FRThemeParser`
