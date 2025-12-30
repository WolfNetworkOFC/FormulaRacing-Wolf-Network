# 🏁 Melhorias no Sistema de Duelos

## 📋 Resumo das Mudanças

Este documento descreve as melhorias implementadas no sistema de duelos do FormulaRacing para corrigir erros e inconsistências.

---

## 🔧 Problemas Corrigidos

### 1. **Rastreamento de Estado Inadequado**
**Antes:** O sistema não mantinha um estado consistente dos duelos ativos e dos jogadores.

**Depois:** 
- Adicionado `Map<Integer, DuelState>` para rastrear todos os duelos ativos
- Adicionado `Map<UUID, PlayerDuelState>` para rastrear o estado individual de cada jogador
- Cada duelo agora tem informações sobre:
  - Jogadores participantes
  - Número de voltas totais
  - Estado da corrida (iniciada ou não)
  - Ordem de chegada dos jogadores

### 2. **ID do Duelo Não Era Capturado**
**Antes:** O duelo era criado de forma assíncrona e o ID nunca era recuperado, causando problemas de sincronização.

**Depois:**
- Mudado para criação síncrona do duelo
- ID é recuperado imediatamente após a criação
- Todos os sistemas (scoreboard, action bar, timer) recebem o ID correto

### 3. **Sistema de Voltas Não Funcionava**
**Antes:** Não havia contagem de voltas nos duelos - apenas detecção de START/END genérica.

**Depois:**
- Implementado `onPlayerCrossStart()` para incrementar voltas quando o jogador cruza a linha de largada
- Implementado `onPlayerCrossFinish()` para verificar se completou todas as voltas
- Scoreboard agora atualiza automaticamente o número da volta atual
- Feedback visual com títulos mostrando "VOLTA X"

### 4. **Detecção de Finalização Incorreta**
**Antes:** O sistema não detectava corretamente quando um jogador completava o duelo.

**Depois:**
- Jogador só finaliza quando cruza o FINISH após completar todas as voltas
- Sistema registra a ordem de chegada (1º, 2º lugar)
- Vencedor é determinado automaticamente (primeiro a finalizar)
- Duelo termina automaticamente quando todos finalizam

### 5. **Limpeza de Recursos Incompleta**
**Antes:** Quando um jogador saía do duelo, muitos recursos não eram liberados corretamente.

**Depois:**
- Método `cleanupPlayer()` centralizado para limpar:
  - Timer visual (action bar)
  - Scoreboard do duelo
  - Modo Lonely (colisões/invisibilidade)
  - Estado do jogador nos mapas
- Método `removePlayerFromDuel()` para desistência manual
- Se um jogador sai, o outro vence automaticamente

### 6. **Timer Não Iniciava Corretamente**
**Antes:** Timer era ativado durante a contagem regressiva, causando tempos incorretos.

**Depois:**
- Timer só é ativado após a primeira passagem pela linha START
- Jogadores podem ficar no grid sem o timer rodar
- Suporta múltiplas voltas com tempos precisos

### 7. **Integração com RegionListener Incompleta**
**Antes:** O RegionListener não chamava os métodos corretos para duelos.

**Depois:**
- RegionListener agora detecta quando um jogador está em duelo
- Chama `timeTrialDuels.onPlayerCrossStart()` ao cruzar START
- Chama `timeTrialDuels.onPlayerCrossFinish()` ao cruzar FINISH
- Lógica separada entre duelos e corridas solo

---

## 🆕 Novos Recursos

### Classes Internas para Gerenciamento de Estado

#### `DuelState`
Rastreia o estado completo de um duelo:
```java
- duelId: ID único do duelo
- trackName: Nome da pista
- totalLaps: Número total de voltas
- timeLimit: Limite de tempo
- lonely: Modo fantasma ativado
- players: Set de UUIDs dos participantes
- finishOrder: Lista ordenada de quem finalizou
- raceStarted: Flag indicando se a corrida já começou
```

#### `PlayerDuelState`
Rastreia o estado individual de cada jogador:
```java
- playerUUID: UUID do jogador
- duelId: ID do duelo em que está
- currentLap: Volta atual (0 = não iniciou)
- finished: Flag indicando se já finalizou
```

### Novos Métodos Públicos

```java
// Chamado quando jogador cruza linha START
public void onPlayerCrossStart(Player player, int duelId)

// Chamado quando jogador cruza linha FINISH
public void onPlayerCrossFinish(Player player, int duelId)

// Remove jogador do duelo (desistência)
public void removePlayerFromDuel(UUID playerUUID, int duelId)

// Verifica se jogador está em duelo
public boolean isPlayerInDuel(UUID playerUUID)

// Obtém ID do duelo do jogador
public int getPlayerDuelId(UUID playerUUID)
```

---

## 📊 Fluxo Melhorado do Duelo

### 1. **Criação do Duelo**
```
Jogador 1 desafia Jogador 2
    ↓
Configurações (pista, voltas, lonely)
    ↓
Convite enviado (expira em 60s)
    ↓
Jogador 2 aceita
    ↓
Duelo criado no banco (SÍNCRONO)
    ↓
ID recuperado imediatamente
    ↓
Estados criados (DuelState + PlayerDuelState)
```

