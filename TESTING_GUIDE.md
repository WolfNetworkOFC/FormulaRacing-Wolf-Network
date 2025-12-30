# 🧪 Guia de Testes - Sistema de Duelos

## 🚀 Como Testar o Sistema Melhorado

### Pré-requisitos
- [ ] Plugin compilado e instalado
- [ ] Pelo menos 2 jogadores online
- [ ] Uma pista configurada com regiões START e FINISH
- [ ] Checkpoints configurados (opcional)

---

## 📝 Cenário 1: Duelo Básico (Caminho Feliz)

### Passos
1. **Jogador A** digita `/duel JogadorB`
2. GUI abre com configurações:
   - Selecionar pista (clique no mapa)
   - Voltas: 3 (use os botões + / -)
   - Tempo limite: 60s
   - Modo Lonely: DESATIVADO
3. Clique em **ENVIAR CONVITE** (lã verde)
4. **Jogador B** recebe mensagem com botão **[ACEITAR CONVITE]**
5. **Jogador B** clica ou digita `/duel accept JogadorA`

### Resultado Esperado
```
✅ Ambos são teleportados para o grid
✅ Contagem regressiva aparece (5s no chat, 5s no title)
✅ "GO!" aparece e barcos são liberados
✅ Scoreboard mostra "Volta: 0/3"
✅ Action bar mostra "-º PLACE | 00:00.000"
```

---

## 📝 Cenário 2: Corrida com Voltas

### Durante a Corrida
1. Jogador cruza linha **START**
   - Title aparece: "VOLTA 1"
   - Timer inicia na action bar
   - Scoreboard atualiza: "Volta: 1/3"

2. Jogador cruza linha **START** novamente
   - Title aparece: "VOLTA 2"
   - Scoreboard atualiza: "Volta: 2/3"

3. Jogador cruza linha **START** pela 3ª vez
   - Title aparece: "VOLTA 3"
   - Scoreboard atualiza: "Volta: 3/3"

4. Jogador cruza linha **FINISH**
   - Title aparece: "FINALIZOU! | 1º Lugar"
   - Som de conquista toca
   - Timer para

### Verificações no Console
```
[INFO] [DUEL] Duelo #123 criado: JogadorA vs JogadorB
[INFO] [DUEL] Duelo #123 iniciado!
[INFO] [DUEL] JogadorA cruzou START no duelo #123
[INFO] [DUEL] JogadorA completou volta 1 e iniciou volta 2
[INFO] [DUEL] JogadorA completou volta 2 e iniciou volta 3
[INFO] [DUEL] JogadorA cruzou FINISH no duelo #123
[INFO] [DUEL] JogadorA finalizou em 1º lugar no duelo #123
[INFO] [DUEL] Finalizando duelo #123
```

---

## 📝 Cenário 3: Desistência Manual

### Passos
1. Durante a corrida, **Jogador A** digita `/duel sair`

### Resultado Esperado
```
✅ Jogador A: "§7Você saiu do duelo."
✅ Jogador A: Timer e scoreboard são removidos
✅ Jogador A: Lonely mode desativado (se estava ativo)

✅ Jogador B: "§a§lVITÓRIA! Você venceu porque o oponente desistiu."
✅ Jogador B: Timer e scoreboard são removidos
✅ Duelo marcado como FINISHED no banco
```

### Verificação no Console
```
[INFO] [DUEL] Duelo #123 finalizado
```

---

## 📝 Cenário 4: Modo Lonely Ativado

### Configuração
1. Ao criar o duelo, clique no **Ender Pearl** para ativar Lonely
2. Ícone muda para **Ender Eye** (verde)
3. Status mostra "ATIVADO"

### Durante a Corrida
```
✅ Jogadores NÃO veem um ao outro
✅ Barcos NÃO colidem entre si
✅ Cada um corre sozinho
```

### Ao Finalizar
```
✅ Lonely mode é DESATIVADO automaticamente
✅ Jogadores voltam a se ver
```

---

## 📝 Cenário 5: Convite Expirado

### Passos
1. **Jogador A** envia convite
2. **Esperar 60 segundos** sem aceitar

### Resultado Esperado
```
✅ Jogador A: "§c§lDUELO » Seu convite para JogadorB expirou."
✅ Jogador B: "§c§lDUELO » O convite de JogadorA expirou."
✅ Convite removido da memória
```

---

## 📝 Cenário 6: Desconexão Durante Duelo

