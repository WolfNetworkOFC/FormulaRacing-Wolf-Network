# Teste — Melhorias do Sistema de Eventos

Este documento descreve os procedimentos de teste in-game para validar as 9 melhorias implementadas no sistema de eventos/rounds/heats do FormulaRacing.

**Ambiente:** Servidor Paper/Spigot local com `/reload` entre testes de persistência.
**Jogadores:** Mínimo 2 jogadores para testes de assinaturas e grid.

---

## Teste 1: HeatStateMachine — Transições de Qualificação

**Hipótese:** `QualifyingSession.start()` não deve mais lançar `IllegalStateException` quando o heat transita para QUALIFYING.

### Procedimento

1. Criar evento com track:
   ```
   /event create TestQuali <nome_da_track>
   ```

2. Criar round QUALIFICATION:
   ```
   /round create <event> QUALIFICATION
   ```

3. Criar heat:
   ```
   /heat create <round>
   ```

4. Adicionar drivers ao heat:
   ```
   /heat adddriver <heat> <player1>
   /heat adddriver <heat> <player2>
   ```

5. Carregar heat (gera grid):
   ```
   /heat load <heat>
   ```

6. **INICIAR CONTAGEM — PASSO CRÍTICO:**
   ```
   /heat start <heat>
   ```
   - Esperado: Contagem regressiva começa, heat entra em estado QUALIFYING
   - Antes da correção: `IllegalStateException: Illegal HeatState transition: SETUP -> QUALIFYING`

7. Aguardar quali terminar (time limit expira ou admin força finish):
   ```
   /heat finish <heat>
   ```

8. **Testar re-qualificação (reset → novo heat):**
   ```
   /heat reset <heat>
   /heat load <heat>
   /heat start <heat>
   ```
   - Esperado: Funciona sem exceção

### Critério de Pass
```
✓ Heat transita SETUP → LOADED → STARTING → QUALIFYING sem IllegalStateException
✓ Re-qualificação (FINISHED → SETUP → QUALIFYING) funciona
```

---

## Teste 2: Persistência de Configuração de Heat

**Hipótese:** Configurações avançadas de heat (DRS, P2P, collision, ghosting, etc.) sobrevivem ao restart do servidor.

### Procedimento

1. Criar heat:
   ```
   /heat create <round>
   ```

2. **Configurar heat com todas as opções avançadas:**
   ```
   /heat set drs <heat> true
   /heat set pushtopass <heat> true
   /heat set collision <heat> HIGH
   /heat set ghostingDelta <heat> 500
   /heat set maxdrivers <heat> 10
   /heat set startdelay <heat> 10
   /heat set timelimit <heat> 300
   /heat set reversegrid <heat> true
   ```

3. **Registrar todas as configurações:**
   ```
   /heat info <heat>
   ```
   Anotar valores exibidos.

4. **Carregar heat (salva configs no DB):**
   ```
   /heat load <heat>
   ```
   - As configurações são salvas automaticamente ao carregar o heat
   - Alternativamente, `/heat start <heat>` também persiste antes de iniciar

5. **REINICIAR O SERVIDOR COMPLETAMENTE:**
   ```
   stop
   (iniciar servidor novamente)
   ```

6. Após reload, consultar novamente:
   ```
   /heat info <heat>
   ```

7. **Comparar valores antes e depois do restart.**

### Critério de Pass
```
✓ DRS permanece true após restart
✓ Push-to-Pass permanece true após restart
✓ Collision mode permanece HIGH após restart
✓ Ghosting delta permanece 500 após restart
✓ Max drivers permanece 10 após restart
✓ Start delay, timelimit, reversegrid mantidos

ANTES: Todas voltavam ao default (null/false/0) após restart
```

---

## Teste 3: Persistência de Subscribers e Reserves

**Hipótese:** Jogadores inscritos e reservas permanecem após restart.

### Procedimento

1. Criar evento:
   ```
   /event create TestSubs <track>
   ```

2. Abrir inscrições:
   ```
   /event set signs open <event>
   ```

3. Player 1 assina:
   ```
   /event sign <event>
   ```

4. Player 2 se torna reserva:
   ```
   /event reserve <event>
   ```

5. Verificar ambos:
   ```
   /event signs <event>
   ```
   Anotar os nomes.

6. **REINICIAR O SERVIDOR COMPLETAMENTE**

7. Após reload, consultar:
   ```
   /event signs <event>
   ```

### Critério de Pass
```
✓ Ambos jogadores aparecem como inscrito/reserva após restart
✓ Ordem de inscrição preservada (se aplicável)

ANTES: Mapa de subscribers/reserves vazio após restart
```

---

## Teste 4: EventStateMachine — Transições de Estado

**Hipótese:** Transições inválidas de estado de evento são bloqueadas com `IllegalStateException`.

### Procedimento

1. Criar evento:
   ```
   /event create TestState <track>
   ```

2. Iniciar evento (SETUP → RUNNING):
   ```
   /event start <event>
   ```
   - Esperado: Funciona normalmente

3. Tentar transição inválida (via console ou código):
   ```java
   // Via /reload ou código temporário
   event.setState(EventState.SETUP);  // RUNNING → SETUP é inválido
   ```

