# FormulaRacing REST API

## 1. Overview

FormulaRacing exposes an embedded JSON REST API for server dashboards, live race views, and external integrations. The API is implemented with Spark Java and is managed by `dev.EfraGroup.formulaRacing.Api.ApiManager`.

The API is read-only except for the authentication-check endpoint. It does not provide public endpoints for creating, updating, or deleting tracks, players, events, leagues, or races.

## 2. Base URL and Transport

The default base URL is:

```text
http://143.14.179.72:10003
```

The host and port are deployment-dependent. The port is configured by `port` in `api_config.yml`.

All API responses are JSON. The global request filter sets the response content type to `application/json` for API requests.

The dashboard directory is created in the plugin data folder on first startup. Bundled dashboard resources are copied there when the directory does not already exist.

## 3. API Surface

All registered API routes use `GET`.

| Area | Endpoint |
|---|---|
| Health and administration | `GET /api/v1/admin/ping` |
| Server status | `GET /api/v1/readonly/status` |
| Tracks | `GET /api/v1/readonly/tracks` |
| Track details | `GET /api/v1/readonly/tracks/:trackname` |
| Track times | `GET /api/v1/readonly/tracks/:trackname/times` |
| Online players | `GET /api/v1/readonly/players` |
| Player collection | `GET /api/v1/readonly/players/all` |
| Player details | `GET /api/v1/readonly/players/:uuidorusername` |
| Player time trial record | `GET /api/v1/readonly/players/:uuid/timetrials/:trackname` |
| Track medal times | `GET /api/v1/readonly/leaderboard/:trackname` |
| Leagues | `GET /api/v1/readonly/leagues` |
| League details | `GET /api/v1/readonly/leagues/:name` |
| Driver standings | `GET /api/v1/readonly/leagues/:name/standings/drivers` |
| Team standings | `GET /api/v1/readonly/leagues/:name/standings/teams` |
| League calendar | `GET /api/v1/readonly/leagues/:name/calendar` |
| League team | `GET /api/v1/readonly/leagues/:name/team/:team` |
| League categories | `GET /api/v1/readonly/leagues/:name/categories` |
| Duel leaderboard | `GET /api/v1/readonly/duels/leaderboard` |
| Duel player statistics | `GET /api/v1/readonly/duels/players/:uuidorusername` |
| Duel match history | `GET /api/v1/readonly/duels/players/:uuidorusername/matches` |
| Daily race | `GET /api/v1/readonly/dailyrace` |
| Latest daily race results | `GET /api/v1/readonly/dailyrace/results/latest` |
| Daily race statistics | `GET /api/v1/readonly/dailyrace/stats` |
| Player duel statistics | `GET /api/v1/readonly/dailyrace/stats/:uuidorusername` |
| Daily race exclusions | `GET /api/v1/readonly/dailyrace/excluded-tracks` |
| Event results | `GET /api/v1/readonly/events/results/:eventname` |
| Live positions | `GET /api/v1/readonly/live/positions` |
| Live events | `GET /api/v1/readonly/live/events` |
| Activity statistics | `GET /api/v1/readonly/activity/stats` |
| Recent activity | `GET /api/v1/readonly/activity/recent` |
| Events | `GET /api/v1/readonly/events` |
| API/system logs | `GET /api/v1/readonly/logs` |

## 4. Configuration

The API reads its runtime configuration from `api_config.yml` in the FormulaRacing plugin data folder. The bundled resource is copied there on first startup.

### 4.1 Configuration fields

| Field | Default | Description |
|---|---:|---|
| `port` | `10003` | HTTP port used by the embedded server. |
| `enable_cors` | `true` | Enables CORS response headers. |
| `rate_limit.enabled` | `true` | Enables request limiting. |
| `rate_limit.requests_per_minute` | `60` | Maximum number of recent requests allowed by the current limiter. |
| `log_requests` | `true` | Enables selected request-count logging in endpoint handlers. |
| `log_errors` | `true` | Enables error logging for the global exception handler. |
| `connection_timeout` | `30000` | Present in the configuration file, but not currently read or enforced by `ApiManager`. |
| `max_request_size` | `1048576` | Present in the configuration file, but not currently enforced by `ApiManager`. |
| `auth.enabled` | `false` | Enables token protection for administrative routes and, optionally, logs. |
| `auth.token` | `""` | Legacy single API token. |
| `auth.tokens` | `[]` | Named tokens for multiple integrations. |
| `auth.protect_logs` | `false` | Requires a token for the logs endpoint when authentication is enabled. |

The API server uses a fixed Spark thread pool of 8 threads, a minimum of 2 threads, and a 30,000 ms idle timeout.

### 4.2 Example configuration

```yaml
port: 10003
enable_cors: true

rate_limit:
  enabled: true
  requests_per_minute: 60

log_requests: true
log_errors: true
connection_timeout: 30000
max_request_size: 1048576

auth:
  enabled: false
  token: ""
  tokens: []
  protect_logs: false
```

Changing `port` by reloading the configuration does not rebind an already running Spark server. Restart the plugin or server for a port change to take effect.

## 11. Server Status

### 11.1 `GET /api/v1/readonly/status`

