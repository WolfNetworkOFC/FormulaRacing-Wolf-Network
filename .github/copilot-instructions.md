# Copilot Custom Instructions

- Do not create new Markdown files or documentation files in the repository unless explicitly requested. Keep all logic and explanations within the chat interface, not in the project files.
- Be concise. After completing a task, provide only a one-sentence summary of what was changed. Do not list bugs fixed or provide long explanations unless explicitly asked.
- Prioritize code stability and compatibility with the Paper/Spigot API. Before suggesting a change, verify if it affects existing logic or introduces thread-safety issues (especially regarding asynchronous tasks and the Minecraft main thread). If a solution requires modifying multiple files, explain the impact before proceeding to avoid breaking dependencies.
- This project is a Minecraft plugin for Ice Boat Racing. Keep in mind that performance is critical (TPS must stay at 20) and physics-related events (VehicleMoveEvent) are triggered frequently. Always suggest the most performant way to handle boat physics and avoid heavy computations inside high-frequency listeners.

Debugging Protocol

Hypothesis First: Before suggesting any code changes or logs, state a brief hypothesis of why the bug is occurring.

Surgical Logging: Do not spam logs. If debugging is needed, suggest only 2 or 3 strategic getLogger().info() or debug() points that target the specific hypothesis.

Dry Run: Before providing the fix, mentally simulate the execution flow and check if the fix breaks common Minecraft mechanics (e.g., event priority, null players, or unloaded chunks).

Check Versions: Always verify if the methods used are compatible with the specific Paper/Spigot version of this project.