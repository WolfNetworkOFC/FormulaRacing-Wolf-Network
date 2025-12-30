# Correções do Sistema de Duelos - Parte 4

## Data: 2025-12-30

## Bugs Corrigidos

### 1. ✅ Contagem regressiva duplicada
**Problema**: A contagem regressiva ocorria duas vezes - uma no chat e outra na tela com títulos grandes.

**Solução**: 
- Removida a **Fase 1** da contagem regressiva (mensagens no chat)
- Mantida apenas a **contagem com títulos grandes** na tela (mais visual e clara)
- Agora só há uma contagem de 5 segundos com números grandes na tela
- Som de "harp" a cada segundo, e som de "orb pickup" ao dar o GO!

**Arquivo modificado**: `TimeTrialDuels.java` - método `startFullCountdownSequence()`

**Antes**:
```
FASE 1: 5 segundos no chat ("Starting in 5s...", "Starting in 4s...", etc.)
FASE 2: 5 segundos com títulos grandes (5, 4, 3, 2, 1, GO!)
Total: 10 segundos de espera
```

**Depois**:
```
Contagem única: 5 segundos com títulos grandes (5, 4, 3, 2, 1, GO!)
Total: 5 segundos de espera
```

---

### 2. ✅ Tempo restante do duelo não era exibido
**Problema**: Não havia forma de saber quanto tempo faltava para o duelo terminar quando havia um limite de tempo definido.

**Solução**: 
- Adicionado novo método `getTimeRemaining(int duelId)` no `TimeTrialDuels`
  - Calcula o tempo restante em tempo real
  - Retorna -1 se não há limite de tempo
  - Retorna o tempo total se a corrida ainda não começou
  - Retorna 0 se o tempo já acabou
- Atualizada a `ScoreboardDuelsTimeUtils` para mostrar o tempo restante
- O tempo aparece em formato legível: `MM:SS` ou `XXs`
- Só aparece quando há limite de tempo definido

**Arquivos modificados**: 
- `TimeTrialDuels.java` - novo método `getTimeRemaining()`
- `ScoreboardDuelsTimeUtils.java` - atualizado `update()` e adicionado `formatTimeRemaining()`

---

## Como Funciona

### Display do Tempo Restante na Scoreboard

**Quando há limite de tempo**:
```
§8------------------
 §fPosição: §a§l1º LUGAR
 §fVolta: §b2§7/§b3
 
 §fTempo: §e45.234
 §fRecorde: §f44.567
 §fTempo Restante: §c5:30  <-- NOVO!
 
 §fPista: §aInterlagos
§8------------------
§ewolfnetwork.com.br
```

**Quando NÃO há limite de tempo**:
```
§8------------------
 §fPosição: §a§l1º LUGAR
 §fVolta: §b2§7/§b3
 
 §fTempo: §e45.234
 §fRecorde: §f44.567
 
 §fPista: §aInterlagos
§8------------------
§ewolfnetwork.com.br
```

### Formato do Tempo Restante

- **5:30** - 5 minutos e 30 segundos
- **1:05** - 1 minuto e 5 segundos
- **45s** - 45 segundos (quando menos de 1 minuto)
- **10s** - 10 segundos

A cor é **vermelha (§c)** para chamar atenção que o tempo está acabando.

---

## Sequência de Contagem Regressiva

### Antes (10 segundos total):
1. **Fase 1 - Chat** (5 segundos):
   - "Starting in 5s..."
   - "Starting in 4s..."
   - "Starting in 3s..."
   - "Starting in 2s..."
   - "Starting in 1s..."

2. **Fase 2 - Tela** (5 segundos):
   - Título: "5"
   - Título: "4"
   - Título: "3"
   - Título: "2"
   - Título: "1"
   - Título: "GO!"

### Agora (5 segundos total):
1. **Contagem única - Tela** (5 segundos):
   - Título: "5"
   - Título: "4"
   - Título: "3"
   - Título: "2"
   - Título: "1"
   - Título: "GO!"

Muito mais rápido e direto! ⚡

---

## Melhorias Implementadas

### Contagem Regressiva
- ✅ Reduzida de 10 para 5 segundos
- ✅ Apenas contagem visual (sem spam no chat)
- ✅ Mais profissional e limpa
- ✅ Sons mantidos para feedback auditivo

### Display de Tempo
- ✅ Tempo restante visível em tempo real
- ✅ Formato legível e intuitivo
- ✅ Só aparece quando relevante (quando há limite)
- ✅ Cor vermelha para urgência
- ✅ Atualização a cada 2 ticks (suave)

---

## Testes Recomendados

### 1. Teste de Contagem Regressiva
- Iniciar um duelo
- Observar se há apenas UMA contagem de 5 segundos na tela
- Verificar se NÃO há mensagens no chat
- Total: deve levar exatamente 5 segundos até o GO!

### 2. Teste de Tempo Restante (COM limite)
- Criar duelo com limite de 5 minutos
- Verificar se aparece "Tempo Restante: 5:00" na scoreboard
- Observar a contagem regressiva em tempo real
- Verificar avisos em 60s, 30s e 10s restantes

### 3. Teste de Tempo Restante (SEM limite)
- Criar duelo SEM limite de tempo
- Verificar se NÃO aparece linha de "Tempo Restante"
- A scoreboard deve ficar mais limpa

### 4. Teste de Fim por Tempo
- Criar duelo com limite de 1 minuto
- Deixar o tempo acabar
- Verificar se o duelo encerra automaticamente
- Verificar se o vencedor é determinado por quem está mais avançado

---

## Arquivos Modificados

### 1. `TimeTrialDuels.java`
**Mudanças**:
- Método `startFullCountdownSequence()` - removida fase 1 (chat), mantida apenas fase 2 (títulos)
- Novo método `getTimeRemaining(int duelId)` - calcula tempo restante em tempo real

**Linhas afetadas**: ~130-185 (contagem), ~680-705 (novo método)

### 2. `ScoreboardDuelsTimeUtils.java`
**Mudanças**:
- Método `update()` - adicionada lógica para exibir tempo restante
- Novo método `formatTimeRemaining(int seconds)` - formata tempo restante de forma legível
- Layout da scoreboard ajustado para incluir linha de tempo restante quando aplicável

**Linhas afetadas**: ~85-150 (update), ~200-210 (formatTimeRemaining)

---

## Notas Importantes

- A contagem regressiva agora é 50% mais rápida (5s em vez de 10s)
- O tempo restante é calculado em tempo real, não é uma estimativa
- Se o duelo não tiver limite de tempo, a linha simplesmente não aparece
- O formato do tempo muda automaticamente baseado na quantidade (MM:SS ou XXs)
- Tudo continua compatível com o sistema de multilinguagem

---

## Resumo das Melhorias

| Antes | Depois |
|-------|--------|
| Contagem regressiva: 10s (chat + tela) | Contagem regressiva: 5s (só tela) |
| Sem display de tempo restante | Tempo restante visível em tempo real |
| Chat poluído com mensagens | Chat limpo |
| Difícil saber quanto tempo falta | Fácil monitorar o tempo |

🎮 **Experiência muito mais limpa e profissional!**

