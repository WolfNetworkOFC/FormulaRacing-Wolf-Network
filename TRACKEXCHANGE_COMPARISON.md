# Comparação: TrackExchange (addon original) vs FormulaRacing (port embutido)

Comparação minuciosa da funcionalidade de importação/exportação de pistas (`.trackexchange`), comparando o addon original `FrostHexABG/TrackExchange` (para TimingSystem) com a implementação embutida no FormulaRacing (`TrackExchangeManager`).

---

## 1. Arquitetura geral

| Aspecto | TrackExchange (original) | FormulaRacing (port) |
|---------|--------------------------|----------------------|
| Forma | Plugin separado (addon) | Embutido no FormulaRacing |
| Dependência | TimingSystem + FAWE | Próprio (DatabaseManager, WorldEdit) |
| Comandos | `/trackexchange\|tex\|tx` copy/paste/undo/reload | Mesmos aliases e subcomandos |
| Async | `TaskChain` (aikar) | `SchedulerHelper.runAsync` |
| Undo stack | `HashMap<UUID, Stack<Runnable>>` estático | `ConcurrentHashMap<UUID, Stack<Runnable>>` de instância |
| Butler (host remoto) | ✅ Suportado | ❌ Não suportado |
| Migração de arquivos antigos | `moveTrackFiles()` move `.trackexchange` da raiz para `/tracks` | Não (usa pasta `trackexchange/` direto) |
| Context resolver de Track | Resolve `Track` via TimingSystemAPI | Usa `String trackName` + DB |

## 2. Comandos

### copy
- **Original:** `onCopy(Player, Track track, @Optional saveAs)`. Valida existência do arquivo e nome via regex `[A-Za-z0-9_]+`. Cria `ProcessSave`.
- **FormulaRacing:** `onCopy(Player, String trackName, @Optional saveAs)`. Mesma validação, mas `trackName` é String (resolvido depois via DB). Chama `exportTrack`.

Diferença: original recebe objeto `Track` (já validado pelo context resolver); FR resolve pelo nome no DB.

### paste
- **Original:** `onPaste(Player, String fileName, @Optional loadAs)`. Valida existência do arquivo, nome de pista disponível (`trackNameNotAvailable`), regex `[A-Za-z0-9 ]+`, e `ApiUtilities.checkTrackName`.
- **FormulaRacing:** `onPaste(Player, String fileName, @Optional loadAs)`. Valida existência (case-insensitive via `listFiles`), `db.isTrackExists`, regex `[A-Za-z0-9 ]+`. **Não chama** `ApiUtilities.checkTrackName` (não existe no FR; validação equivalente pode faltar).

### undo
- Ambos: pop da stack e executa o Runnable.

### reload
- **Original:** `reloadConfig()` + mensagem verde.
- **FormulaRacing:** apenas mensagem "Reloaded config." (não recarrega nada de fato).

## 3. Exportação (save)

### Original (ProcessSave)
- Usa `TaskChain` com estágios paralelos: `doTrackStage` (cria `TrackExchangeTrack` a partir do objeto `Track`) e `doSchematicStage` (captura seleção WorldEdit via `session.getSelection()`, cria `BlockArrayClipboard`, `ForwardExtentCopy` com `Operations.complete`).
- Se não houver seleção: `IncompleteRegionException` → salva sem schematic.
- `doWriteStage`: serializa `TrackExchangeFile` (data + track + schematic) e salva via `LocalFileSaver`/`ButlerFileSaver`.
- Schematic format: `FAST_V3` (`BuiltInClipboardFormat.FAST`? original define `CLIPBOARD_FORMAT = BuiltInClipboardFormat.FAST_V3`).