### 2. **Preparação**
```
Aplicar BoatUtils aos jogadores
    ↓
Ativar visuais (Action Bar + Scoreboard)
    ↓
Aplicar modo Lonely (se ativo)
    ↓
Teleportar para grid (com ArmorStand)
    ↓
Iniciar contagem regressiva (10s)
```

### 3. **Corrida**
```
GO! → Barcos liberados
    ↓
Jogador cruza START → Volta 1 inicia, Timer ativado
    ↓
Jogador cruza START novamente → Volta 2
    ↓
... (continua por N voltas)
    ↓
Jogador cruza FINISH na última volta → Finalizado!
    ↓
Verifica se todos finalizaram → Encerra duelo
```

### 4. **Finalização**
```
Determinar vencedor (1º a chegar)
    ↓
Salvar no banco com vencedor
    ↓
Limpar recursos de todos os jogadores:
  - Parar timers
  - Remover scoreboards
  - Desativar Lonely
  - Remover do estado
    ↓
Notificar jogadores do resultado
```

---

## 🐛 Bugs Conhecidos Corrigidos

1. ✅ **Timer iniciava durante contagem regressiva**
2. ✅ **ID do duelo era sempre 0 ou inválido**
3. ✅ **Voltas não eram contadas**
4. ✅ **Jogadores não conseguiam finalizar o duelo**
5. ✅ **Lonely mode não era desativado ao sair**
6. ✅ **Scoreboard não atualizava a volta**
7. ✅ **Duelo não encerrava quando um jogador saía**
8. ✅ **Múltiplos duelos causavam conflitos de estado**

---

## 🎯 Próximos Passos Sugeridos

### Melhorias Futuras
1. **Sistema de Pontuação**
   - Implementar ELO ou ranking baseado em vitórias/derrotas
   - Histórico de duelos por jogador

2. **Suporte a Mais Jogadores**
   - Expandir de 1v1 para 1v1v1v1 (4 jogadores)
   - Grid de largada com mais posições

3. **Modos de Duelo Adicionais**
   - Time Trial puro (melhor tempo vence)
   - Eliminação (último lugar é eliminado a cada volta)
   - Relay (corrida em equipe)

4. **Estatísticas Avançadas**
   - Tempo médio por volta
   - Melhor volta individual
   - Gráficos de performance

5. **Timeout Automático**
   - Implementar o `timeLimit` configurado
   - Encerrar duelo automaticamente após X segundos
   - Vence quem estiver na frente quando acabar o tempo

---

## 📝 Notas de Desenvolvimento

### Dependências Atualizadas
- `TimeTrialDuels.java` - Sistema principal de duelos
- `RegionListener.java` - Detecção de START/FINISH
- `DuelCommandHandler.java` - Comandos e GUI
- `FormulaRacing.java` - Inicialização
- `ScoreboardDuelsTimeUtils.java` - Scoreboard (já tinha suporte)
- `TimeTrialDuelsAction.java` - Action bar (já tinha suporte)

### Arquivos Não Modificados (mas integrados)
- `DatabaseManager.java` - Métodos existentes são utilizados
- `PacketSender.java` - BoatUtils e Lonely já funcionavam
- `DuelProtectionListener.java` - Proteção contra comandos

---

## ✅ Testes Recomendados

1. **Teste Básico**
   - [ ] Criar duelo 1v1
   - [ ] Aceitar convite
   - [ ] Completar 3 voltas
   - [ ] Verificar vencedor

2. **Teste de Voltas**
   - [ ] Verificar contagem de voltas no scoreboard
   - [ ] Confirmar que finaliza após N voltas
   - [ ] Testar com 1, 3, 5 e 10 voltas

3. **Teste de Desistência**
   - [ ] Jogador 1 sai com `/duel sair`
   - [ ] Verificar que Jogador 2 vence automaticamente
   - [ ] Confirmar limpeza de recursos

4. **Teste de Desconexão**
   - [ ] Jogador desconecta durante duelo
   - [ ] Verificar que duelo é encerrado corretamente

5. **Teste de Lonely Mode**
   - [ ] Ativar modo Lonely
   - [ ] Confirmar invisibilidade entre jogadores
   - [ ] Verificar que desativa ao finalizar

6. **Teste de Múltiplos Duelos**
   - [ ] Criar 2+ duelos simultâneos
   - [ ] Verificar que não há conflito de estado
   - [ ] Confirmar IDs únicos

---

## 📞 Suporte

Se encontrar problemas após essas mudanças:

1. Verifique os logs do console para mensagens `[DUEL]`
2. Confirme que o banco de dados tem as tabelas corretas
3. Teste em um ambiente isolado primeiro
4. Considere adicionar mais logging se necessário

---

**Última Atualização:** 2025-12-30
**Versão:** 1.0.0 - Sistema de Duelos Refatorado

