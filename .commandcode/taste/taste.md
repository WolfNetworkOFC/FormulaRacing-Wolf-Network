# Taste (Continuously Learned by [CommandCode][cmd])

[cmd]: https://commandcode.ai/

# localization
- For hotbar item lores: remove the "Right-click" / "Clique com o botão direito" hint line. Confidence: 0.70

# communication
- Communicate in Brazilian Portuguese (pt-BR). Confidence: 0.80
- When presenting ideas or items, explain what each thing is and what purpose it serves — avoid just listing jargon without context. Confidence: 0.85
- The user themselves prefers extremely brief, direct instructions with no pleasantries, greetings, or excessive context — just state the task and expect autonomous execution. Confidence: 0.70
- When reporting cross-file diagnostic findings, use a layered structured report: (1) enumerate the subsystem layers involved, (2) pinpoint exact `file:line` locations, (3) give a proposed fix with code snippet and reasoning, (4) close with a quick-verification checklist. Prefer this over flat prose. Confidence: 0.65

# ai-model
- Use Llama 3.1 1B with maximum quantization for lightweight local AI. Confidence: 0.50

# platform
- Operates on Windows — Git for Windows, winget for package installs (including JDK 21 via winget), and Windows-style paths (drive letters, backslashes, `cmdkey`/Credential Manager for git auth). Confidence: 0.75

# workflow
See [workflow/taste.md](workflow/taste.md)
