# FormulaRacing REST API — Documentation

> **Version:** `v1`, `v2`, `v3` — Port `20004`
> **Format:** All responses are `application/json`
> **Base URL:** `http://143.14.179.72:20004`

---

## Table of Contents

1. [Configuration](#1-configuration)
2. [Authentication](#2-authentication)
3. [Rate Limiting](#3-rate-limiting)
4. [Error Format](#4-error-format)
5. [Endpoints — v1](#5-endpoints--v1)
   - [Status](#51-status)
   - [Tracks](#52-tracks)
   - [Players](#53-players)
   - [Live / Real-Time](#54-live--real-time)
   - [Activity](#55-activity)
   - [Events](#56-events)
   - [Leaderboard](#57-leaderboard)
   - [Logs](#58-logs)
6. [Endpoints — v2 (Leagues & Results)](#6-endpoints--v2-leagues--results)
   - [Leagues](#61-leagues)
   - [Event Results](#62-event-results)
7. [Endpoints — v3 (Detailed Tracks)](#7-endpoints--v3-detailed-tracks)
8. [Dashboard](#8-dashboard)
9. [Time Format](#9-time-format)
10. [Quick Start — Testar com Python](#10-quick-start--testar-com-python)
11. [cURL Examples](#11-curl-examples)
12. [Quick Reference Table](#12-quick-reference-table)

---

## 1. Configuration

The API is configured via `plugins/FormulaRacing/api_config.yml` no servidor:

```yaml
port: 20004                             # HTTP port
enable_cors: true                       # Cross-Origin Resource Sharing
rate_limit:
  enabled: true                         # Enable rate limiting
  requests_per_minute: 60               # Max requests per minute per IP
log_requests: true                      # Log each API request
log_errors: true                        # Log API errors
connection_timeout: 30000               # Connection timeout (ms)
max_request_size: 1048576               # Max request size (bytes)
api_keys:
  read_only: []                         # Optional: API keys for v2 endpoints
```

> **Note:** Configuration is loaded once at startup. Changes require a server reload or restart.

---

## 2. Authentication

### Public Mode (default)
If `api_keys.read_only` is empty (the default), the API is **public** — no authentication required.

### API Key Mode
If one or more keys are listed in `api_keys.read_only`, the following v2 endpoints require a valid `api_key` query parameter:

- `/api/v2/readonly/leagues` and sub-endpoints
- `/api/v2/readonly/events/results` and sub-endpoints

**Request with API Key:**
```http
GET http://143.14.179.72:20004/api/v2/readonly/leagues?api_key=your-api-key-here
```

Unauthorized requests return `401`:
```json
{
  "error": true,
  "message": "Couldn't read api_key. Provide a valid api_key in your request.",
  "timestamp": 1785430000000
}
```

---

## 3. Rate Limiting

When enabled (default), the API allows **60 requests per minute** per IP address.

If exceeded, a `429 Too Many Requests` is returned:
```json
{
  "error": true,
  "message": "Rate limit exceeded",
  "timestamp": 1785430000000
}
```

Rate limiting applies to all `/api/*` endpoints.

---

## 4. Error Format

All errors follow this structure:

```json
{
  "error": true,
  "message": "Description of what went wrong",
  "timestamp": 1785430000000
}
```

HTTP status codes used:
| Code | Meaning |
|:----:|:--------|
| 200 | Success |
| 400 | Bad request — missing or invalid parameters |
| 401 | Unauthorized — invalid or missing API key |
| 404 | Resource not found |
| 429 | Rate limit exceeded |
| 500 | Internal server error |

---

## 5. Endpoints — v1

### 5.1 Status

#### `GET /api/v1/readonly/status`

Returns server status, version, and counters.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/status
```

**Response:**
```json
{
  "status": "online",
  "version": "0.2",
  "server": "Paper",
  "players_online": 12,
  "max_players": 100,
  "total_tracks": 25,
  "active_heats": 3,
  "total_events": 5
}
```

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | Always `"online"` |
| `version` | string | Plugin version |
| `server` | string | Server software name |
| `players_online` | int | Currently online players |
| `max_players` | int | Server max players |
| `total_tracks` | int | Total tracks in database |
| `active_heats` | int | Heats currently in RACING or STARTING state |
| `total_events` | int | Total events created |

---

### 5.2 Tracks

#### `GET /api/v1/readonly/tracks`

List all tracks with basic info.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/tracks
```

**Response:**
```json
{
  "number": 25,
  "tracks": [
    {
      "name": "Tutorial",
      "world": "world",
      "creator": "WolfBuildersTeam",
      "checkpoints": 3,
      "icon": "GRASS_BLOCK"
    },
    {
      "name": "KnightshadeRaceway",
      "world": "world",
      "creator": "mopeki",
      "checkpoints": 5,
      "icon": "BEACON"
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `number` | int | Total track count |
| `tracks[]` | array | Array of track objects |
| `.name` | string | Track command name |
| `.world` | string | World the track is in |
| `.creator` | string | Track creator/owner |
| `.checkpoints` | int | Number of checkpoints |
| `.icon` | string | Material icon name (or `"null"`) |

---

#### `GET /api/v1/readonly/tracks/:trackname`

Get detailed info about a specific track.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/tracks/BahrainFW
```

**Response:**
```json
{
  "name": "KnightshadeRaceway",
  "world": "world",
  "creator": "mopeki",
  "checkpoints": 5,
  "icon": "BEACON",
  "spawn": {
    "x": 100.5,
    "y": 64.0,
    "z": -200.3,
    "yaw": 180.0,
    "pitch": 0.0,
    "world": "world"
  },
  "medals": {
    "gold": "00:45:000",
    "silver": "00:50:000",
    "bronze": "01:00:000"
  },
  "record": 44123
}
```

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Track name |
| `world` | string | World |
| `creator` | string | Owner name |
| `checkpoints` | int | Total checkpoints |
| `icon` | string | Material icon |
| `spawn` | object | Track spawn location |
| `spawn.x` | double | X coordinate |
| `spawn.y` | double | Y coordinate |
| `spawn.z` | double | Z coordinate |
| `spawn.yaw` | float | Yaw (horizontal rotation) |
| `spawn.pitch` | float | Pitch (vertical rotation) |
| `spawn.world` | string | World name |
| `medals` | object | Medal rank times |
| `medals.gold` | string | Gold medal time (MM:SS:mmm) |
| `medals.silver` | string | Silver medal time |
| `medals.bronze` | string | Bronze medal time |
| `record` | double | Track record in **milliseconds** |

---

#### `GET /api/v1/readonly/tracks/:trackname/times`

Get top times on a track.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/tracks/BahrainFW/times
```

**Response:**
```json
{
  "track": "BahrainFW",
  "total": 35,
  "times": [
    {
      "player_name": "joka_10",
      "time": "01:55:200",
      "time_ms": 115200,
      "checkpoints": 15,
      "finished": true,
      "date": 1778468291000
    },
    {
      "player_name": "p4sokaa",
      "time": "01:55:870",
      "time_ms": 115870,
      "checkpoints": 16,
      "finished": true,
      "date": 1776559142000
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `track` | string | Track name |
| `total` | int | Number of times returned |
| `times[]` | array | Array of time records |
| `.player_name` | string | Player name |
| `.time` | string | Formatted time (MM:SS:mmm) |
| `.time_ms` | long | Time in **milliseconds** |
| `.checkpoints` | int | Checkpoints reached |
| `.finished` | bool | Whether the lap was completed |
| `.date` | long | Timestamp of the record (epoch ms) |

---

#### `GET /api/v1/readonly/leaderboard/:trackname`

Get medal/rank times for a track.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/leaderboard/BahrainFW
```

**Response:**
```json
{
  "track": "BahrainFW",
  "total_ranks": 3,
  "ranks": [
    { "medal": "gold",   "time": "01:45:000" },
    { "medal": "silver", "time": "01:50:000" },
    { "medal": "bronze", "time": "02:00:000" }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `track` | string | Track name |
| `total_ranks` | int | Number of rank tiers |
| `ranks[]` | array | Array of medal entries |
| `.medal` | string | Medal name (`gold`, `silver`, `bronze`) |
| `.time` | string | Formatted target time (MM:SS:mmm) |

---

### 5.3 Players

#### `GET /api/v1/readonly/players`

List all **online** players with their settings.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/players
```

**Response:**
```json
{
  "total": 12,
  "players": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "name": "mopeki",
      "online": true,
      "language": "en",
      "color1": "#FF0000",
      "color2": "#FFFFFF",
      "boat_type": 0,
      "scoreboard": true,
      "compact_mode": false
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `total` | int | Number of online players |
| `players[]` | array | Array of player objects |
| `.uuid` | string | Player UUID |
| `.name` | string | Player name |
| `.online` | bool | Always `true` for this endpoint |
| `.language` | string | Language code (`en`, `pt_BR`, `pt_PT`) |
| `.color1` | string | Primary color hex |
| `.color2` | string | Secondary color hex |
| `.boat_type` | int | Boat type ID |
| `.scoreboard` | bool | Whether scoreboard is enabled |
| `.compact_mode` | bool | Compact scoreboard mode |

---

#### `GET /api/v1/readonly/players/all`

List all players (currently online only — offline players are not returned yet).

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/players/all
```

**Response:** Same format as `/players`.

---

#### `GET /api/v1/readonly/players/:uuidorusername`

Get info about a specific player by UUID or username.

**Exemplos:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/players/EfraMLG
curl http://143.14.179.72:20004/api/v1/readonly/players/550e8400-e29b-41d4-a716-446655440000
```

**Response:**
```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "name": "EfraMLG",
  "online": true,
  "language": "pt_BR",
  "color1": "#FF0000",
  "color2": "#FFFFFF",
  "boat_type": 0,
  "scoreboard": true,
  "compact_mode": false
}
```

---

#### `GET /api/v1/readonly/players/:uuid/timetrials/:trackname`

Get a player's best time and rank on a specific track.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/players/550e8400-e29b-41d4-a716-446655440000/timetrials/Tutorial
```

**Response:**
```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "track": "Tutorial",
  "best_time": 44123,
  "rank": 1
}
```

| Field | Type | Description |
|-------|------|-------------|
| `uuid` | string | Player UUID |
| `track` | string | Track name |
| `best_time` | double | Best time in **milliseconds** (`0` if none) |
| `rank` | int | Player's rank on this track |

---

### 5.4 Live / Real-Time

#### `GET /api/v1/readonly/live/positions`

Get live positions of all drivers in active heats.

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `heat_id` | int (optional) | Filter by specific heat ID |

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/live/positions
curl http://143.14.179.72:20004/api/v1/readonly/live/positions?heat_id=5
```

**Without `heat_id`:**
```json
{
  "timestamp": 1785430000000,
  "data": [
    {
      "heat_id": 5,
      "state": "RACING",
      "track": "KnightshadeRaceway",
      "positions": [
        {
          "uuid": "550e8400-e29b-41d4-a716-446655440000",
          "name": "mopeki",
          "position": 1,
          "start_position": 1,
          "laps": 3,
          "checkpoints": 15,
          "finished": false,
          "dnf": false,
          "total_time_ms": 120000,
          "total_time": "02:00:000",
          "fastest_lap": "00:45:123",
          "fastest_lap_ms": 45123,
          "online": true,
          "world": "world",
          "x": 123.45,
          "y": 64.0,
          "z": -789.01,
          "yaw": 180.5
        }
      ]
    }
  ]
}
```

| Driver Field | Type | Description |
|--------------|------|-------------|
| `uuid` | string | Player UUID |
| `name` | string | Player name |
| `position` | int | Current race position |
| `start_position` | int | Grid start position |
| `laps` | int | Laps completed |
| `checkpoints` | int | Checkpoints reached in current lap |
| `finished` | bool | Whether driver finished |
| `dnf` | bool | Did Not Finish |
| `total_time_ms` | long | Total race time in ms |
| `total_time` | string | Formatted time (MM:SS:mmm) |
| `fastest_lap` | string | Best lap time formatted |
| `fastest_lap_ms` | long | Best lap in ms |
| `online` | bool | Whether player is currently online |
| `world` | string | World name |
| `x` | double | X position |
| `y` | double | Y position |
| `z` | double | Z position |
| `yaw` | float | Yaw (horizontal rotation) |

---

#### `GET /api/v1/readonly/positions/live`

**Map Feed** — Lightweight endpoint that serves positions from a memory cache (updated every ~100ms). Does **not** access entities per-request — safe for polling at high frequency.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/positions/live
```

**Response:**
```json
{
  "server_time": 1785430000000,
  "drivers": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "x": 123.45,
      "y": 64.0,
      "z": -789.01,
      "yaw": 180.5,
      "pitch": 2.3,
      "world": "world",
      "t": 1785430000000
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `server_time` | long | Server timestamp in ms |
| `drivers[]` | array | Array of driver positions |
| `.uuid` | string | Player UUID |
| `.x` | double | X coordinate |
| `.y` | double | Y coordinate |
| `.z` | double | Z coordinate |
| `.yaw` | float | Yaw (horizontal rotation) |
| `.pitch` | float | Pitch (vertical rotation) |
| `.world` | string | World name |
| `.t` | long | Snapshot timestamp |

> **Intended use:** Polled by the [Live Map dashboard](#dashboard) to render driver positions on a 2D canvas for real-time overtake visualization.

---

#### `GET /api/v1/readonly/live/events`

Get all currently **active** events.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/live/events
```

**Response:**
```json
{
  "timestamp": 1785430000000,
  "events": [
    {
      "event_id": 1,
      "name": "Grand Prix Test",
      "state": "ACTIVE",
      "track": "KnightshadeRaceway",
      "round": "Qualification Round",
      "heat_id": 5,
      "heat_state": "RACING",
      "drivers": 10,
      "laps": 15
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `event_id` | int | Event ID |
| `name` | string | Event display name |
| `state` | string | Event state enum |
| `track` | string | Track name |
| `round` | string | Current round display name |
| `heat_id` | int | Current heat ID |
| `heat_state` | string | Heat state (IDLE, SETUP, COUNTDOWN, RACING, FINISHED) |
| `drivers` | int | Number of drivers in current heat |
| `laps` | int | Total laps for this heat |

---

#### `GET /api/v1/readonly/events/running-heats`

Get all currently running heats with detailed driver information, gaps, and timing.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/events/running-heats
```

**Response:**
```json
[
  {
    "name": "Heat 1",
    "event_name": "Grand Prix Test",
    "id": 5,
    "track": "KnightshadeRaceway",
    "driver_details": [
      {
        "name": "mopeki",
        "uuid": "550e8400-...",
        "position": 1,
        "start_position": 1,
        "laps": 3,
        "pits": 1,
        "is_in_pit": false,
        "is_offline": false,
        "best_lap": 45123,
        "gap_to_leader": 0,
        "gap": 0
      },
      {
        "name": "gytixss",
        "uuid": "660e8400-...",
        "position": 2,
        "start_position": 3,
        "laps": 3,
        "pits": 0,
        "is_in_pit": false,
        "is_offline": false,
        "best_lap": 45789,
        "gap_to_leader": 2345,
        "gap": 2345
      }
    ]
  }
]
```

| Driver Field | Type | Description |
|--------------|------|-------------|
| `name` | string | Driver name |
| `uuid` | string | Player UUID |
| `position` | int | Current position |
| `start_position` | int | Starting grid position |
| `laps` | int | Laps completed |
| `pits` | int | Pit stops made |
| `is_in_pit` | bool | Currently in pit lane |
| `is_offline` | bool | Player is offline (disconnected) |
| `best_lap` | long | Fastest lap time in ms (`-1` if none) |
| `gap_to_leader` | long | Time gap to leader in ms (`-1` if N/A) |
| `gap` | long | Gap to driver ahead in ms |

---

### 5.5 Activity

#### `GET /api/v1/readonly/activity/stats?period=1h`

Get activity statistics for a time period.

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `period` | string | `"1h"` | Time period: `1h`, `24h`, `7d`, `30d` |

**Exemplo:**
```bash
curl "http://143.14.179.72:20004/api/v1/readonly/activity/stats?period=24h"
```

**Response:**
```json
{
  "period": "1h",
  "labels": ["14:00", "14:10", "14:20", "14:30", "14:40", "14:50", "15:00"],
  "values": [10, 12, 15, 14, 18, 20, 22]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `period` | string | Requested time period |
| `labels` | string[] | Time labels for X-axis |
| `values` | int[] | Player count values for Y-axis |

---

#### `GET /api/v1/readonly/activity/recent`

Get recent server activity.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/activity/recent
```

**Response:**
```json
{
  "activities": [
    {
      "type": "join",
      "title": "Jogadores Online",
      "message": "12 jogadores conectados",
      "timestamp": 1785430000000
    },
    {
      "type": "race",
      "title": "Corrida em Andamento",
      "message": "Heat #5 na pista KnightshadeRaceway",
      "timestamp": 1785430000000
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `activities[]` | array | Recent activity items |
| `.type` | string | Activity type (`join`, `race`, etc.) |
| `.title` | string | Activity title |
| `.message` | string | Activity description |
| `.timestamp` | long | When the activity occurred |

---

### 5.6 Events

#### `GET /api/v1/readonly/events`

Get all events (active and inactive).

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v1/readonly/events
```

**Response:**
```json
{
  "events": [
    {
      "id": 1,
      "name": "Grand Prix Test",
      "active": true,
      "state": "ACTIVE",
      "track": "KnightshadeRaceway",
      "round": "Qualification Round",
      "heat_id": 5,
      "drivers": 10,
      "laps": 15,
      "start_time": 1785429000000
    }
  ],
  "total": 5
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | int | Event ID |
| `name` | string | Event display name |
| `active` | bool | Whether event is currently active |
| `state` | string | Event state |
| `track` | string | Track name |
| `round` | string | Current round name |
| `heat_id` | int | Current heat ID |
| `drivers` | int | Driver count in current heat |
| `laps` | int | Total laps |
| `start_time` | long | Event start timestamp (ms) |

---

### 5.7 Logs

#### `GET /api/v1/readonly/logs?level=info&search=api&limit=20`

Get system logs.

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `level` | string | all | Filter by level: `info`, `warning`, `error`, or `all` |
| `search` | string | — | Filter logs containing this text |
| `limit` | int | 50 | Max logs to return (max 100) |

**Exemplo:**
```bash
curl "http://143.14.179.72:20004/api/v1/readonly/logs?level=error&limit=10"
```

**Response:**
```json
{
  "logs": [
    {
      "level": "info",
      "message": "API iniciada na porta 20004",
      "timestamp": 1785429000000
    },
    {
      "level": "error",
      "message": "API Error: Track not found",
      "timestamp": 1785429500000
    }
  ],
  "total": 2
}
```

| Field | Type | Description |
|-------|------|-------------|
| `logs[]` | array | Log entries |
| `.level` | string | Log level (`info`, `warning`, `error`) |
| `.message` | string | Log message text |
| `.timestamp` | long | When the log was created |
| `total` | int | Number of logs returned |

---

## 6. Endpoints — v2 (Leagues & Results)

> **Note:** v2 endpoints may require an `api_key` query parameter if API keys are configured in `api_config.yml`.

### 6.1 Leagues

#### `GET /api/v2/readonly/leagues`

List all leagues.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v2/readonly/leagues
```

**Response:**
```json
{
  "number": 2,
  "leagues": [
    {
      "name": "Wolf Racing League",
      "driver_count": 15,
      "team_count": 5,
      "event_count": 8
    }
  ]
}
```

---

#### `GET /api/v2/readonly/leagues/:name`

Get league details.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v2/readonly/leagues/Wolf%20Racing%20League
```

**Response:**
```json
{
  "name": "Wolf Racing League",
  "scoring_system": "f1_points",
  "team_mode": "TEAMS",
  "mulligan_count": 1,
  "teams": [
    {
      "id": 1,
      "name": "Wolf Racing",
      "color": "#FF0000"
    }
  ],
  "driver_count": 15
}
```

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | League name |
| `scoring_system` | string | Scoring system identifier |
| `team_mode` | string | Team mode (`TEAMS`, `SOLO`) |
| `mulligan_count` | int | Number of mulligan (dropped) results |
| `teams[]` | array | Teams in the league |
| `.id` | int | Team ID |
| `.name` | string | Team name |
| `.color` | string | Team hex color |
| `driver_count` | int | Total drivers |

---

#### `GET /api/v2/readonly/leagues/:name/standings/drivers`

Get driver standings.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v2/readonly/leagues/Wolf%20Racing%20League/standings/drivers
```

**Response:**
```json
{
  "drivers": [
    {
      "uuid": "550e8400-...",
      "points": 85,
      "wins": 3,
      "podiums": 5,
      "events_count": 7
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `uuid` | string | Player UUID |
| `points` | int | Total championship points |
| `wins` | int | Race wins |
| `podiums` | int | Podium finishes |
| `events_count` | int | Events participated in |

---

#### `GET /api/v2/readonly/leagues/:name/standings/teams`

Get team standings.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v2/readonly/leagues/Wolf%20Racing%20League/standings/teams
```

**Response:**
```json
{
  "teams": [
    {
      "name": "Wolf Racing",
      "points": 150,
      "wins": 3,
      "podiums": 8
    }
  ]
}
```

---

#### `GET /api/v2/readonly/leagues/:name/calendar`

Get league calendar.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v2/readonly/leagues/Wolf%20Racing%20League/calendar
```

**Response:**
```json
{
  "calendar": [
    {
      "event_id": 1,
      "category": "Gold",
      "heat": "5"
    },
    {
      "event_id": 2,
      "category": "null",
      "heat": "null"
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `event_id` | int | Event ID |
| `category` | string | Category name (or `"null"`) |
| `heat` | string | Pinned heat ID (or `"null"`) |

---

#### `GET /api/v2/readonly/leagues/:name/team/:team`

Get team members.

**Exemplo:**
```bash
curl "http://143.14.179.72:20004/api/v2/readonly/leagues/Wolf%20Racing%20League/team/Wolf%20Racing"
```

**Response:**
```json
{
  "name": "Wolf Racing",
  "color": "#FF0000",
  "id": 1,
  "members": [
    "550e8400-e29b-41d4-a716-446655440000",
    "660e8400-e29b-41d4-a716-446655440001"
  ]
}
```

---

#### `GET /api/v2/readonly/leagues/:name/categories`

Get league categories.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v2/readonly/leagues/Wolf%20Racing%20League/categories
```

**Response:**
```json
{
  "categories": [
    {
      "id": 1,
      "display_name": "Gold",
      "scoring_system": "f1_points",
      "mulligan_count": 0
    }
  ]
}
```

---

### 6.2 Event Results

#### `GET /api/v2/readonly/events/results/:eventname`

Get complete event results including all rounds, heats, and drivers with lap times.

**Exemplo:**
```bash
curl "http://143.14.179.72:20004/api/v2/readonly/events/results/Grand%20Prix%20Test"
```

**Response:**
```json
{
  "id": 1,
  "name": "Grand Prix Test",
  "track": "KnightshadeRaceway",
  "state": "FINISHED",
  "rounds": [
    {
      "name": "qualification",
      "display_name": "Qualification Round",
      "state": "FINISHED",
      "heats": [
        {
          "id": 3,
          "name": "Quali Heat 1",
          "state": "FINISHED",
          "track": "KnightshadeRaceway",
          "total_laps": 3,
          "driver_count": 10,
          "drivers": [
            {
              "uuid": "550e8400-...",
              "name": "mopeki",
              "position": 1,
              "start_position": 1,
              "laps_completed": 3,
              "checkpoints": 15,
              "finished": true,
              "dnf": false,
              "total_time_ms": 135000,
              "total_time": "02:15:000",
              "fastest_lap_ms": 45123,
              "fastest_lap": "00:45:123",
              "laps": [
                { "time_ms": 45123, "time": "00:45:123" },
                { "time_ms": 45200, "time": "00:45:200" },
                { "time_ms": 45000, "time": "00:45:000" }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

| Driver Field | Type | Description |
|--------------|------|-------------|
| `laps_completed` | int | Total laps completed |
| `finished` | bool | Whether driver finished the heat |
| `dnf` | bool | Did Not Finish |
| `total_time_ms` | long | Total race time in ms |
| `laps[]` | array | Array of individual lap times |
| `.time_ms` | long | Lap time in ms |
| `.time` | string | Lap time formatted (MM:SS:mmm) |

---

#### `GET /api/v2/readonly/events/results/:eventname/heat/:heatid`

Get a specific heat's results.

**Exemplo:**
```bash
curl "http://143.14.179.72:20004/api/v2/readonly/events/results/Grand%20Prix%20Test/heat/3"
```

**Response:** Same single heat object as the `heats[]` array in the event results above.

---

## 7. Endpoints — v3 (Detailed Tracks)

#### `GET /api/v3/readonly/tracks`

Get a detailed list of all tracks including circuit info, spawn locations, and icon details.

**Exemplo:**
```bash
curl http://143.14.179.72:20004/api/v3/readonly/tracks
```

**Response:**
```json
{
  "number": 25,
  "tracks": [
    {
      "command_name": "KnightshadeRaceway",
      "display_name": "KnightshadeRaceway",
      "circuit": true,
      "owner": "mopeki",
      "icon": "BEACON",
      "total_checkpoints": 5,
      "world": "world",
      "spawn_location": {
        "x": 100.5,
        "y": 64.0,
        "z": -200.3,
        "pitch": 0.0,
        "yaw": 180.0,
        "world_name": "world"
      }
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `number` | int | Total tracks |
| `tracks[]` | array | Array of detailed track objects |
| `.command_name` | string | Track name for commands |
| `.display_name` | string | Track display name |
| `.circuit` | bool | Whether the track is a circuit (lap-based) |
| `.owner` | string | Track creator |
| `.icon` | string | Material icon |
| `.total_checkpoints` | int | Number of checkpoints |
| `.world` | string | World name |
| `.spawn_location` | object | Track spawn location |
| `.spawn_location.x` | double | X coordinate |
| `.spawn_location.y` | double | Y coordinate |
| `.spawn_location.z` | double | Z coordinate |
| `.spawn_location.pitch` | float | Pitch |
| `.spawn_location.yaw` | float | Yaw |
| `.spawn_location.world_name` | string | World name |

---

## 8. Dashboard

The plugin serves a web dashboard with visual UI and a live minimap.

### `http://143.14.179.72:20004/`
Redirects to `/dashboard/index.html`.

### `http://143.14.179.72:20004/dashboard/index.html`
Main dashboard with:
- Server overview (players, tracks, events, active heats)
- Track list
- Player list
- Event list
- Activity logs
- Live event status

### `http://143.14.179.72:20004/dashboard/live-map.html`
Live minimap page showing drivers in real-time with:
- 2D canvas with driver triangles (rotated by yaw)
- Driver names with position colors
- Heat selector dropdown
- Legend (P1, P2, P3, etc. with colors)
- Zoom and pan controls
- Trail visualization (last 200 points)
- Auto-scale on heat selection
- **OBS-ready** for stream overlays

---

## 9. Time Format

All displayed times use the format `MM:SS:mmm`:
| Example | Meaning |
|:-------:|:--------|
| `00:45:123` | 45 seconds and 123 milliseconds |
| `01:55:200` | 1 minute, 55 seconds and 200 milliseconds |
| `--:--:---` | No time available |

In JSON, numeric time fields (`time_ms`, `best_time`, `record`, etc.) are provided in **milliseconds** for programmatic use.

---

## 10. Quick Start — Testar com Python

### Opção 1 — One-liner (teste rápido)

```bash
# Ver status do servidor
python -c "import urllib.request,json; print(json.dumps(json.loads(urllib.request.urlopen('http://143.14.179.72:20004/api/v1/readonly/status', timeout=10).read()), indent=2))"

# Listar tracks
python -c "import urllib.request,json; d=json.loads(urllib.request.urlopen('http://143.14.179.72:20004/api/v1/readonly/tracks', timeout=10).read()); [print(t['name'],'-',t['world'],'-',t['creator']) for t in d['tracks']]"

# Ver top 5 times de uma pista
python -c "import urllib.request,json; d=json.loads(urllib.request.urlopen('http://143.14.179.72:20004/api/v1/readonly/tracks/BahrainFW/times', timeout=10).read()); [print(t['player_name'], t['time'], str(t['time_ms'])+'ms') for t in d['times'][:5]]"

# Ver corridas ao vivo
python -c "import urllib.request,json; d=json.loads(urllib.request.urlopen('http://143.14.179.72:20004/api/v1/readonly/events/running-heats', timeout=10).read()); [print('Heat #'+str(h['id']), h['track'], str(len(h['driver_details']))+' drivers') for h in d]"
```

### Opção 2 — Script completo (`test_api.py`)

Copia o ficheiro `test_api.py` da raiz do projeto e corre:

```bash
python test_api.py                    # Já aponta para o servidor correto
python test_api.py http://localhost:8080  # Testar local
```

O script testa **todos os endpoints**:
- Status do servidor
- Lista de tracks
- Players online
- Eventos ativos
- Heats em execução com gaps
- Posições ao vivo (x, y, z, yaw)
- Leaderboard / medalhas
- Top times da primeira pista
- Leagues
- Logs do sistema
- Activity stats

---

## 11. cURL Examples

```bash
# Get server status
curl http://143.14.179.72:20004/api/v1/readonly/status

# List all tracks
curl http://143.14.179.72:20004/api/v1/readonly/tracks

# Get track details
curl http://143.14.179.72:20004/api/v1/readonly/tracks/BahrainFW

# Get top times on a track
curl http://143.14.179.72:20004/api/v1/readonly/tracks/BahrainFW/times

# Get online players
curl http://143.14.179.72:20004/api/v1/readonly/players

# Get live events
curl http://143.14.179.72:20004/api/v1/readonly/live/events

# Get live positions for all heats
curl http://143.14.179.72:20004/api/v1/readonly/live/positions

# Get live positions for a specific heat
curl http://143.14.179.72:20004/api/v1/readonly/live/positions?heat_id=5

# Get running heats with driver details and gaps
curl http://143.14.179.72:20004/api/v1/readonly/events/running-heats

# Get map feed (lightweight position cache)
curl http://143.14.179.72:20004/api/v1/readonly/positions/live

# Get activity stats (last hour)
curl "http://143.14.179.72:20004/api/v1/readonly/activity/stats?period=1h"

# Get recent activity
curl http://143.14.179.72:20004/api/v1/readonly/activity/recent

# List all leagues
curl http://143.14.179.72:20004/api/v2/readonly/leagues

# Get league driver standings
curl "http://143.14.179.72:20004/api/v2/readonly/leagues/Wolf%20Racing%20League/standings/drivers"

# Get event results
curl "http://143.14.179.72:20004/api/v2/readonly/events/results/Grand%20Prix%20Test"

# Track leaderboard (medal times)
curl http://143.14.179.72:20004/api/v1/readonly/leaderboard/BahrainFW

# Player info by username
curl http://143.14.179.72:20004/api/v1/readonly/players/EfraMLG

# Player's best time on a track
curl http://143.14.179.72:20004/api/v1/readonly/players/550e8400-e29b-41d4-a716-446655440000/timetrials/Tutorial

# Get system logs (last 10 warnings)
curl "http://143.14.179.72:20004/api/v1/readonly/logs?level=warning&limit=10"

# With API key authentication
curl "http://143.14.179.72:20004/api/v2/readonly/leagues?api_key=my-secret-key"

# Pretty-print com Python
curl -s http://143.14.179.72:20004/api/v1/readonly/status | python -m json.tool
```

---

## 12. Quick Reference Table

| # | Method | Endpoint | Auth | Description |
|:-:|:------:|:---------|:----:|:------------|
| 1 | GET | `/api/v1/readonly/status` | — | Server status |
| 2 | GET | `/api/v1/readonly/tracks` | — | List tracks (basic) |
| 3 | GET | `/api/v1/readonly/tracks/:trackname` | — | Track details |
| 4 | GET | `/api/v1/readonly/tracks/:trackname/times` | — | Top times |
| 5 | GET | `/api/v1/readonly/leaderboard/:trackname` | — | Medal ranks |
| 6 | GET | `/api/v1/readonly/players` | — | Online players |
| 7 | GET | `/api/v1/readonly/players/all` | — | All players |
| 8 | GET | `/api/v1/readonly/players/:uuidorusername` | — | Player info |
| 9 | GET | `/api/v1/readonly/players/:uuid/timetrials/:trackname` | — | Player best time |
| 10 | GET | `/api/v1/readonly/live/positions` | — | Live heat positions |
| 11 | GET | `/api/v1/readonly/live/events` | — | Active events |
| 12 | GET | `/api/v1/readonly/events/running-heats` | — | Running heats with gaps |
| 13 | GET | `/api/v1/readonly/positions/live` | — | Map feed (cache) |
| 14 | GET | `/api/v1/readonly/activity/stats` | — | Activity statistics |
| 15 | GET | `/api/v1/readonly/activity/recent` | — | Recent activity |
| 16 | GET | `/api/v1/readonly/events` | — | All events |
| 17 | GET | `/api/v1/readonly/logs` | — | System logs |
| 18 | GET | `/api/v2/readonly/leagues` | 🔑 | List leagues |
| 19 | GET | `/api/v2/readonly/leagues/:name` | 🔑 | League details |
| 20 | GET | `/api/v2/readonly/leagues/:name/standings/drivers` | 🔑 | Driver standings |
| 21 | GET | `/api/v2/readonly/leagues/:name/standings/teams` | 🔑 | Team standings |
| 22 | GET | `/api/v2/readonly/leagues/:name/calendar` | 🔑 | League calendar |
| 23 | GET | `/api/v2/readonly/leagues/:name/team/:team` | 🔑 | Team members |
| 24 | GET | `/api/v2/readonly/leagues/:name/categories` | 🔑 | Categories |
| 25 | GET | `/api/v2/readonly/events/results/:eventname` | 🔑 | Event results |
| 26 | GET | `/api/v2/readonly/events/results/:eventname/heat/:heatid` | 🔑 | Heat results |
| 27 | GET | `/api/v3/readonly/tracks` | — | Detailed tracks |

> 🔑 = Requires API key if `api_keys.read_only` is configured.

### Dashboard Pages

| URL | Description |
|:----|:------------|
| `http://143.14.179.72:20004/` | Redirects to dashboard |
| `http://143.14.179.72:20004/dashboard/index.html` | Main dashboard |
| `http://143.14.179.72:20004/dashboard/live-map.html` | Live minimap with real-time driver positions |

---

## Changelog

| Date | Version | Changes |
|:----:|:-------:|:--------|
| 2026-07-30 | v1–v3 | Initial documentation covering all API endpoints |

---

*Generated for FormulaRacing v0.2*
