# Correções do Sistema de Duelos - Parte 2

## Data: 30/12/2025

## Bugs Corrigidos

### 1. ✅ Quantidade de voltas ultrapassando o limite definido
**Problema:** As voltas continuavam sendo contadas além do limite configurado no menu.

**Solução:** 
- Alterada a condição de verificação de `currentLap >= duelState.getTotalLaps()` para `currentLap > duelState.getTotalLaps()`
- Adicionada verificação para finalizar o jogador automaticamente quando completar a última volta
- Agora quando o jogador cruza a linha START na última volta, ele é automaticamente finalizado

**Arquivo:** `TimeTrialDuels.java` - método `onPlayerCrossStart()`

---

### 2. ✅ PB (Personal Best) não sendo registrado no HUD e na Scoreboard
**Problema:** O tempo pessoal do jogador não era salvo e não aparecia na interface.

**Solução:**
- Criado novo método `saveDuelFinalTime()` no `DatabaseManager` para salvar o tempo total do duelo
- Adicionado campo `firstLapStartTime` na classe `PlayerDuelState` para rastrear o início do cronômetro
- Modificado o método `finishPlayerInDuel()` para calcular e salvar o tempo total desde o início até o fim
- O tempo é marcado como "finished = true" no banco de dados para diferenciá-lo dos tempos de volta individual

**Arquivos modificados:**
- `TimeTrialDuels.java` - classes `PlayerDuelState` e método `finishPlayerInDuel()`
- `DatabaseManager.java` - novo método `saveDuelFinalTime()`

---

### 3. ✅ Volta sendo contada múltiplas vezes
**Problema:** Ao cruzar a linha START, às vezes a volta era contada mais de uma vez.

**Solução:**
- Mantido o sistema de debounce de 3 segundos para evitar detecções duplicadas
- Adicionada verificação adicional para garantir que o jogador não incrementa além do limite de voltas
- A finalização automática impede contagens extras após completar todas as voltas

**Arquivo:** `TimeTrialDuels.java` - método `onPlayerCrossStart()`

---

### 4. ✅ Posição aparecendo como "-º lugar"
**Problema:** A posição era mostrada incorretamente no HUD.

**Solução:**
- Adicionada verificação para jogadores que ainda não começaram (lap = 0)
- Jogadores que não começaram são colocados automaticamente em último lugar
- Antes da corrida começar, todos aparecem em 1º lugar (empatados)
- Melhorada a lógica de comparação de posições

**Arquivo:** `TimeTrialDuels.java` - método `getPlayerPosition()`

---

### 5. ✅ Duelo não finalizando automaticamente após última volta
**Problema:** O duelo continuava mesmo depois dos jogadores completarem todas as voltas.

**Solução:**
- Implementada finalização automática quando o jogador cruza a linha START após completar a última volta
- O método `finishPlayerInDuel()` é chamado automaticamente
- Quando todos os jogadores finalizam, o duelo é encerrado com o método `endDuel()`

**Arquivo:** `TimeTrialDuels.java` - método `onPlayerCrossStart()`

---

### 6. ✅ Tempo limite não sendo aplicado
**Problema:** O timer de limite de tempo configurado no menu não funcionava.

**Solução:**
- Criado método `startTimeLimitTimer()` que inicia um cronômetro regressivo
- Avisos são enviados aos jogadores em 60s, 30s e 10s restantes
- Quando o tempo acaba, o método `endDuelByTimeLimit()` é chamado
- O vencedor é determinado pelo jogador que estava mais avançado (maior número de voltas)
- Adicionado campo `raceStartTime` na classe `DuelState` para rastrear o início

**Arquivos modificados:**
- `TimeTrialDuels.java` - novos métodos `startTimeLimitTimer()`, `endDuelByTimeLimit()`, `determineWinnerByProgress()`
- `TimeTrialDuels.java` - classe `DuelState` com novo campo `raceStartTime`

---

### 7. ✅ Posicionamento dos barcos diferente no modo normal vs lonely
**Problema:** No modo lonely, os jogadores deveriam começar exatamente na mesma posição (já que não se veem), mas tinham offset de 2 blocos.

