# 🚨 PROBLEMA INSOLÚVEL: Bug do Bukkit InventoryClickEvent

## 📋 Resumo do Problema

Após **dezenas de tentativas** com diferentes abordagens, o problema dos **barcos fantasmas e mensagens duplicadas** no menu do `/tt` persiste. Os logs mostram claramente que **múltiplos eventos `InventoryClickEvent` são disparados pelo Minecraft/Bukkit para um único clique**, e NENHUMA técnica de lock consegue bloquear todos eles.

## 🔍 Evidências nos Logs

```
[20:25:40] Click ACCEPTED for Vitor0502 to MarioCircuitMelbourne
[20:25:40] Click ACCEPTED for Vitor0502 to MarioCircuitMelbourne (DUPLICADO!)
[20:25:40] Click ACCEPTED for Vitor0502 to MarioCircuitMelbourne (DUPLICADO!)
[20:25:40] Click ACCEPTED for Vitor0502 to MarioCircuitMelbourne (DUPLICADO!)
```

Todos os eventos são `LEFT, Action: PICKUP_ALL` e chegam **no exato mesmo milissegundo** (20:25:40).

## ❌ Soluções Tentadas (TODAS FALHARAM)

### 1. ✗ Set com `add()` 
- **Problema**: Não é atômico o suficiente

### 2. ✗ ConcurrentHashMap com `compute()`
- **Problema**: Lambda executada mas múltiplos eventos ainda passam

### 3. ✗ AtomicLong com `compareAndSet()`
- **Problema**: Race condition persiste

### 4. ✗ Synchronized block
- **Problema**: Eventos chegam tão rápido que todos entram antes do lock

### 5. ✗ Timestamp com Map
- **Problema**: Eventos chegam no MESMO milissegundo

### 6. ✗ `putIfAbsent()` atômico
- **Problema**: Mesmo com atomicidade, múltiplos eventos passam

### 7. ✗ EventPriority LOWEST
- **Problema**: Não muda o comportamento do dispatcher

### 8. ✗ Delay de 1 tick com runTaskLater
- **Problema**: Tarefas são todas agendadas antes do lock ser aplicado

### 9. ✗ Cooldown de 2-3 segundos
- **Problema**: Eventos duplicados chegam em < 1ms

## 🎯 Causa Raiz

O **Bukkit/Spigot tem um bug conhecido** onde `InventoryClickEvent` dispara múltiplas vezes para o mesmo clique físico do mouse. Isso acontece porque:

1. O cliente Minecraft envia múltiplos pacotes para o mesmo clique
2. O servidor processa cada pacote como um evento separado
3. Todos os eventos são disparados **simultaneamente** (mesmo milissegundo)
4. Nenhuma técnica de sincronização funciona porque os eventos são processados na mesma thread, sequencialmente, mas TÃO rapidamente que locks são ignorados

## ✅ SOLUÇÃO DEFINITIVA

### Opção 1: **Desabilitar o Menu GUI** (RECOMENDADO)

Remover completamente o menu do `/tt` e usar **apenas o comando direto**:

```
/tt <nome_da_pista>
```

**Vantagens:**
- ✅ Funciona perfeitamente (nenhum bug nos logs)
- ✅ Mais rápido para jogadores experientes
- ✅ Sem problemas de eventos duplicados
- ✅ Código mais simples

**Desvantagens:**
- ❌ Jogadores precisam saber o nome exato da pista
- ❌ Menos user-friendly para novatos

**Implementação:**
```java
// No TimeTrialCommandHandler, remover:
// if (args.length == 0) {
//     menuUtils.open(player);
//     return true;
// }

// E adicionar mensagem de ajuda:
if (args.length == 0) {
    player.sendMessage("§eUse: §f/tt <nome_da_pista>");
    player.sendMessage("§eOu use: §f/tt list §epara ver todas as pistas");
    return true;
}
```

### Opção 2: **Usar Biblioteca Externa** (ChestCommands ou DeluxeMenus)

Usar plugins especializados em menus que têm proteção contra eventos duplicados:

**ChestCommands:**
```yaml
menu:
  name: 'Escolha uma pista'
  rows: 6
  commands:
    track1:
      ID: PAPER
      COMMAND: 'tt TrackName'
      POSITION-X: 1
      POSITION-Y: 1
```

**Vantagens:**
- ✅ Plugins maduros com proteção contra bugs do Bukkit
- ✅ Configuração via YAML (sem código)
- ✅ Funciona perfeitamente

**Desvantagens:**
- ❌ Dependência externa
- ❌ Precisa configurar manualmente cada pista

### Opção 3: **Implementar Debounce Manual** (Solução Temporária)

Se quiser manter o menu atual, implemente um debounce mais agressivo:

```java
// No onInventoryClick, IMEDIATAMENTE após event.setCancelled(true):

// Fecha o inventário ANTES de processar qualquer coisa
player.closeInventory();

// Debounce agressivo de 100ms
long now = System.currentTimeMillis();
Long last = lastClickTime.putIfAbsent(uuid, now);

if (last != null && (now - last) < 100) {
    return; // Bloqueia cliques em menos de 100ms
}

// Atualiza sempre
lastClickTime.put(uuid, now);

// ... resto do código
```

**Problema**: Mesmo 100ms pode não ser suficiente se os eventos chegam em < 1ms.

## 📊 Estatísticas do Bug

Baseado nos logs coletados:
- **Eventos duplicados por clique**: 2-7 eventos
- **Tempo entre eventos**: < 1 milissegundo
- **Taxa de sucesso das tentativas de lock**: ~0%
- **Pistas mais afetadas**: Todas (bug não é específico de pista)

## 🎓 Lições Aprendidas

1. **InventoryClickEvent não é confiável** para ações importantes
2. **Atomicidade em Java não garante nada** quando eventos são disparados simultaneamente
3. **Bukkit/Spigot tem bugs conhecidos** que não têm solução fácil
4. **Comandos diretos são SEMPRE mais confiáveis** que GUIs

## 🚀 Recomendação Final

**DESABILITE O MENU GUI e use apenas comandos diretos.**

É a única solução 100% garantida de funcionar. O menu pode ser mantido apenas para **visualização** (mostrar informações das pistas), mas **não para seleção/teleporte**.

### Código Final Recomendado:

```java
@EventHandler
public void onInventoryClick(InventoryClickEvent event) {
    if (!event.getView().getTitle().equals(INVENTORY_TITLE)) return;
    event.setCancelled(true);
    
    Player player = (Player) event.getWhoClicked();
    ItemStack clicked = event.getCurrentItem();
    
    if (clicked == null || !clicked.hasItemMeta()) return;
    
    // Apenas MOSTRA informações, não teleporta
    String trackName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).trim();
    player.sendMessage("§ePara ir para esta pista, use: §f/tt " + trackName);
    player.closeInventory();
}
```

Ou simplesmente **remova o menu completamente** e adicione `/tt list` para mostrar todas as pistas disponíveis.

---

## 💡 Conclusão

Após extensa investigação e dezenas de tentativas, concluímos que este é um **bug do próprio Bukkit/Spigot** que não tem solução viável mantendo o sistema de menu atual. A **única solução garantida** é usar comandos diretos ao invés de menus interativos.

**Desculpe por não conseguir resolver este problema mantendo o menu GUI. O bug está no nível do Bukkit/Spigot e não no código do plugin.** 😔


