# visual
- For item icon arguments specified via `[key=value]` syntax (e.g. `LIGHT[level=15]`): apply them as actual functional item/block metadata rather than rendering them as visible text in the item lore — the user expects the arguments to modify the icon's properties (block level, custom model data, etc.), not to be displayed in the tooltip. Confidence: 0.85
- For difficulty color-coding on item tooltips: differentiate by level using a light green→yellow→red→purple gradient (EASY=light green `§a`, MEDIUM=yellow `§e`, HARD=red `§c`, INSANE=dark purple `§5`) instead of using a single uniform color; the "EXTREME" difficulty level is displayed in-game as "INSANE". Confidence: 0.85# localization
- For hotbar item lores: remove the "Right-click" / "Clique com o botão direito" hint line. Confidence: 0.70
- For item lore lines: remove redundant label prefixes (e.g. "Tags:") and display the raw data values directly, keeping tooltips minimal and uncluttered. Confidence: 0.80

# visual
- For difficulty color-coding on item tooltips: differentiate by level using a light green→yellow→red→purple gradient (EASY=light green `§a`, MEDIUM=yellow `§e`, HARD=red `§c`, INSANE=dark purple `§5`) instead of using a single uniform color; the "EXTREME" difficulty level is displayed in-game as "INSANE". Confidence: 0.85# Taste (Continuously Learned by [CommandCode][cmd])

[cmd]: https://commandcode.ai/

# localization
- For hotbar item lores: remove the "Right-click" / "Clique com o botão direito" hint line. Confidence: 0.70

# communication
See [communication/taste.md](communication/taste.md)
# ai-model
- Use Llama 3.1 1B with maximum quantization for lightweight local AI. Confidence: 0.50

# platform
See [platform/taste.md](platform/taste.md)
# workflow
See [workflow/taste.md](workflow/taste.md)