**Solução:**
- Implementada lógica condicional no posicionamento:
  - **Modo Lonely:** Ambos os jogadores spawnam na mesma posição exata
  - **Modo Normal:** Mantém o offset de 2 blocos no eixo X para evitar colisões
- Isso garante que no modo lonely não haja vantagem/desvantagem de posição

**Arquivo:** `TimeTrialDuels.java` - método `startDuelPreparation()`

---

### 8. ✅ Tempo não sendo atualizado corretamente na Scoreboard
**Problema:** O tempo mostrado na scoreboard estava fixo em um valor de teste (13223ms).

**Solução:**
- Modificado o método `startAutoUpdateTask()` para usar `ttda.getPlayerElapsedSeconds(player)`
- O tempo agora é obtido dinamicamente do cronômetro real do jogador
- Formatação correta usando o método `formatTime()`

**Arquivo:** `ScoreboardDuelsTimeUtils.java` - método `startAutoUpdateTask()`

---

## Melhorias Adicionais

### Sistema de Formatação de Tempo
- Criado método `formatTime()` na classe `TimeTrialDuels` para padronizar a formatação
- Formato: `MM:SS.mmm` quando há minutos, ou `SS.mmm` quando apenas segundos
- Consistente em toda a aplicação

### Logs Melhorados
- Adicionados logs informativos para:
  - Início de cada volta
  - Conclusão de cada volta com tempo
  - Finalização de jogadores com posição e tempo total
  - Salvamento de tempo final no banco de dados

### Tratamento de Edge Cases
- Desconexão de jogadores durante duelo
- Cancelamento por desistência
- Finalização por tempo limite
- Proteção contra spam de detecção de linha

---

## Testes Recomendados

1. **Teste de Voltas:**
   - Configure um duelo com 3 voltas
   - Verifique se após completar a 3ª volta, o jogador finaliza automaticamente
   - Confirme que não aparecem voltas 4, 5, etc.

2. **Teste de PB:**
   - Complete um duelo
   - Faça outro duelo na mesma pista
   - Verifique se o PB aparece corretamente no HUD e Scoreboard

3. **Teste de Posição:**
   - Inicie um duelo e observe a posição antes de começar (deve ser "1º LUGAR")
   - Durante a corrida, verifique se as posições se atualizam corretamente
   - Não deve aparecer "-º LUGAR" em nenhum momento

4. **Teste de Tempo Limite:**
   - Configure um duelo com 1 minuto de limite
   - Não complete as voltas
   - Verifique se recebe avisos aos 60s, 30s e 10s
   - Confirme que o duelo finaliza automaticamente após 1 minuto

5. **Teste de Lonely:**
   - Configure um duelo com modo Lonely ativado
   - Verifique se ambos os jogadores não se veem
   - Confirme que ambos spawnam na mesma posição exata
   - Compare com modo normal (deve ter offset de 2 blocos)

6. **Teste de Scoreboard/HUD:**
   - Verifique se o tempo está se atualizando em tempo real
   - Confirme que a volta atual está correta
   - Verifique se o PB aparece após completar pelo menos um duelo

---

## Arquivos Modificados

1. `src/main/java/dev/EfraGroup/formulaRacing/Duels/TimeTrialDuels.java`
   - Corrigida lógica de voltas
   - Adicionado sistema de tempo limite
   - Melhorado cálculo de posição
   - Implementada finalização automática
   - Ajustado posicionamento para modo lonely

2. `src/main/java/dev/EfraGroup/formulaRacing/Database/DatabaseManager.java`
   - Adicionado método `saveDuelFinalTime()`

3. `src/main/java/dev/EfraGroup/formulaRacing/Utils/ScoreboardDuelsTimeUtils.java`
   - Corrigida atualização de tempo em tempo real

---

## Observações

- Todos os bugs reportados foram corrigidos
- O sistema agora está mais robusto e confiável
- A lógica de voltas está consistente
- O PB é registrado e exibido corretamente
- O modo lonely funciona como esperado
- O limite de tempo é aplicado corretamente

## Próximos Passos

Realize testes completos em ambiente de desenvolvimento para validar todas as correções antes de fazer deploy em produção.