### Passos
1. Iniciar duelo normalmente
2. Um jogador desconecta (sai do servidor)

### Resultado Esperado
```
✅ Evento PlayerQuitEvent é capturado
✅ handleLeave() é chamado automaticamente
✅ Outro jogador vence por W.O.
✅ Recursos são limpos
```

---

## 📝 Cenário 7: Múltiplos Duelos Simultâneos

### Passos
1. **JogadorA** vs **JogadorB** → Pista 1
2. **JogadorC** vs **JogadorD** → Pista 2
3. Ambos os duelos acontecem ao mesmo tempo

### Verificações
```
✅ IDs de duelo são diferentes (ex: #123 e #124)
✅ Não há interferência entre duelos
✅ Scoreboards mostram dados corretos
✅ Cada duelo finaliza independentemente
```

### Console
```
[INFO] [DUEL] Duelo #123 criado: JogadorA vs JogadorB
[INFO] [DUEL] Duelo #124 criado: JogadorC vs JogadorD
[INFO] [DUEL] JogadorA cruzou START no duelo #123
[INFO] [DUEL] JogadorC cruzou START no duelo #124
```

---

## 🐛 Testes de Casos Extremos

### Teste 1: Spam de Cliques no GUI
- Clicar rapidamente nos itens
- **Esperado:** Cooldown de 500ms previne spam

### Teste 2: Aceitar Convite Inexistente
- Digitar `/duel accept JogadorInexistente`
- **Esperado:** Mensagem de erro amigável

### Teste 3: Pista Sem Spawn
- Criar duelo em pista sem spawn configurado
- **Esperado:** Erro e duelo não inicia

### Teste 4: Jogador Já em Duelo
- Jogador tenta aceitar outro convite enquanto já está em duelo
- **Esperado:** Mensagem "Você já está em um duelo!"

### Teste 5: Voltas = 1
- Configurar apenas 1 volta
- **Esperado:** Finaliza imediatamente ao cruzar FINISH

### Teste 6: Voltas = 10+
- Configurar 10 ou mais voltas
- **Esperado:** Funciona corretamente, contador continua

---

## 📊 Checklist de Validação

### Funcionalidade Básica
- [ ] Duelo é criado com ID válido
- [ ] Scoreboard aparece para ambos
- [ ] Action bar funciona
- [ ] Timer inicia corretamente
- [ ] Voltas são contadas
- [ ] Vencedor é determinado
- [ ] Recursos são limpos

### Proteções
- [ ] `/tp` é bloqueado durante duelo
- [ ] `/home` é bloqueado durante duelo
- [ ] Não pode desmontar do barco
- [ ] `/duel sair` funciona para sair

### Integrações
- [ ] Banco de dados salva duelo corretamente
- [ ] Estado STARTED → FINISHED
- [ ] Vencedor é registrado
- [ ] Lonely mode funciona
- [ ] BoatUtils são aplicados

### Performance
- [ ] Sem lag durante contagem
- [ ] Scoreboard atualiza suavemente
- [ ] Múltiplos duelos não causam lag

---

## 🔍 Debugging

### Comandos Úteis para Admin
```
/duel <jogador>         - Iniciar duelo
/duel accept <jogador>  - Aceitar duelo
/duel sair             - Sair do duelo
```

### Verificar Logs
Procure por estas mensagens:
- `[DEBUG]` - Informações de depuração
- `[DUEL]` - Estados do duelo
- `[ERRO]` - Problemas críticos

### SQL para Verificar Duelos
```sql
-- Ver duelos ativos
SELECT * FROM fr_timetrial_duels WHERE state = 'STARTED';

-- Ver duelos finalizados recentemente
SELECT * FROM fr_timetrial_duels WHERE state = 'FINISHED' ORDER BY finished_in DESC LIMIT 10;

-- Ver jogadores em um duelo específico
SELECT * FROM fr_timetrial_duel_players WHERE duel_id = 123;
```

---

## ✅ Checklist Final

Antes de considerar o sistema pronto:
- [ ] Todos os cenários básicos passam
- [ ] Casos extremos são tratados
- [ ] Não há memory leaks (recursos sempre limpos)
- [ ] Logs estão claros e informativos
- [ ] Performance está boa (< 1ms por tick)
- [ ] Banco de dados está consistente
- [ ] Jogadores reportam experiência positiva

---

**Dica:** Execute estes testes em um servidor de desenvolvimento primeiro, depois em produção após validação completa!

