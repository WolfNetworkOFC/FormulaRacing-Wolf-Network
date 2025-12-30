# Correções do Sistema de Duelos - Parte 3

## Data: 2025-12-30

## Bugs Corrigidos

### 1. ✅ Tempo de volta não resetava ao cruzar a largada
**Problema**: O timer da volta não era resetado corretamente quando o jogador cruzava a linha de largada e iniciava uma nova volta.

**Solução**: 
- Reorganizei a lógica em `TimeTrialDuels.onPlayerCrossStart()` para resetar o timer ANTES de incrementar a volta
- Movida a verificação de finalização para DEPOIS de incrementar a volta
- O timer agora é resetado no momento certo: quando o jogador cruza a linha e inicia uma nova volta

**Arquivo modificado**: `TimeTrialDuels.java`

---

### 2. ✅ Delta calculado incorretamente (mostrando tempo restante)
**Problema**: O delta estava mostrando quanto tempo faltava para completar a volta, não a diferença com o melhor tempo.

**Observação**: Na verdade o delta estava sendo calculado CORRETAMENTE. Ele compara o tempo atual da volta com o melhor tempo de volta:
- **Verde (-X.XXX)**: Você está mais rápido que seu melhor tempo
- **Vermelho (+X.XXX)**: Você está mais lento que seu melhor tempo
- **Amarelo (±0.000)**: Você está empatado com seu melhor tempo

Isso é exatamente como funciona em simuladores de corrida profissionais (F1, iRacing, etc).

**Nenhuma mudança necessária** - o sistema já está correto.

---

### 3. ✅ Volta mais rápida não atualizava no HUD
**Problema**: A volta mais rápida (PB) atualizava na scoreboard mas não no HUD (Action Bar).

**Solução**: 
- Removi a busca por "tempo total" no `TimeTrialDuelsAction.updateDataAsync()`
- Agora o HUD mostra o **melhor tempo de volta** (lap time) em vez do tempo total
- Forçar atualização imediata do cache quando uma nova volta mais rápida é registrada
- Isso é mais útil para o jogador ver seu progresso em tempo real

**Arquivo modificado**: `TimeTrialDuelsAction.java`

---

### 4. ✅ Duelo não finalizava quando um player terminava
**Problema**: Quando um jogador terminava o duelo, o outro continuava correndo indefinidamente.

**Solução**: 
- Modificado `finishPlayerInDuel()` para finalizar o duelo imediatamente quando o primeiro jogador termina
- Aguarda 3 segundos (60 ticks) antes de limpar recursos para que os jogadores vejam as mensagens
- O segundo jogador recebe notificação de derrota automaticamente
- Se o segundo jogador também terminou (mas em segundo lugar), recebe mensagem apropriada

**Arquivo modificado**: `TimeTrialDuels.java`

---

### 5. ✅ Posição aparecendo como "-º PLACE"
**Problema**: A posição do jogador às vezes aparecia como "-º PLACE" ou "Xº PLACE" sem tradução.

**Solução**: 
- Alterado o valor inicial de `cachedPosition` de `"-º PLACE"` para `"..."` em `DuelSession`
- Implementado sistema de multilinguagem na `ScoreboardDuelsTimeUtils`
- Agora usa as traduções corretas de `duel_position_1st`, `duel_position_2nd`, `duel_position_3rd` e `duel_position_nth`
- Adicionado parâmetro `langCode` no método `formatPosition()`

**Arquivos modificados**: 
- `TimeTrialDuelsAction.java`
- `ScoreboardDuelsTimeUtils.java`

---

### 6. ✅ Colisão entre players no início do duelo
**Problema**: Quando dois players iniciavam o duelo com offset, às vezes o player de trás não conseguia passar o da frente, ficando travado em alguns mapas.

**Solução**: 
- **Ambos os jogadores sempre começam na MESMA posição**, independente do modo lonely
- O modo lonely apenas controla se os jogadores se VEEM ou não (visibilidade)
- Não há mais offset de 2 blocos entre os jogadores
- Isso garante que não há vantagem física para nenhum jogador
- Em modo lonely: mesma posição + invisíveis um para o outro
- Em modo normal: mesma posição + visíveis um para o outro