### FormulaRacing (doExport)
- Executa síncrono em async (sem TaskChain).
- Lê dados da pista direto do DB (`fr_tracks`, `fr_regions`, `fr_checkpoint`, tags, hologramas).
- Captura seleção WorldEdit via `session.getSelection(bukkitPlayer.getWorld())`, cria `BlockArrayClipboard`, `ForwardExtentCopy` com `Operations.completeLegacy`.
- Se não houver seleção: catch silencioso (log).
- Schematic format: `SPONGE_V3_SCHEMATIC` (`BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC`).
- ⚠️ **Diferença de formato de schematic**: original FAST_V3 vs FR SPONGE_V3_SCHEMATIC. Arquivos podem não ser 100% compatíveis em nível de schematic (mas o addon lê o campo `schematic_format` do JSON, então FR escreve `SCHEMATIC_FORMAT.getName()` e o addon tenta `BuiltInClipboardFormat.valueOf(...)`).

### Campos exportados
| Campo | Original | FormulaRacing |
|-------|----------|---------------|
| version | TRACK_VERSION (5) | TRACK_VERSION (5) |
| owner | Track owner UUID | creatorUUID do DB |
| guiItem | itemToString (Material) | Material name + amount + meta (extras) |
| weight | track.getWeight() | 0 (hardcoded) |
| trackType | track.getType() (BOAT/ELYTRA/PARKOUR) | "RACE" (hardcoded) |
| boatUtilsMode | track.getBoatUtilsMode().getId() | 0 (hardcoded) |
| spawn | Track spawn location | spawn do DB |
| origin | player location | player location |
| contributors | Track contributors (TPlayer UUIDs) | [creatorUUID] |
| regions | TrackRegion → TrackExchangeRegion | lê fr_regions (type, shape, AABB min/max, POLY points) |
| locations (checkpoints) | TrackLocation → TrackExchangeLocation | lê fr_checkpoint (min/max bounds) |
| tags | TrackTag → TrackExchangeTag | lê db.getTrackTags |
| options | TrackOption → TrackExchangeOption | vazio (não exporta options) |
| leaderboard (java/bedrock) | — (não exporta hologramas) | ✅ exporta como locations type LEADERBOARD |
| worldName | — | ✅ exporta worldName |
| schematic | FAST_V3 clipboard | SPONGE_V3_SCHEMATIC clipboard |
| clipboardOffset | offset do origin | offset do origin |

FormulaRacing exporta **mais** (leaderboards, worldName, guiItem amount/meta) mas **menos** (weight, boatUtilsMode, contributors reais, options, trackType real).

## 4. Importação (load)

### Original (ProcessLoad)
- TaskChain: `doReadStage` (lê arquivo, valida versão), `doTrackStage` (cria Track via `track.createTrack(player)` que persiste no DB e adiciona regiões/locations/tags/options/contributors), `doPasteStage` (cola schematic via `clipboard.paste(...)`).
- Undo: `createUndo()` empilha Runnable que desfaz schematic (session.undo) e remove track (`TrackDatabase.removeTrack`).

### FormulaRacing (doImport)
- Lê arquivo, valida versão (IOException se incompatível).
- Normaliza legacy JSON (`normalizeLegacyTrackJson`: flat region coords → minP/maxP, plain tag/option arrays → objetos).
- Cria track via `db.createTrack(name, spawnLocation, creatorName, creatorUUID)`.
- Persiste tags, icon (material/amount/meta), regions (AABB/POLY), checkpoints (com bounds ou 3x3 fallback), leaderboards (java/bedrock).
- Cola schematic via `pasteSchematic` (ClipboardHolder.createPaste, `Operations.completeLegacy`).
- Undo: `pushInverse` (desfaz schematic async + deleteTrack + removeTrackLeaderboard).

