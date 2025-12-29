# 🎯 Guia Rápido - Sistema de Multilinguagem

## Para Jogadores

### Comandos Disponíveis

```
/frlang list              - Ver todos os idiomas disponíveis
/frlang set pt_BR         - Definir português do Brasil
/frlang set en_US         - Definir inglês
/frlang set pt_PT         - Definir português de Portugal
/frlang                   - Ver ajuda dos comandos
```

### Exemplo de Uso
```
/frlang list
> Idiomas Disponíveis:
> • en_US - /frlang set en_US
> • pt_BR (Atual) ✅
> • pt_PT - /frlang set pt_PT

/frlang set en_US
> Your language was updated to: en_US
```

---

## Para Desenvolvedores

### Adicionar Nova Tradução

#### 1. Adicione a chave nos 3 arquivos de idioma:

**en_US.yml:**
```yaml
my_new_message: "&aThis is my new message for &e{player}"
```

**pt_BR.yml:**
```yaml
my_new_message: "&aEsta é minha nova mensagem para &e{player}"
```

**pt_PT.yml:**
```yaml
my_new_message: "&aEsta é a minha nova mensagem para &e{player}"
```

#### 2. Use no código:

**Opção 1 - Método Helper (Recomendado):**
```java
plugin.sendMessage(player, "my_new_message", "{player}", player.getName());
```

**Opção 2 - Manual:**
```java
String langCode = database.getPlayerLanguage(player.getUniqueId());
String message = plugin.getTranslation("my_new_message", langCode, 
    "{player}", player.getName());
player.sendMessage(message);
```

**Opção 3 - Sem placeholders:**
```java
String langCode = database.getPlayerLanguage(player.getUniqueId());
player.sendMessage(plugin.getDirectTranslation("my_new_message", langCode));
```

---

### Adicionar Novo Idioma

#### 1. Crie o arquivo de idioma:
```
src/main/resources/lang/es_ES.yml
```

#### 2. Copie o conteúdo de en_US.yml e traduza:
```yaml
# Mensagens Gerais
lang_set: "&a[FormulaRacing] Tu idioma fue actualizado a: "
no_permission: "&cNo tienes permiso para usar este comando."
# ... etc
```

#### 3. Atualize FileManager.java:
```java
private void copyLangFiles() {
    String[] langFiles = {"en_US.yml", "pt_BR.yml", "pt_PT.yml", "es_ES.yml"};
    // ...
}
```

---

### Placeholders Disponíveis

| Placeholder | Uso |
|-------------|-----|
| `{player}` | Nome do jogador |
| `{track}` | Nome da pista |
| `{time}` | Tempo |
| `{page}` | Número de página |
| `{owner}` | Dono |
| `{world}` | Mundo |
| `{lang}` | Código do idioma |

**Como usar múltiplos placeholders:**
```java
plugin.sendMessage(player, "track_info",
    "{track}", trackName,
    "{owner}", ownerName,
    "{world}", worldName);
```

---

### Cores no YAML

Use `&` para cores, elas serão convertidas automaticamente:

```yaml
my_message: "&a[Success] &fYour action was completed!"
```

**Cores disponíveis:**
- `&a` = Verde
- `&c` = Vermelho
- `&e` = Amarelo
- `&f` = Branco
- `&7` = Cinza
- `&8` = Cinza escuro
- `&b` = Azul claro
- `&3` = Azul escuro
- `&6` = Dourado
- `&l` = Negrito
- `&m` = Riscado

---

## Checklist para Adicionar Tradução

- [ ] Adicionar chave em `en_US.yml`
- [ ] Adicionar chave em `pt_BR.yml`
- [ ] Adicionar chave em `pt_PT.yml`
- [ ] Usar `plugin.sendMessage()` ou `plugin.getTranslation()` no código
- [ ] Testar com diferentes idiomas
- [ ] Verificar se placeholders foram substituídos corretamente

---

## Comandos Já Traduzidos ✅

- `/frlang` - Gerenciamento de idiomas
- `/track` - Comandos de pistas
- `/formularacingreload` - Recarregar plugin
- Sistema de menus de Time Trial
- Sistema de câmera
- Proteção de duelos
- Mensagens de listeners

---

## Testando o Sistema

### 1. No servidor:
```
/frlang set pt_BR
/track times MinhaTrack
> Deverá ver mensagens em português

/frlang set en_US  
/track times MinhaTrack
> Deverá ver mensagens em inglês
```

### 2. Verificar banco de dados:
```sql
SELECT uuid, name, lang FROM fr_players;
```

### 3. Verificar arquivos copiados:
```
plugins/FormulaRacing/lang/en_US.yml
plugins/FormulaRacing/lang/pt_BR.yml
plugins/FormulaRacing/lang/pt_PT.yml
```

---

## Troubleshooting

### Mensagem aparece como erro
**Problema:** `§c[Lang Error] Key 'xxx' not found in pt_BR.yml`  
**Solução:** Adicione a chave no arquivo de idioma correspondente

### Placeholder não é substituído
**Problema:** Mensagem mostra `{player}` ao invés do nome  
**Solução:** Use `getTranslation()` ao invés de `getDirectTranslation()`

### Idioma não muda
**Problema:** Jogador usa `/frlang set` mas mensagens continuam no idioma antigo  
**Solução:** Verifique se o código está usando `getPlayerLanguage()` antes de buscar tradução

### Arquivo não encontrado
**Problema:** `File not found: pt_BR`  
**Solução:** Certifique-se que o arquivo foi copiado para `plugins/FormulaRacing/lang/`

---

## Boas Práticas

✅ **Sempre use placeholders** para valores dinâmicos  
✅ **Mantenha consistência** entre os idiomas  
✅ **Use cores apropriadas** (verde para sucesso, vermelho para erro)  
✅ **Teste em todos os idiomas** antes de fazer commit  
✅ **Documente novas chaves** em comentários no YAML  
✅ **Use nomes descritivos** para chaves (ex: `track_not_found`)  
❌ **Nunca hardcode** mensagens no código Java  

---

## Exemplo Completo

```java
// No CommandHandler
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
        sender.sendMessage("§cThis command cannot be executed from the console.");
        return true;
    }
    
    // Busca o idioma do jogador
    String langCode = database.getPlayerLanguage(player.getUniqueId());
    
    if (args.length == 0) {
        // Mensagem simples
        player.sendMessage(plugin.getDirectTranslation("command_usage", langCode));
        return true;
    }
    
    String trackName = args[0];
    
    if (!database.trackExists(trackName)) {
        // Mensagem com placeholder
        plugin.sendMessage(player, "track_not_found", "{track}", trackName);
        return true;
    }
    
    // Sucesso
    plugin.sendMessage(player, "track_teleported", "{track}", trackName);
    return true;
}
```

**Arquivos YAML correspondentes:**
```yaml
command_usage: "&eUse: &a/mycommand <track>"
track_not_found: "&cTrack &e{track} &cnot found!"
track_teleported: "&aTeleported to &e{track}&a!"
```

---

Pronto! O sistema está completo e funcional! 🎉