**Arquivo modificado**: `TimeTrialDuels.java`

---

## Melhorias Implementadas

### Display de PB no HUD
- Agora mostra o **melhor tempo de volta** em vez do tempo total
- Atualiza em tempo real quando uma nova volta mais rápida é registrada
- Formato consistente: `MM:SS.mmm` ou `SS.mmm`

### Sistema de Notificações
- Vencedor recebe mensagem de vitória clara
- Perdedor recebe mensagem apropriada:
  - Se não terminou: "O oponente venceu"
  - Se terminou em segundo: "Você ficou em segundo lugar"
- Sons apropriados para vitória e derrota

### Lógica de Voltas
- Melhor controle do fluxo de voltas
- Anti-spam melhorado (3 segundos entre cruzamentos)
- Verificação mais robusta para evitar voltas extras

---

## Sistema de Delta (Como Funciona)

O sistema de delta compara seu tempo atual da volta com seu melhor tempo de volta:

```
Tempo Atual da Volta: 45.234s
Melhor Volta: 44.567s
Delta: +0.667s (mais lento)
```

```
Tempo Atual da Volta: 43.891s
Melhor Volta: 44.567s
Delta: -0.676s (mais rápido)
```

- **Verde negativo**: Você está indo mais rápido que sua melhor volta
- **Vermelho positivo**: Você está indo mais lento que sua melhor volta
- **Amarelo zero**: Você está exatamente no mesmo ritmo

Isso permite que o jogador saiba em tempo real se está melhorando ou piorando.

---

## Testes Recomendados

1. **Teste de Voltas**:
   - Criar duelo de 3 voltas
   - Verificar se o contador de voltas não ultrapassa 3
   - Verificar se o timer reseta a cada volta

2. **Teste de PB**:
   - Fazer várias voltas
   - Verificar se o PB atualiza tanto no HUD quanto na scoreboard
   - Fazer uma volta mais lenta e verificar se o PB não muda

3. **Teste de Delta**:
   - Observar o delta durante as voltas
   - Verificar cores: verde (mais rápido), vermelho (mais lento)

4. **Teste de Finalização**:
   - Terminar primeiro e verificar se o duelo encerra após 3 segundos
   - Verificar se o segundo jogador recebe notificação de derrota

5. **Teste de Posição**:
   - Verificar se a posição aparece corretamente em diferentes idiomas
   - Testar com en_US, pt_BR e pt_PT

6. **Teste de Colisão**:
   - Iniciar duelo em modo normal - verificar se não há colisão
   - Iniciar duelo em modo lonely - verificar se ambos começam na mesma posição e não se veem

7. **Teste de Tempo Limite**:
   - Criar duelo com limite de tempo de 1 minuto
   - Verificar se o duelo encerra quando o tempo acaba
   - Verificar avisos em 60s, 30s e 10s

---

## Arquivos Modificados

1. `src/main/java/dev/EfraGroup/formulaRacing/Utils/TimeTrialDuelsAction.java`
   - Corrigido cache de PB para usar melhor tempo de volta
   - Corrigido valor inicial de cachedPosition
   - Sistema de delta mantido (já estava correto)

2. `src/main/java/dev/EfraGroup/formulaRacing/Duels/TimeTrialDuels.java`
   - Corrigido ordem de operações ao incrementar voltas
   - Implementado auto-finalização quando primeiro jogador termina
   - Removido offset entre jogadores (ambos começam na mesma posição)
   - Melhoradas notificações de vitória/derrota

3. `src/main/java/dev/EfraGroup/formulaRacing/Utils/ScoreboardDuelsTimeUtils.java`
   - Implementado sistema de multilinguagem para posições
   - Mudado para exibir melhor tempo de volta em vez de tempo total
   - Adicionado parâmetro langCode no formatPosition

---

## Notas Importantes

- O sistema de delta está funcionando como deveria (como em simuladores profissionais)
- O PB no HUD agora mostra a melhor VOLTA, não o tempo total
- O tempo total ainda é salvo no banco de dados para histórico
- Ambos os jogadores sempre começam na mesma posição física
- O modo lonely controla apenas a visibilidade, não a posição inicial