### Diferenças de import
| Aspecto | Original | FormulaRacing |
|---------|----------|---------------|
| Versão incompatível | RuntimeException | IOException (mensagem PT) |
| Schematic format default | SPONGE_SCHEMATIC (legado) | SCHEMATIC (usa SCHEMATIC_FORMAT = SPONGE_V3) |
| Clipboard offset | lê de dataJson (clipboardOffset) | lê de dataJson (clipboardOffset) |
| Offset aplicado | player location - origin | player location - origin (dx/dy/dz) |
| Regiões POLY | não suporta (só AABB?) | ✅ suporta POLY com points |
| Checkpoints bounds | centro 3x3 (original não tem bounds?) | bounds reais ou 3x3 fallback |
| Leaderboards | não importa (não exporta) | ✅ recria java/bedrock |
| Tags | importa | importa |
| Options | importa | ❌ não importa |
| Contributors | importa | ❌ não importa (só creator) |
| BoatUtilsMode | importa | ❌ não importa (hardcoded 0 no export) |
| Weight | importa | ❌ não importa (hardcoded 0) |
| TrackType | importa (BOAT/ELYTRA/PARKOUR) | ❌ não importa (hardcoded "RACE") |
| Undo schematic | session.undo(undoSession) | pasteSession.undo(undoSession) |
| Undo track | TrackDatabase.removeTrack | db.deleteTrack + removeTrackLeaderboard |
| Clear checkpoint times | não | ✅ clearCheckpointTimesForTrack + clearCheckpointsCache |
| Region reload | não | ✅ plugin.getRegionListener().reloadRegions() |
| World mismatch warning | não | ✅ avisa se mundo diferente |
| Normalização legacy | não | ✅ normalizeLegacyTrackJson |

## 5. Formato do arquivo (.trackexchange)

Ambos usam ZIP com 3 entries:
- `data.component` (JSON com version, schematic_format, clipboardOffset)
- `track.component` (JSON com dados da pista)
- `schematic.component` (bytes do schematic, opcional)

Compatível em estrutura. Diferenças:
- **schematic_format**: original FAST_V3 vs FR SPONGE_V3_SCHEMATIC. Se um arquivo gerado pelo original for importado no FR, o FR tenta `BuiltInClipboardFormat.valueOf("FAST_V3")` → pode falhar (FAST_V3 pode não existir como enum const com esse nome). Original usa `BuiltInClipboardFormat.FAST`? Precisa verificar. FR usa `SPONGE_V3_SCHEMATIC`.
- **version**: ambos 5. ✅ compatível.
- **track.component**: original tem campos owner, guiItem, weight, trackType, boatUtilsMode, spawn, origin, contributors, regions, locations, tags, options. FR tem os mesmos + worldName, guiItemAmount, guiItemMeta, spawn yaw/pitch, min/max checkpoint bounds. FR é superset (campos extras ignorados pelo addon? O addon lê via Gson, campos extras são ignorados). ✅ compatível para import no addon.

## 6. Undo

- **Original:** `playerActions` é `HashMap<UUID, Stack<Runnable>>` estático. Undo executa o Runnable que desfaz schematic (async) e remove track (sync via TrackDatabase.removeTrack).
- **FormulaRacing:** `playerActions` é `ConcurrentHashMap<UUID, Stack<Runnable>>` de instância. Undo similar, mas também remove leaderboard (`plugin.removeTrackLeaderboard`). E executa `db.deleteTrack`.

## 7. Pontos de atenção / possíveis melhorias no FormulaRacing

1. **Formato de schematic diferente** (SPONGE_V3_SCHEMATIC vs FAST_V3): pode causar incompatibilidade ao importar arquivos do addon original. Considerar alinhar para FAST_V3 (ou manter ambos via `schematic_format`).
2. **Não importa options, contributors, weight, boatUtilsMode, trackType**: exportação também não exporta options/contributors reais. Para paridade total com o addon, adicionar.
3. **reload não faz nada**: implementar reloadConfig real.
4. **Paste validation**: adicionar `ApiUtilities.checkTrackName` equivalente (já tem regex).
5. **Undo do schematic**: original usa `session.undo(undoSession)` em async; FR idem. OK.
6. **TaskChain vs SchedulerHelper**: funcionalmente equivalente.

## 8. Resumo

O port do FormulaRacing é **funcionalmente equivalente e em alguns pontos superior** (leaderboards, POLY regions, checkpoint bounds, world mismatch warning, legacy normalization). As principais lacunas são: incompatibilidade potencial do formato de schematic (SPONGE vs FAST), e campos não importados (options, contributors, weight, boatUtilsMode, trackType). O addon original suporta Butler (host remoto) e migração de arquivos, que o FR não tem.

*Comparação gerada por análise dos repositórios TrackExchange e FormulaRacing.*
