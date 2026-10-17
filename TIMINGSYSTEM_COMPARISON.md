# Comparação de Features: TimingSystem vs FormulaRacing

Documento comparando o plugin **TimingSystem** (FrostHexABG/Makkuusen) com o **FormulaRacing** (Wolf Network), focando no que o TimingSystem possui e o FormulaRacing não.

## 📊 Resumo

| Métrica | TimingSystem | FormulaRacing |
|---------|:------------:|:-------------:|
| Tipo de plugin | Bukkit/Paper (Java 21) | Paper/Folia (Java 25) |
| Foco principal | Time trials (barco/elytra/parkour) + eventos | Corridas estilo F1 completas (barcos) |
| Tipos de pista | Boat, Elytra, Parkour | Boat (com blocos parkour como superfície) |
| Idiomas | ~? (lang/) | 32 |
| Banco de dados | SQLite/MySQL/MariaDB | SQLite/MySQL/MariaDB (HikariCP) |
| REST API | Add-on separado | Embutida (Spark Java) |
| Folia | ❌ | ✅ |
| Bedrock (Geyser) | ❌ (não oficial) | ✅ |

## ✅ O que o TimingSystem tem e o FormulaRacing NÃO tem

### 1. Time Trials de Elytra
- TimingSystem suporta pistas do tipo `ELYTRA` (asas descartáveis dadas ao jogador, proteção de elytra).
- FormulaRacing é focado apenas em barcos; não há modo de time trial com elytra.
- **Arquivos relevantes (TimingSystem):** `track/Track.java` (`isElytraTrack`, `TrackType.ELYTRA`), `ApiUtilities.giveElytra`, `TSListener.onPlayerMoveEventRemoveElytra`.

### 2. Time Trials de Parkour (a pé)
- TimingSystem suporta pistas do tipo `PARKOUR` (jogador correndo a pé, checkpoints).
- FormulaRacing tem blocos "parkour" apenas como superfície de pistas de barco, não time trials a pé.
- **Arquivos relevantes (TimingSystem):** `Track.TrackType.PARKOUR`.

### 3. Integração com BlueMap
- TimingSystem tem o add-on `TimingSystemBlueMap` que adiciona localizações de pistas ao BlueMap.
- FormulaRacing não possui integração com BlueMap.
- **Possível port:** criar módulo similar usando a API do BlueMap.

### 4. No-Boat-Collisions como add-on separado
- TimingSystem distribui `TimingSystemNoBoatCollisions` como plugin separado.
- FormulaRacing já possui modos de colisão (`CollisionMode`: HIGH/LOW/DISABLED) embutidos, então é equivalente/mais completo.

### 5. Faixa de servidor / FAWE
- TimingSystem exige FastAsyncWorldEdit (FAWE) para criação de pistas.
- FormulaRacing usa WorldEdit padrão. (Diferença de dependência, não feature.)

### 6. Tipos de pista (TrackType)
- TimingSystem: `BOAT`, `ELYTRA`, `PARKOUR`.
- FormulaRacing: apenas barcos (com variações de superfície/blocos).

## 🔄 Equivalências (TimingSystem tem, FormulaRacing também tem, de forma equivalente ou melhor)

| Feature TimingSystem | Equivalente FormulaRacing |
|---------------------|--------------------------|
| Hologram leaderboards (DecentHolograms) | TrackLeaderboard com hologramas Java/Bedrock |
| Time trial random (`/timetrialrandom`) | `/timetrial random` (TimeTrialCommand) |
| Ghosts (`/ghost`, `/unghost`) | GhostCommand / UnghostCommand |
| Teams | League/Team system (mais completo) |
| Ready check | ReadyCheckManager |
| Track tags | Track tags (TrackEditorCommand) |
| Track exchange (copy/paste) | TrackExchangeManager + TrackExchangeCommand |
| Boat utils modes | OpenBoatUtilsCommand + BoatUtils |
| DRS | DRS embutido + regiões |
| Reset / checkpoint | ResetCommand, ResetCheckpointCommand |
| Events / heats / rounds | EventCommand, HeatCommand, RoundCommand (mais completo) |
| Database (SQLite/MySQL/MariaDB) | DatabaseManager (SQLite/MySQL/MariaDB via HikariCP) |
| PlaceholderAPI | PlaceholderAPI (registerPlaceholders) |
| Leaderboards (comando) | Track leaderboards |

## 🚀 O que o FormulaRacing tem e o TimingSystem NÃO tem (vantagens nossas)

- **IA de oponentes** (AIOpponentManager, AIRacingLine, FakePlayerNPC)
- **Ligas/Campeonatos** completos (LeagueCommand, standings, calendar, categorias)
- **Duels 1v1** (DuelCommand)
- **Party Race** (PartyCommand)
- **Daily Race** (DailyRaceCommand/Manager)
- **Fantasy/Escolha de pilotos**
- **DRS / ERS / Push-to-Pass** (sistemas completos)
- **Pit stops** com gerenciador dedicado
- **Pneus** (compound, degradação)
- **Combustível**
- **Clima dinâmico** (WeatherCommand)
- **Eliminação / Hot Potato / Musical Chairs / CTF / Infection / Sumo / Blind Race / Moving Target / Total Chaos / Roulette / Obstacle Race** (round types extras)
- **Gimmicks** (GimmickCommand/Gui)
- **Podium** (PodiumCommand)
- **Vote Race** (VoteRaceCommand)
- **Cameras** (CamCommand)
- **Spawn interno** (SpawnCommand)
- **Compatibilidade Bedrock** (Geyser/Floodgate, formulários nativos)
- **Folia** (suporte a Folia)
- **32 idiomas**
- **REST API embutida** (Spark Java, ~30 endpoints)
- **Scoreboard V2** (Megavex) com builders por estado
- **AI Command** (AICommand)
- **Admin Command** (AdminCommand)
- **Announce Command**
- **Toggle Rods**
- **OpenBoatUtils** integração completa (command)
- **Hotbar Items** (HotbarItemsCommand)
- **Help service** (CommandHelpService)

## 🎯 Gaps prioritários a portar do TimingSystem para o FormulaRacing

1. **Elytra time trials** — adicionar `TrackType.ELYTRA` e lógica de dar/remover elytra (maior gap de gameplay).
2. **Parkour time trials (a pé)** — modo de time trial sem barco.
3. **Integração BlueMap** — portar `TimingSystemBlueMap` como módulo opcional.
4. **TrackType enum** — introduzir conceito de tipo de pista (BOAT/ELYTRA/PARKOUR) no FormulaRacing.

## 📝 Notas

- O FormulaRacing é, no geral, muito mais completo em termos de corridas, campeonatos e gimmicks.
- O TimingSystem se destaca por suportar múltiplos tipos de veículo (barco, elytra, parkour) em time trials.
- A maioria das features "core" do TimingSystem já tem equivalente (ou superior) no FormulaRacing.
- Add-ons do TimingSystem (BlueMap, NoBoatCollisions, REST API, TrackExchange) — REST API e TrackExchange já embutidos no FormulaRacing; BlueMap e NoBoatCollisions (parcial) são os únicos gaps.

*Gerado por análise de código dos dois repositórios.*