4. Verificar console:
   ```
   [FormulaRacing] Illegal EventState transition: RUNNING -> SETUP
   ```

### Critério de Pass
```
✓ SETUP → RUNNING funciona
✓ RUNNING → FINISHED funciona
✓ Transições inválidas disparam IllegalStateException com mensagem clara
✓ Nenhuma transição protected (SETUP → FINISHED, etc.) é permitia
```

---

## Teste 5: QualificationManager — Grid do Final

**Hipótese:** O grid do final é preenchido corretamente via `addDriver()`, com limpeza de drivers antigos e sem orphan records no DB.

### Procedimento

1. Criar evento completo (Practice + Quali + Final):
   ```
   /event createfull TestQualiGrid <track> 300 3 180 5 2
   ```

2. Inscrição — 4+ players fazem:
   ```
   /event sign <event>
   ```

3. Practice round — pular ou deixar terminar

4. Qualification round:
   ```
   /round start <quali-round>
   ```
   - Players completam voltas válidas

5. Finalizar quali:
   ```
   /round finish <quali-round>
   ```
   - Verificar log do console: "Processando resultados da qualificação..."
   - Verificar: "Grid de largada definido com sucesso!"

6. Inspect final heat:
   ```
   /heat info <final-heat>
   ```

7. **Verificações no DB (opcional):**
   ```sql
   -- Verificar drivers duplicados no final heat
   SELECT uuid, COUNT(*) as c FROM fr_drivers WHERE heatId = <final-heat-id> GROUP BY uuid HAVING c > 1;
   ```

### Critério de Pass
```
✓ Todos os drivers que completaram volta válida aparecem no grid final
✓ Drivers ordenados por melhor tempo (mais rápido = P1)
✓ Sem drivers duplicados no DB
✓ DriverLookup registrado para todos os drivers do grid final
✓ maxDrivers respeitado se número de inscritos exceder limite

ANTES: Podia haver drivers órfãos no DB, duplicados, ou DriverLookup desatualizado
```

---

## Teste 6: PracticeRound Broadcast

**Hipótese:** Resultados de practice são anunciados aos jogadores e espectadores ao invés de apenas logados no console.

### Procedimento

1. Criar evento com practice:
   ```
   /event createfull TestPractice <track> 300 ...
   ```

2. Inscrição — players fazem `/event sign`

3. Iniciar practice:
   ```
   /round start <practice-round>
   ```

4. Players completam voltas

5. Finalizar practice:
   ```
   /round finish <practice-round>
   ```

6. **Verificar mensagens no chat:**
   - Cada player deve ver: `P1: Nome - Tempo`, `P2: Nome - Tempo`, etc.
   - Espectadores devem ver: `P1 Practice: Nome - Tempo`

### Critério de Pass
```
✓ Players veem ranking individual no chat após practice finalizar
✓ Top 3 anunciados para seluruh espectadores
✓ Tempos formatados corretamente (M:SS.mmm)

ANTES: Apenas log no console "Resultados do Treino Livre (Practice) - [Implementar Broadcast]"
```

---

## Teste 7: Soft-Delete Pattern

**Hipótese:** Eventos deletados são marcados como removidos no DB em vez de serem apagados, permitindo recuperação e audit trail.

### Procedimento

1. Criar evento:
   ```
   /event create TestSoftDelete <track>
   ```

2. Deletar evento:
   ```
   /event delete <event>
   ```

3. Verificar que não aparece mais na lista:
   ```
   /event list
   ```

4. **Verificar no banco de dados:**
   ```sql
   -- Conectar no SQLite
   sqlite3 <data-folder>/database.db

   -- Verificar registro
   SELECT id, name, state, isRemoved FROM fr_events WHERE name = 'TestSoftDelete';
   ```

### Critério de Pass
```
✓ Evento não aparece em /event list
✓ isRemoved = 1 no banco de dados
✓ state = 'FINISHED' no banco de dados
✓ Dados relacionados (rounds, heats, drivers) também marcados ou preservados

ANTES: DELETE físico — registro sumia completamente do DB
```

---

## Checklist Resumida

| # | Teste | Critério de Pass | Prioridade |
|---|---|---|---|
| 1 | QUALIFYING transitions | Heat entra em QUALIFYING sem `IllegalStateException` | CRÍTICA |
| 2 | Heat config persistence | DRS/P2P/collision/ghosting mantidos após restart | CRÍTICA |
| 3 | Subscriber persistence | Inscritos/reservas aparecem após restart | CRÍTICA |
| 4 | EventStateMachine | Transições inválidas são rejeitadas | MODERADA |
| 5 | Grid do final | Drivers правильно posicionados, sem duplicados | MODERADA |
| 6 | Practice broadcast | Mensagens aparecem no chat | MENOR |
| 7 | Soft-delete | Registro marcado com isRemoved=1 no DB | MODERADA |

---

## Problemas Conhecidos (não implementados)

- **Task 10 (naming cleanup):** Não implementada. Métodos como `setrealistc()`, `setpushtopasspower()` mantêm nomes decompilados. Funciona normalmente, apenas estética do código.
