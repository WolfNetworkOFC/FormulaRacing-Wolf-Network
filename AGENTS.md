# AGENTS.md

Agent guidance for this repository (`FormulaRacing`).

## Project Snapshot
- Language: Java 21
- Build tool: Maven (`pom.xml`)
- Artifact type: Spigot/Paper plugin JAR
- Main class: `dev.EfraGroup.formulaRacing.FormulaRacing`
- Resource entrypoint: `src/main/resources/plugin.yml`
- Test source folder: not present (`src/test/java` is missing)

## Environment and Prerequisites
- Install JDK 21 and ensure `java -version` reports 21.x.
- Install Maven (no `mvnw` wrapper is committed).
- Run commands from repository root.
- On Windows PowerShell, use the same Maven commands.

## Build Commands

### Fast compile check
- `mvn -DskipTests compile`
- Use this as default validation for quick iterations.

### Create packaged JAR (full build)
- `mvn clean package`
- Note: `package` phase runs `maven-shade-plugin` and also `wagon-maven-plugin` upload step.
- The upload step targets `serverId=meu-servidor-minecraft` and may fail without credentials.

### Safer packaging for local/dev agents
- Prefer `mvn -DskipTests compile` unless packaging is explicitly required.
- If packaging is required, expect potential failure at upload step due to missing deployment credentials.

## Test Commands

### Run all tests
- `mvn test`
- Current repository has no tests; this usually results in "no tests to run" behavior.

### Run a single test class (when tests are added)
- `mvn -Dtest=MyTestClass test`

### Run a single test method (when tests are added)
- `mvn -Dtest=MyTestClass#myTestMethod test`

### Avoid failing when no tests exist
- `mvn -DfailIfNoTests=false test`

## Lint / Static Analysis
- No dedicated lint tool (Checkstyle/SpotBugs/PMD) is configured in `pom.xml`.
- Treat `mvn -DskipTests compile` as the minimum quality gate.
- If adding a linter, keep config minimal and consistent with existing code style before enforcing.

## Repo Layout (High-Level)
- `src/main/java/dev/EfraGroup/formulaRacing/` core plugin code
- `Command/` ACF command handlers
- `Listener/` Bukkit event listeners (high-frequency logic lives here)
- `Controllers/` orchestration and race-flow managers
- `Database/` SQL and persistence code
- `Gui/` inventory/menu systems
- `Heat/`, `Round/`, `Event/` racing domain model
- `src/main/resources/config.yml` main config
- `src/main/resources/lang/*.yml` translations

## Code Style Guidelines

### General style
- Keep existing package naming as-is: `dev.EfraGroup.formulaRacing` (case-sensitive).
- Use 4-space indentation; do not introduce tabs.
- Keep braces and formatting consistent with surrounding file style.
- Prefer small, focused changes over broad rewrites in this codebase.

### Imports
- Avoid wildcard imports except where already intentionally used.
- Keep import groups stable within each file (JDK, third-party, Bukkit, project-local), following existing local ordering.
- Remove unused imports in touched files.

### Naming
- Classes: `PascalCase` (`RaceMovementListener`, `QuickRaceManager`).
- Methods/fields: `camelCase` (`getRaceEventManager`, `trackVisualizer`).
- Constants: `UPPER_SNAKE_CASE` for new true constants.
- Preserve established domain terms (`heat`, `round`, `event`, `trackNameWS`, etc.).

### Types and APIs
- Target Java 21 syntax where it improves clarity and matches project usage.
- Prefer interfaces for references (`List`, `Map`) unless concrete type is required.
- Use generics explicitly for new collections.
- Prefer `Optional` patterns already used by managers over nullable ad-hoc returns when practical.

### Nullability and defensive checks
- Bukkit API objects may be null depending on lifecycle/chunk load context.
- Guard `Player`, `World`, entity passenger access, and DB lookups before use.
- In listeners, fail fast and return early to reduce nesting and tick cost.

### Error handling
- Never swallow exceptions silently.
- Log concise, actionable messages through plugin logger/debug manager.
- Include context (track/event/player identifiers) in error logs.
- Use try-with-resources for SQL (`Connection`, `PreparedStatement`, `ResultSet`).

### Concurrency and performance
- Assume TPS target is 20; avoid heavy work on main thread.
- `VehicleMoveEvent` and movement listeners are hot paths: no expensive I/O or complex allocations there.
- Offload database/heavy computation to async tasks, then switch back to main thread for Bukkit state mutation.
- Do not call Bukkit world/entity mutating APIs from async threads.

### Commands and user messages
- Follow existing ACF patterns (`@Subcommand`, `@CommandPermission`, `@CommandCompletion`).
- Prefer translation keys via `plugin.sendMessage(...)` over hardcoded player-facing text.
- Keep permission nodes and aliases consistent with `plugin.yml`.

### Config and resources
- If adding config keys, update defaults in `src/main/resources/config.yml`.
- If adding user-facing text, update all language files (`en_US.yml`, `pt_BR.yml`, `pt_PT.yml`) when possible.
- Keep YAML keys stable; avoid breaking existing saved configs unless migration is provided.

## Copilot/Cursor Rule Integration

### Detected Copilot instructions
From `.github/copilot-instructions.md`:

1. Do not create new Markdown/docs unless explicitly requested.
   - Exception here: this `AGENTS.md` is explicitly requested by the user.
2. Be concise in task summaries.
3. Prioritize Paper/Spigot compatibility and thread safety.
4. Keep high-frequency listener logic highly performant.
5. Debugging protocol:
   - state a hypothesis first,
   - add only 2-3 strategic logs,
   - mentally dry-run for gameplay side effects,
   - verify API/version compatibility.

### Cursor rules
- No `.cursorrules` file found.
- No `.cursor/rules/` directory found.

## Agent Workflow Recommendations
- Before edits, inspect neighboring classes for local conventions.
- For behavior changes in listeners/controllers, validate likely tick/thread impact.
- For persistence changes, verify SQL path and potential migration impact.
- Prefer compile check after edits: `mvn -DskipTests compile`.
- If adding tests later, run targeted tests first, then full suite.

## Known Build Caveats
- `mvn` may not be installed in some environments.
- `mvn clean package` can fail on upload step if SFTP credentials are absent.
- Repository includes generated artifacts (`target/`, JARs) locally; do not assume they are source of truth.

## Definition of Done for Agent Changes
- Code compiles (or change is syntactically coherent if Maven unavailable).
- Threading model is safe for Bukkit/Paper APIs.
- No heavy logic added to hot movement events without justification.
- Commands/messages/config updates remain consistent across plugin resources.
- Logs are useful but not spammy.