Returns server, plugin, track, event, and active-heat counters.

#### Response

```json
{
  "status": "online",
  "version": "2.0",
  "server": "Paper",
  "players_online": 12,
  "max_players": 100,
  "total_tracks": 8,
  "active_heats": 2,
  "total_events": 15
}
```

| Field | Type | Description |
|---|---|---|
| `status` | string | Current API status, normally `"online"`. |
| `version` | string | FormulaRacing plugin version. |
| `server` | string | Bukkit server implementation name. |
| `players_online` | integer | Current online player count. |
| `max_players` | integer | Configured maximum player count. |
| `total_tracks` | integer | Number of tracks returned by the track database. |
| `active_heats` | integer | Heats in `STARTING` or `RACING` state. |
| `total_events` | integer | Total number of events known to the event manager. |

## 12. Tracks

### 12.1 `GET /api/v1/readonly/tracks`

Returns the available track collection.

#### Response

```json
{
  "number": 2,
  "tracks": [
    {
      "name": "Volcano Circuit",
      "world": "race_world",
      "creator": "Builder",
      "checkpoints": 24,
      "icon": "BLUE_ICE"
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `number` | integer | Number of tracks. This field is named `number`, not `total`. |
| `tracks` | array | Track summaries. |
| `tracks[].name` | string | Display name of the track. |
| `tracks[].world` | string | World containing the track. |
| `tracks[].creator` | string | Track owner or creator name. |
| `tracks[].checkpoints` | integer | Configured checkpoint count. |
| `tracks[].icon` | string | Material/icon name, or the string `"null"` when unavailable. |

### 12.2 `GET /api/v1/readonly/tracks/:trackname`

Returns detailed information about one track.

#### Path parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `trackname` | string | yes | Track display name or database track name. URL-encode spaces. |

#### Response

```json
{
  "name": "Volcano Circuit",
  "world": "race_world",
  "creator": "Builder",
  "checkpoints": 24,
  "icon": "BLUE_ICE",
  "spawn": {
    "x": 120.5,
    "y": 64.0,
    "z": -240.25,
    "yaw": 90.0,
    "pitch": 0.0,
    "world": "race_world"
  },
  "medals": {
    "gold": "58.421",
    "silver": "61.903",
    "bronze": "66.120"
  },
  "record": 57123.0
}
```

| Field | Type | Description |
|---|---|---|
| `name` | string | Track name. |
| `world` | string | Track world. |
| `creator` | string | Creator name. |
| `checkpoints` | integer | Checkpoint count. |
| `icon` | string | Icon/material name. |
| `spawn` | object | Spawn location. An empty object is returned if no spawn is available. |
| `spawn.x` | number | X coordinate. |
| `spawn.y` | number | Y coordinate. |
| `spawn.z` | number | Z coordinate. |
| `spawn.yaw` | number | Yaw in degrees. |
| `spawn.pitch` | number | Pitch in degrees. |
| `spawn.world` | string | Spawn world name. |
| `medals` | object | Medal rank names mapped to time strings in seconds. The current database query returns at most gold, silver, and bronze. |
| `record` | number | Best finished track record in milliseconds, or `0` when no record exists. |

A missing track returns HTTP `404`.

### 12.3 `GET /api/v1/readonly/tracks/:trackname/times`

Returns the best stored attempt for each player on a track.

#### Path parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `trackname` | string | yes | Track name. URL-encode spaces. |

#### Response

```json
{
  "track": "Volcano Circuit",
  "total": 2,
  "times": [
    {
      "player_name": "RacerOne",
      "time": "00:57.123",
      "time_ms": 57123,
      "checkpoints": 24,
      "finished": true,
      "date": 1720000000000
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `track` | string | Requested track name. |
| `total` | integer | Number of returned player records. |
| `times` | array | Best stored attempt per player. |
| `times[].player_name` | string | Player name stored with the attempt. |
| `times[].time` | string | Formatted time in `MM:SS:mmm`. |
| `times[].time_ms` | integer | Time in milliseconds. |
| `times[].checkpoints` | integer | Checkpoints reached for the stored attempt. |
| `times[].finished` | boolean | Whether the stored attempt finished the track. |
| `times[].date` | integer | Record creation time in Unix epoch milliseconds. |

The database query orders finished attempts before unfinished attempts, then applies the stored ranking rules. There is no query-string limit for this endpoint.

## 13. Players

### 13.1 `GET /api/v1/readonly/players`

Returns currently online players and their FormulaRacing settings.

#### Response

```json
{
  "total": 2,
  "players": [
    {
      "uuid": "01234567-89ab-cdef-0123-456789abcdef",
      "name": "RacerOne",
      "online": true,
      "language": "en_US",
      "color1": "#FF0000",
      "color2": "#0000FF",
      "boat_type": 1,
      "scoreboard": true,
      "compact_mode": false
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `total` | integer | Number of online players. |
| `players` | array | Online player objects. |
| `players[].uuid` | string | Canonical player UUID. |
| `players[].name` | string | Current player name. |
| `players[].online` | boolean | Always `true` in this endpoint. |
| `players[].language` | string | Stored language setting, or `"en"` when unavailable. |
| `players[].color1` | string | Primary color setting, or `#FFFFFF` when unavailable. |
| `players[].color2` | string | Secondary color setting, or `#FFFFFF` when unavailable. |
| `players[].boat_type` | integer | Stored boat type identifier. |
| `players[].scoreboard` | boolean | Time-trial scoreboard setting. |
| `players[].compact_mode` | boolean | Compact display-mode setting. |

### 13.2 `GET /api/v1/readonly/players/all`

Returns the player collection currently available to the endpoint.

Despite the route name and source comment, the current implementation returns only online players. Offline players are not loaded from the database by this endpoint.

The response schema is the same as `GET /api/v1/readonly/players`.

### 13.3 `GET /api/v1/readonly/players/:uuidorusername`

Returns a player's identity and FormulaRacing settings.

#### Path parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `uuidorusername` | string | yes | Canonical UUID or player username. |

The username path is resolved through Bukkit's offline-player lookup. The player must have played on the server before; otherwise the endpoint returns HTTP `404`.

#### Response

```json
{
  "uuid": "01234567-89ab-cdef-0123-456789abcdef",
  "name": "RacerOne",
  "online": true,
  "language": "en_US",
  "color1": "#FF0000",
  "color2": "#0000FF",
  "boat_type": 1,
  "scoreboard": true,
  "compact_mode": false
}
```

The field definitions are identical to the player objects returned by the collection endpoint, except that `online` reflects the player's current connection state.

### 13.4 `GET /api/v1/readonly/players/:uuid/timetrials/:trackname`

Returns a player's best finished time and rank on a track.

#### Path parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `uuid` | string | yes | Canonical player UUID. |
| `trackname` | string | yes | Track name. URL-encode spaces. |

A malformed UUID returns HTTP `400`.

#### Response

```json
{
  "uuid": "01234567-89ab-cdef-0123-456789abcdef",
  "track": "Volcano Circuit",
  "best_time": 57123.0,
  "rank": 1
}
```

| Field | Type | Description |
|---|---|---|
| `uuid` | string | Requested player UUID. |
| `track` | string | Requested track name. |
| `best_time` | number | Best finished time in milliseconds, or `0` when no finished time exists. |
| `rank` | integer | Track rank, or `0` when the player has no finished time. |

### 13.5 `GET /api/v1/readonly/leaderboard/:trackname`

Returns the track's top medal thresholds.

Despite the endpoint name, this route does not return a full player leaderboard. It returns the best finished time for up to three players and labels them as gold, silver, and bronze.

#### Path parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `trackname` | string | yes | Track name. URL-encode spaces. |

#### Response

```json
{
  "track": "Volcano Circuit",
  "total_ranks": 3,
  "ranks": [
    {
      "medal": "gold",
      "time": "57.123"
    },
    {
      "medal": "silver",
      "time": "59.840"
    },
    {
      "medal": "bronze",
      "time": "62.105"
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `track` | string | Requested track name. |
| `total_ranks` | integer | Number of returned rank entries, normally no more than three. |
| `ranks` | array | Medal/time entries. |
| `ranks[].medal` | string | `gold`, `silver`, or `bronze`. |
| `ranks[].time` | string | Best time in seconds as stored by the database. |

## 14. Leagues

### 14.1 `GET /api/v1/readonly/leagues`

Returns a summary of every league.

#### Response

```json
{
  "leagues": [
    {
      "id": 1,
      "name": "Season 1",
      "status": "RUNNING",
      "team_mode": "MAIN_RESERVE",
      "scoring_system": "F1",
      "teams": 4,
      "drivers": 12
    }
  ],
  "total": 1
}
```

| Field | Type | Description |
|---|---|---|
| `leagues` | array | League summaries. |
| `leagues[].id` | integer | League ID. |
| `leagues[].name` | string | League name. |
| `leagues[].status` | string | League state enum: `SETUP`, `RUNNING`, or `FINISHED`. |
| `leagues[].team_mode` | string | Team mode enum: `MAIN_RESERVE`, `PRIORITY`, or `HIGHEST`. |
| `leagues[].scoring_system` | string | Configured scoring-system identifier. |
| `leagues[].teams` | integer | Number of teams. |
| `leagues[].drivers` | integer | Number of drivers. |
| `total` | integer | Number of leagues. |

### 14.2 `GET /api/v1/readonly/leagues/:name`

Returns detailed league information.

#### Path parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `name` | string | yes | League name. Matching is case-insensitive. |

#### Response

```json
{
  "id": 1,
  "name": "Season 1",
  "status": "RUNNING",
  "team_mode": "MAIN_RESERVE",
  "scoring_system": "F1",
  "teams": 4,
  "drivers": 12,
  "creator": "01234567-89ab-cdef-0123-456789abcdef",
  "mulligan": 0,
  "teams_list": [
    {
      "id": 10,
      "name": "Red Racing",
      "color": "#FF0000"
    }
  ],
  "categories": [
    {
      "id": 100,
      "name": "main",
      "display_name": "Main Class"
    }
  ],
  "calendar": [
    {
      "event_id": 42,
      "category": "main",
      "pinned_heat": 3
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `id` | integer | League ID. |
| `name` | string | League name. |
| `status` | string | League state enum. |
| `team_mode` | string | Team mode enum. |
| `scoring_system` | string | Scoring-system identifier. |
| `teams` | integer | Team count. |
| `drivers` | integer | Driver count. |
| `creator` | string | Creator UUID. |
| `mulligan` | integer | Configured mulligan count. |
| `teams_list` | array | Team summaries containing `id`, `name`, and `color`. |
| `categories` | array | Category summaries containing `id`, `name`, and `display_name`. |
| `calendar` | array | Calendar entries containing `event_id`, nullable `category`, and optional `pinned_heat`. |

A missing league returns HTTP `404`.

### 14.3 `GET /api/v1/readonly/leagues/:name/standings/drivers`

Returns driver standings for a league.

#### Response

```json
{
  "league": "Season 1",
  "standings": [
    {
      "uuid": "01234567-89ab-cdef-0123-456789abcdef",
      "name": "RacerOne",
      "points": 25,
      "wins": 2,
      "podiums": 4,
      "events": 6
    }
  ],
  "total": 1
}
```

| Field | Type | Description |
|---|---|---|
| `league` | string | League name. |
| `standings` | array | Driver standings. |
| `standings[].uuid` | string | Driver UUID. |
| `standings[].name` | string | Driver name. |
| `standings[].points` | integer | Championship points. |
| `standings[].wins` | integer | Wins. |
| `standings[].podiums` | integer | Podium finishes. |
| `standings[].events` | integer | Event count. |
| `total` | integer | Number of standings entries. |

### 14.4 `GET /api/v1/readonly/leagues/:name/standings/teams`

Returns team standings for a league.

#### Response

```json
{
  "league": "Season 1",
  "standings": [
    {
      "team_id": 10,
      "team_name": "Red Racing",
      "points": 40,
      "wins": 3,
      "podiums": 7
    }
  ],
  "total": 1
}
```

| Field | Type | Description |
|---|---|---|
| `league` | string | League name. |
| `standings` | array | Team standings. |
| `standings[].team_id` | integer | Team ID. |
| `standings[].team_name` | string | Team name. |
| `standings[].points` | integer | Team points. |
| `standings[].wins` | integer | Team wins. |
| `standings[].podiums` | integer | Team podiums. |
| `total` | integer | Number of standings entries. |

### 14.5 `GET /api/v1/readonly/leagues/:name/calendar`

Returns the league calendar.

#### Response

```json
{
  "league": "Season 1",
  "calendar": [
    {
      "event_id": 42,
      "category": "main",
      "pinned_heat": 3
    },
    {
      "event_id": 43,
      "category": null
    }
  ],
  "total": 2
}
```

| Field | Type | Description |
|---|---|---|
| `league` | string | League name. |
| `calendar` | array | Calendar entries. |
| `calendar[].event_id` | integer | Event ID. |
| `calendar[].category` | string/null | Category name, or `null` when none is assigned. |
| `calendar[].pinned_heat` | integer | Optional pinned heat ID; omitted when none is assigned. |
| `total` | integer | Number of calendar entries. |

### 14.6 `GET /api/v1/readonly/leagues/:name/team/:team`

Returns details and drivers for one league team.

#### Path parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `name` | string | yes | League name. |
| `team` | string | yes | Team name; matching is case-insensitive. |

#### Response

```json
{
  "id": 10,
  "name": "Red Racing",
  "color": "#FF0000",
  "drivers": [
    {
      "uuid": "01234567-89ab-cdef-0123-456789abcdef",
      "name": "RacerOne"
    }
  ],
  "driver_count": 1
}
```

| Field | Type | Description |
|---|---|---|
| `id` | integer | Team ID. |
| `name` | string | Team name. |
| `color` | string | Team color in hexadecimal form. |
| `drivers` | array | Driver identity summaries. |
| `drivers[].uuid` | string | Driver UUID. |
| `drivers[].name` | string | Driver name. |
| `driver_count` | integer | Number of drivers assigned to the team. |

A missing team returns HTTP `404`.

### 14.7 `GET /api/v1/readonly/leagues/:name/categories`

Returns league categories.

#### Response

```json
{
  "league": "Season 1",
  "categories": [
    {
      "id": 100,
      "name": "main",
      "display_name": "Main Class"
    }
  ],
  "total": 1
}
```

| Field | Type | Description |
|---|---|---|
| `league` | string | League name. |
| `categories` | array | Category summaries. |
| `categories[].id` | integer | Category ID. |
| `categories[].name` | string | Internal category name. |
| `categories[].display_name` | string | Display name. |
| `total` | integer | Number of categories. |

## 15. Duels

### 15.1 `GET /api/v1/readonly/duels/leaderboard`

Returns the duel ELO leaderboard.

#### Query parameters

| Name | Type | Required | Default | Description |
|---|---|---:|---:|---|
| `limit` | integer | no | `50` | Maximum number of rows. Positive values above `500` are reduced to `500`; invalid or non-positive values use the default. |

#### Response

```json
{
  "leaderboard": [
    {
      "uuid": "01234567-89ab-cdef-0123-456789abcdef",
      "name": "RacerOne",
      "elo": 1450,
      "wins": 12,
      "losses": 4
    }
  ],
  "total": 1
}
```

| Field | Type | Description |
|---|---|---|
| `leaderboard` | array | ELO rows sorted by ELO descending. |
| `leaderboard[].uuid` | string | Player UUID. |
| `leaderboard[].name` | string | Player name. |
| `leaderboard[].elo` | integer | Current ELO. |
| `leaderboard[].wins` | integer | Duel wins. |
| `leaderboard[].losses` | integer | Duel losses. |
| `total` | integer | Number of returned rows. |

### 15.2 `GET /api/v1/readonly/duels/players/:uuidorusername`

Returns a player's duel ELO statistics.

#### Path parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `uuidorusername` | string | yes | UUID or username. |

#### Response

```json
{
  "uuid": "01234567-89ab-cdef-0123-456789abcdef",
  "name": "RacerOne",
  "elo": 1450,
  "wins": 12,
  "losses": 4
}
```

If no database row exists, the implementation returns the default values `elo: 1200`, `wins: 0`, and `losses: 0`.

### 15.3 `GET /api/v1/readonly/duels/players/:uuidorusername/matches`

Returns recent duel matches involving a player.

#### Path parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `uuidorusername` | string | yes | UUID or username. |

#### Query parameters

| Name | Type | Required | Default | Description |
|---|---|---:|---:|---|
| `limit` | integer | no | `20` | Maximum number of matches. Positive values above `100` are reduced to `100`; invalid or non-positive values use the default. |

#### Response

```json
{
  "uuid": "01234567-89ab-cdef-0123-456789abcdef",
  "matches": [
    {
      "id": 17,
      "track": "VolcanoCircuit",
      "state": "FINISHED",
      "owner": "01234567-89ab-cdef-0123-456789abcdef",
      "winner": "89abcdef0123456789abcdef01234567",
      "laps": 3,
      "started": "2026-09-22 20:00:00.0",
      "finished": "2026-09-22 20:12:00.0",
      "players": [
        {
          "uuid": "01234567-89ab-cdef-0123-456789abcdef",
          "name": "RacerOne"
        }
      ]
    }
  ],
  "total": 1
}
```

| Field | Type | Description |
|---|---|---|
| `uuid` | string | Requested player UUID. |
| `matches` | array | Recent matches, newest first. |
| `matches[].id` | integer | Duel database ID. |
| `matches[].track` | string | Internal track name without spaces. |
| `matches[].state` | string | Stored duel state. |
| `matches[].owner` | string | Owner UUID as stored by the database. |
| `matches[].winner` | string/null | Winner UUID, or `null` when unavailable. |
| `matches[].laps` | integer | Configured lap count. |
| `matches[].started` | string | Database timestamp string for the start time. It is not normalized to epoch milliseconds by this endpoint. |
| `matches[].finished` | string/null | Database timestamp string for the finish time, or `null`. |
| `matches[].players` | array | Participant UUID/name summaries. |
| `total` | integer | Number of returned matches. |

## 16. Daily Race

### 16.1 `GET /api/v1/readonly/dailyrace`

Returns the active daily race and its exclusion list.

#### Response when active

```json
{
  "active": true,
  "event_id": 42,
  "event_name": "Daily Race",
  "track": "VolcanoCircuit",
  "state": "RUNNING",
  "phase": "PRACTICE",
  "practice_remaining_ms": 240000,
  "excluded_tracks": [
    "OldTrack"
  ]
}
```

#### Response when inactive

```json
{
  "active": false,
  "excluded_tracks": []
}
```

| Field | Type | Description |
|---|---|---|
| `active` | boolean | Whether an active daily event exists. |
| `event_id` | integer | Active event ID; present only when active. |
| `event_name` | string | Active event display name; present only when active. |
| `track` | string | Internal track name without spaces; present only when active. |
| `state` | string | Event state enum: `SETUP`, `RUNNING`, or `FINISHED`; present only when active. |
| `phase` | string | Daily-race phase enum value; present only when active. |
| `practice_remaining_ms` | integer | Remaining practice time in milliseconds; present only when active. |
| `excluded_tracks` | array | Tracks excluded from daily-race selection. |

If the daily race manager is unavailable, the endpoint returns HTTP `503`.

### 16.2 `GET /api/v1/readonly/dailyrace/results/latest`

Returns results for the most recently finished heat in the active daily race.

#### Response

```json
{
  "event_id": 42,
  "event_name": "Daily Race",
  "heat_id": 7,
  "track": "VolcanoCircuit",
  "results": [
    {
      "uuid": "01234567-89ab-cdef-0123-456789abcdef",
      "name": "RacerOne",
      "position": 1,
      "start_position": 1,
      "finished": true,
      "dnf": false,
      "total_time_ms": 183420,
      "total_time": "03:03:420",
      "fastest_lap_ms": 57123,
      "fastest_lap": "00:57.123"
    }
  ],
  "total": 1
}
```

| Field | Type | Description |
|---|---|---|
| `event_id` | integer | Daily event ID. |
| `event_name` | string | Daily event display name. |
| `heat_id` | integer | Latest finished heat ID. |
| `track` | string | Heat track name without spaces. |
| `results` | array | Driver result objects. |
| `results[].uuid` | string | Driver UUID. |
| `results[].name` | string | Driver name. |
| `results[].position` | integer | Finishing or current result position. |
| `results[].start_position` | integer | Starting position. |
| `results[].finished` | boolean | Whether the driver finished. |
| `results[].dnf` | boolean | Whether the driver is marked DNF. |
| `results[].total_time_ms` | integer | Total time in milliseconds. |
| `results[].total_time` | string | Formatted total time. |
| `results[].fastest_lap_ms` | integer | Fastest completed lap in milliseconds; omitted when unavailable. |
| `results[].fastest_lap` | string | Formatted fastest lap or placeholder. |
| `total` | integer | Number of result rows. |

If there is no active daily race, the endpoint returns HTTP `404`. If the active event has no finished heats, it also returns HTTP `404`.

### 16.3 `GET /api/v1/readonly/dailyrace/stats`

Returns a compact daily-race overview.

#### Response

```json
{
  "active": true,
  "event_id": 42,
  "event_name": "Daily Race",
  "track": "VolcanoCircuit",
  "phase": "PRACTICE",
  "excluded_tracks": 1,
  "total_events": 15
}
```

| Field | Type | Description |
|---|---|---|
| `active` | boolean | Whether a daily event is active. |
| `event_id` | integer | Active event ID; present only when active. |
| `event_name` | string | Active event name; present only when active. |
| `track` | string | Active event track; present only when active. |
| `phase` | string | Current daily-race phase; present only when active. |
| `excluded_tracks` | integer | Number of excluded tracks. |
| `total_events` | integer | Total number of events known to the event manager. |

### 16.4 `GET /api/v1/readonly/dailyrace/stats/:uuidorusername`

Returns player duel/ELO statistics through the daily-race statistics route.

The current implementation calls the same ELO record lookup as the duel statistics endpoint. It does not return a separate daily-race participation statistic.

#### Response

```json
{
  "uuid": "01234567-89ab-cdef-0123-456789abcdef",
  "name": "RacerOne",
  "elo": 1450,
  "wins": 12,
  "losses": 4
}
```

### 16.5 `GET /api/v1/readonly/dailyrace/excluded-tracks`

Returns the tracks excluded from daily-race selection.

#### Response

```json
{
  "excluded_tracks": [
    "OldTrack",
    "Maintenance Circuit"
  ],
  "total": 2
}
```

| Field | Type | Description |
|---|---|---|
| `excluded_tracks` | array | Excluded track names. |
| `total` | integer | Number of excluded tracks. |

## 17. Events and Results

### 17.1 `GET /api/v1/readonly/events/results/:eventname`

Returns complete stored results for an event, organized by rounds and heats.

#### Path parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `eventname` | string | yes | Event display name. Matching is case-insensitive. URL-encode spaces. |

#### Response

```json
{
  "id": 42,
  "name": "Weekly Cup",
  "track": "VolcanoCircuit",
  "state": "FINISHED",
  "rounds": [
    {
      "name": "Round 1 (FINAL)",
      "type": "FINAL",
      "state": "FINISHED",
      "heats": [
        {
          "id": 7,
          "track": "VolcanoCircuit",
          "state": "FINISHED",
          "laps": 3,
          "results": [
            {
              "uuid": "01234567-89ab-cdef-0123-456789abcdef",
              "name": "RacerOne",
              "position": 1,
              "start_position": 1,
              "finished": true,
              "dnf": false,
              "total_time_ms": 183420,
              "total_time": "03:03:420",
              "fastest_lap_ms": 57123,
              "fastest_lap": "00:57.123"
            }
          ]
        }
      ]
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `id` | integer | Event ID. |
| `name` | string | Event display name. |
| `track` | string | Event track name without spaces. |
| `state` | string | Event state enum. |
| `rounds` | array | Event rounds. |
| `rounds[].name` | string | Round display name. |
| `rounds[].type` | string | Round type enum, such as `PRACTICE`, `QUALIFICATION`, `FINAL`, or `SPRINT_RACE`. |
| `rounds[].state` | string | Round state enum: `SETUP`, `RUNNING`, or `FINISHED`. |
| `rounds[].heats` | array | Heats belonging to the round. |
| `heats[].id` | integer | Heat ID. |
| `heats[].track` | string | Heat track name without spaces. |
| `heats[].state` | string | Heat state enum: `IDLE`, `SETUP`, `PRACTICE`, `QUALIFYING`, `LOADED`, `STARTING`, `RACING`, or `FINISHED`. |
| `heats[].laps` | integer | Configured lap count. |
| `heats[].results` | array | Driver result objects using the shared result schema. |

A missing event returns HTTP `404`.

### 17.2 `GET /api/v1/readonly/events`

Returns all known events, including inactive and finished events.

#### Response

```json
{
  "events": [
    {
      "id": 42,
      "name": "Weekly Cup",
      "active": true,
      "state": "RUNNING",
      "track": "VolcanoCircuit",
      "round": "Round 1 (FINAL)",
      "heat_id": 7,
      "drivers": 8,
      "laps": 3,
      "start_time": 1720000000000
    }
  ],
  "total": 1
}
```

| Field | Type | Description |
|---|---|---|
| `events` | array | Event summaries. |
| `events[].id` | integer | Event ID. |
| `events[].name` | string | Event display name. |
| `events[].active` | boolean | Whether the event is not finished. |
| `events[].state` | string | Event state enum. |
| `events[].track` | string | Event track name without spaces. |
| `events[].round` | string | Current round display name; omitted when no current round exists. |
| `events[].heat_id` | integer | Current heat ID; omitted when no current heat exists. |
| `events[].drivers` | integer | Current heat driver count; omitted when no current heat exists. |
| `events[].laps` | integer | Current heat lap count; omitted when no current heat exists. |
| `events[].start_time` | integer | Event creation time in epoch milliseconds, or the current time when creation time is unavailable. |
| `total` | integer | Number of events. |

## 18. Live Data

### 18.1 `GET /api/v1/readonly/live/positions`

Returns live driver positions. The response shape depends on whether `heat_id` is supplied.

#### Query parameters

| Name | Type | Required | Description |
|---|---|---:|---|
| `heat_id` | integer/string | no | Heat ID to filter by. |

#### Response without `heat_id`

```json
{
  "timestamp": 1720000000000,
  "data": [
    {
      "heat_id": 7,
      "state": "RACING",
      "track": "VolcanoCircuit",
      "positions": [
        {
          "uuid": "01234567-89ab-cdef-0123-456789abcdef",
          "name": "RacerOne",
          "position": 1,
          "start_position": 1,
          "laps": 2,
          "checkpoints": 24,
          "finished": false,
          "dnf": false,
          "total_time_ms": 120000,
          "total_time": "02:00.000",
          "fastest_lap_ms": 57123,
          "fastest_lap": "00:57.123",
          "online": true,
          "world": "race_world",
          "x": 120.5,
          "y": 64.0,
          "z": -240.25,
          "yaw": 90.0
        }
      ]
    }
  ]
}
```

#### Response with a valid `heat_id`

```json
{
  "timestamp": 1720000000000,
  "heat_id": 7,
  "state": "RACING",
  "track": "VolcanoCircuit",
  "data": [
    {
      "uuid": "01234567-89ab-cdef-0123-456789abcdef",
      "name": "RacerOne",
      "position": 1,
      "start_position": 1,
      "laps": 2,
      "checkpoints": 24,
      "finished": false,
      "dnf": false,
      "total_time_ms": 120000,
      "total_time": "02:00.000",
      "fastest_lap_ms": 57123,
      "fastest_lap": "00:57.123",
      "online": true,
      "world": "race_world",
      "x": 120.5,
      "y": 64.0,
      "z": -240.25,
      "yaw": 90.0
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `timestamp` | integer | Response generation time in epoch milliseconds. |
| `heat_id` | integer | Present only when a valid heat ID was requested. |
| `state` | string | Present only when a valid heat ID was requested; heat state enum. |
| `track` | string | Present only when a valid heat ID was requested; heat track name. |
| `data` | array | Without `heat_id`, an array of heat objects. With `heat_id`, a flat array of driver position objects. |
| `data[].heat_id` | integer | Heat ID; present only in the all-heats response shape. |
| `data[].state` | string | Heat state; present only in the all-heats response shape. |
| `data[].track` | string | Heat track; present only in the all-heats response shape. |
| `data[].positions` | array | Driver positions for one heat; present only in the all-heats response shape. |
| `positions[].uuid` | string | Driver UUID. |
| `positions[].name` | string | Driver name. |
| `positions[].position` | integer | Current race position. |
| `positions[].start_position` | integer | Starting position. |
| `positions[].laps` | integer | Completed lap count. |
| `positions[].checkpoints` | integer | Checkpoints reached. |
| `positions[].finished` | boolean | Whether the driver finished. |
| `positions[].dnf` | boolean | Whether the driver is marked DNF. |
| `positions[].total_time_ms` | integer | Current or final total time in milliseconds. |
| `positions[].total_time` | string | Formatted total time. |
| `positions[].fastest_lap_ms` | integer | Fastest completed lap in milliseconds; omitted when unavailable. |
| `positions[].fastest_lap` | string | Formatted fastest lap or placeholder. |
| `positions[].online` | boolean | Whether the driver is currently online. |
| `positions[].world` | string | Current world; present only for online drivers. |
| `positions[].x` | number | Current X coordinate rounded to two decimals; present only for online drivers. |
| `positions[].y` | number | Current Y coordinate rounded to two decimals; present only for online drivers. |
| `positions[].z` | number | Current Z coordinate rounded to two decimals; present only for online drivers. |
| `positions[].yaw` | number | Current yaw rounded to two decimals; present only for online drivers. |

A missing or non-numeric `heat_id` is ignored and produces an empty `data` array with HTTP `200`. A numeric heat ID that does not exist also produces an empty `data` array with HTTP `200`.

### 18.2 `GET /api/v1/readonly/live/events`

Returns currently active events and their current round/heat information.

#### Response

```json
{
  "timestamp": 1720000000000,
  "events": [
    {
      "event_id": 42,
      "name": "Weekly Cup",
      "state": "RUNNING",
      "track": "VolcanoCircuit",
      "round": "Round 1 (FINAL)",
      "heat_id": 7,
      "heat_state": "RACING",
      "drivers": 8,
      "laps": 3
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `timestamp` | integer | Response generation time in epoch milliseconds. |
| `events` | array | Active event summaries. |
| `events[].event_id` | integer | Event ID. |
| `events[].name` | string | Event display name. |
| `events[].state` | string | Event state enum. |
| `events[].track` | string | Event track name without spaces. |
| `events[].round` | string | Current round display name; omitted when unavailable. |
| `events[].heat_id` | integer | Current heat ID; omitted when unavailable. |
| `events[].heat_state` | string | Current heat state; omitted when unavailable. |
| `events[].drivers` | integer | Current heat driver count; omitted when unavailable. |
| `events[].laps` | integer | Current heat lap count; omitted when unavailable. |

The endpoint considers every event whose state is not `FINISHED` active, including events that are still in `SETUP`.

## 19. Activity

### 19.1 `GET /api/v1/readonly/activity/stats`

Returns a time-series activity view.

#### Query parameters

| Name | Type | Required | Default | Description |
|---|---|---:|---:|---|
| `period` | string | no | `1h` | Supported values are `1h`, `24h`, `7d`, and `30d`. |

#### Response

```json
{
  "period": "1h",
  "labels": [
    "20:00",
    "20:10",
    "20:20"
  ],
  "values": [
    12,
    12,
    12
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `period` | string | Requested period, or the default `1h`. |
| `labels` | array | Time labels formatted as `HH:mm` or `dd/MM`. |
| `values` | array | Numeric activity values aligned with `labels`. |

Behavior by period:

- `1h`: seven 10-minute samples using the current online-player count.
- `24h`: 25 hourly samples generated with random values from 0 through 19.
- `7d`: eight daily samples generated with random values from 0 through 29.
- `30d`: 31 daily samples generated with random values from 0 through 39.
- Any other value: the requested value is echoed in `period`, but the endpoint uses the 24-hour sample shape and random values.

The longer-period values are generated per request and are not persisted historical measurements.

### 19.2 `GET /api/v1/readonly/activity/recent`

Returns recent activity entries for dashboard display.

#### Response

```json
{
  "activities": [
    {
      "type": "join",
      "title": "Players Online",
      "message": "12 players connected",
      "timestamp": 1720000000000
    },
    {
      "type": "race",
      "title": "Race in Progress",
      "message": "Heat #7 on track VolcanoCircuit",
      "timestamp": 1720000000000
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `activities` | array | Generated activity entries. |
| `activities[].type` | string | Activity type, such as `join` or `race`. |
| `activities[].title` | string | Display title. |
| `activities[].message` | string | Display message. |
| `activities[].timestamp` | integer | Generation time in epoch milliseconds. |

The current implementation generates example activity data from the current online-player count and active heats. It does not read a persistent activity history database. Display text is generated by the server and may follow the server's configured language.

## 20. Logs

### 20.1 `GET /api/v1/readonly/logs`

Returns recent API/system log entries held in memory.

Authentication is required when both `auth.enabled: true` and `auth.protect_logs: true` are configured.

#### Query parameters

| Name | Type | Required | Default | Description |
|---|---|---:|---:|---|
| `level` | string | no | none | Filters by log level case-insensitively. Use `all` or omit the parameter to disable the level filter. |
| `search` | string | no | none | Case-insensitive substring filter applied to the log message. |
| `limit` | integer | no | `50` | Maximum number of entries. Values above `100` are reduced to `100`. Invalid or non-positive values remain at the default. |

#### Response

```json
{
  "logs": [
    {
      "timestamp": 1720000000000,
      "time": "22/09/2026 20:00:00",
      "level": "info",
      "message": "API started on port 8080"
    }
  ],
  "total": 1
}
```

| Field | Type | Description |
|---|---|---|
| `logs` | array | Log entries, newest first. |
| `logs[].timestamp` | integer | Unix epoch milliseconds. |
| `logs[].time` | string | Human-readable time in `dd/MM/yyyy HH:mm:ss` using the server default time zone. |
| `logs[].level` | string | Log level, normally `info`, `warning`, or `error`. |
| `logs[].message` | string | Log message. |
| `total` | integer | Number of returned entries after filtering. |

The log store is in-memory and retains at most 100 entries. It is not a substitute for the complete server log file.

## 21. Shared Driver Result Schema

The event-results and daily-race result endpoints use this object shape:

```json
{
  "uuid": "01234567-89ab-cdef-0123-456789abcdef",
  "name": "RacerOne",
  "position": 1,
  "start_position": 1,
  "finished": true,
  "dnf": false,
  "total_time_ms": 183420,
  "total_time": "03:03:420",
  "fastest_lap_ms": 57123,
  "fastest_lap": "00:57.123"
}
```

`fastest_lap_ms` is omitted when no completed lap is available. `fastest_lap` remains present and uses a placeholder string in that case.

For qualification and sprint-qualification heats, results are ordered by fastest lap. Other heat types are ordered by driver position.