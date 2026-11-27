# Problemas da "linha do PB" (ghost) — GhostManager

Referência: `src/main/java/dev/EfraGroup/formulaRacing/Ghost/GhostManager.java`
A linha do PB é desenhada no replay do ghost como uma trilha de partículas DUST
interpolada entre frames, visível só para o dono. (Antes eram corações + um
`ArmorStand` teleportado frame a frame.)

## Status

| # | Problema | Status |
|---|----------|--------|
| 1 | Coração não forma linha (HEART deriva/dispersa) | ✅ Corrigido |
| 2 | Sem interpolação (pontos discretos a cada 2 ticks) | ✅ Corrigido |
| 3 | Oclusão por blocos + Y alto (`+0.5`) | ✅ Corrigido |
| 4 | Culling de distância (sem `force`/`longDistance`) | ✅ Corrigido |
| 5 | `ArmorStand` é peso morto (entidade + teleport sem função) | ✅ Corrigido |
| 6 | Timing assíncrono (`teleportAsync` sem garantia de ordem) | ✅ Corrigido |
| 7 | Só o dono vê / cor fixa (sem cor por equipe/status) | ⬜ Pendente |
| 8 | Cross-play: HEART não renderiza em Bedrock/Geyser e some com partículas "Minimal" | ⬜ Pendente |
| 9 | Performance: 10 Hz por sessão, `runTaskAt` por frame | ✅ Corrigido |
| 10 | Frames esparsos (lag) não validados | ✅ Corrigido |

## Correções aplicadas

1. **Linha do PB agora é DUST estático (não coração)**
   - `PB_LINE_DUST = new Particle.DustOptions(Color.fromRGB(255, 64, 64), 1.0f)`.
   - `HEART` nunca é usado: `resolveLineDust()` cai no DUST estático quando não há
     `dust` de medalha. Medalha continua com o DUST dela.

2. **Interpolação contínua entre frames**
   - `drawSegment(...)` preenche o trecho entre o frame anterior (`lastDrawn`) e o
     atual com pontos a cada `SEGMENT_POINT_SPACING = 0.35` bloco, no máximo
     `MAX_POINTS_PER_SEGMENT = 48` pontos.
   - Trechos maiores que `MAX_SEGMENT_DISTANCE = 8.0` blocos (teleporte/lag) não
     são pintados como reta falsa.

3. **Y alto + oclusão no terreno**
   - Offset reduzido de `+0.5` para `LINE_Y_OFFSET = 0.2`, então a linha senta no
     traçado do barco em vez de flutuar.
   - `resolveVisibleY(...)` levanta até `OCCLUSION_EPSILON = 0.05` acima do topo do
     bloco quando um ponto cai dentro de bloco sólido (`isOccluding`), evitando que
     a linha seja engolida pelo terreno. Pontos acima da superfície não mudam.
   - Nota: oclusão de partículas por muros/terreno *entre a câmera e a linha* é
     limitação do renderizador do cliente e não tem flag de servidor; o que dava
     para corrigir (Y e pontos enterrados) foi corrigido.

4. **Culling de distância**
   - Spawn agora usa o overload com `force=true`:
     `spawnParticle(Particle.DUST, x, y, z, 1, 0,0,0,0, lineDust, true)`.
     Isso desliga o corte de ~32 blocos, então a linha continua visível atrás do
     jogador.

5. **ArmorStand removido**
   - A sessão não cria mais entidade nem faz `teleportAsync`; spawna partículas
     direto em `targetLoc`. Acabou o custo de entidade/pacotes e o risco de
     vazamento. `removeStand()` virou `stop()`.

6. **Ordenação assíncrona**
   - Sem `teleportAsync`, não há reordenação async. As partículas são enviadas em
     tarefas de região (`runTaskFor` do jogador), que executam em ordem por região.

7. **Performance (#9)**
   - Desenho movido para `SchedulerHelper.runTaskFor(plugin, player, ...)` (região
     do jogador), igual ao `AILineVisualizer` — acesso a `getLocation`/
     `spawnParticle` no thread correto.
   - Culling por distância: `MAX_LINE_DISTANCE_SQ = 64²`. Pontos além de 64 blocos
     do jogador não são enviados (mesmo com `force`), então o `force=true` não vira
     spam de pacotes quando o ghost está muito à frente/atrás.

8. **Validação da gravação (#10)**
   - `hasTeleportSpike(frames)` detecta saltos impossíveis entre frames consecutivos
     (`MAX_RECORD_FRAME_STEP = 16` blocos por frame de 2 ticks — acima da velocidade
     real, só pega teleporte/lag).
   - Aplicado em `stopRecording` (descarta a gravação do PB) e em `saveGhostAsync`
     (rejeita qualquer origem, inclusive upload do cliente no `WolfTimingService`).
     O tempo do PB é preservado; só o ghost corrompido não é salvo.

## O que falta (pendente)

- **#7 Só o dono vê + cor fixa**: não serve como linha compartilhada/espectadores, e
  o PB tem cor fixa (sem distinguir por equipe/status).
- **#8 Cross-play**: HEART já não é usado, mas vale confirmar DUST no Bedrock/Geyser e
  o caso de partículas "Minimal/Off" no cliente.
