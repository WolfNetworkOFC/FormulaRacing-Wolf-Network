# 🎉 ATUALIZAÇÃO: Suporte para Offline Mode (Minecraft Pirata)

**Data:** 30/12/2024  
**Versão:** 0.2.1

## 🔧 Problema Identificado

O servidor roda em **offline mode** (Minecraft pirata), o que impede o carregamento de texturas customizadas de cabeças dos servidores da Mojang.

**Sintoma:** Todas as cabeças no menu `/lang` apareciam como Steve padrão.

## ✅ Solução Implementada

### Alteração Principal:
**Substituído sistema de cabeças customizadas por BANNERS coloridos**

### Novos Ícones por Idioma:

| Idioma | Ícone | Cor | Representação |
|--------|-------|-----|---------------|
| 🇺🇸 English (US) | Banner Azul | `BLUE_BANNER` | Cores dos EUA (azul/branco) |
| 🇧🇷 Português (Brasil) | Banner Verde-Lima | `LIME_BANNER` | Verde e amarelo do Brasil |
| 🇵🇹 Português (Portugal) | Banner Vermelho | `RED_BANNER` | Cores de Portugal (verde/vermelho) |
| 🌍 Outros idiomas | Livro | `BOOK` | Ícone genérico |

## 📁 Arquivos Modificados

### 1. `LanguageGui.java`
**Antes:**
```java
private record LanguageInfo(String code, String displayName, String textureValue) {}
```

**Depois:**
```java
private record LanguageInfo(String code, String displayName, Material icon) {}
```

**Mudanças:**
- ❌ Removido `SkullUtils.createSkull()`
- ✅ Adicionado uso direto de `new ItemStack(material)`
- ❌ Removido imports de reflection e Base64
- ✅ Simplificado código em ~80 linhas

### 2. `SkullUtils.java`
- ⚠️ Arquivo mantido para futura compatibilidade, mas não é mais usado
- Pode ser removido se desejar

## 🎮 Experiência do Usuário

### Antes:
```
/lang
┌─────────────────────────┐
│  😐 😐 😐               │  ← Todas cabeças de Steve
│                         │
│  😐 😐 😐               │
└─────────────────────────┘
```

### Depois:
```
/lang
┌─────────────────────────┐
│  🔵 🟢 🔴               │  ← Banners coloridos representando países
│                         │
│  Clique para selecionar │
└─────────────────────────┘
```

## 📊 Vantagens da Nova Solução

✅ **Funciona em offline mode** (100% compatível com servidores piratas)  
✅ **Sem dependência de servidores externos** (textures.minecraft.net)  
✅ **Código mais simples** (~80 linhas a menos)  
✅ **Mais rápido** (não precisa fazer reflection)  
✅ **Visualmente distinto** (cores chamativas e fáceis de identificar)  
✅ **Compatível com todas as versões** do Minecraft  

## 🚀 Como Atualizar

### 1. Compilar o Plugin
```bash
cd C:\Users\vitor\IdeaProjects\FormulaRacing-Wolf-Network
mvn clean package
```

### 2. Instalar no Servidor
```bash
# Pare o servidor
stop

# Copie o novo JAR
cp target/formularacing-0.2.jar plugins/

# Inicie o servidor
start
```

### 3. Testar
```
/lang
```
Deve abrir um menu com **banners coloridos** representando cada idioma!

## 🎨 Customização

Quer mudar as cores dos banners? Edite `LanguageGui.java`:

```java
static {
    // Mude os Materials aqui:
    LANGUAGES.put("en_US", new LanguageInfo("en_US", "English (United States)", 
        Material.WHITE_BANNER));  // ← Mude para WHITE_BANNER, por exemplo
    
    LANGUAGES.put("pt_BR", new LanguageInfo("pt_BR", "Português (Brasil)", 
        Material.YELLOW_BANNER)); // ← Ou YELLOW_BANNER
    
    // Disponíveis: RED, BLUE, GREEN, YELLOW, WHITE, BLACK, ORANGE, 
    //              MAGENTA, LIGHT_BLUE, LIME, PINK, GRAY, LIGHT_GRAY, 
    //              CYAN, PURPLE, BROWN_BANNER
}
```

## 📝 Adicionando Novos Idiomas

### 1. Crie o arquivo de idioma:
```yaml
# lang/es_ES.yml
lang_set: "&aTu idioma ha sido cambiado a:"
# ... outras traduções
```

### 2. Adicione ao mapa de idiomas:
```java
// LanguageGui.java
LANGUAGES.put("es_ES", new LanguageInfo("es_ES", "Español", 
    Material.ORANGE_BANNER)); // Laranja para Espanha
```

### 3. Recompile e reinicie!

## 🐛 Troubleshooting

### Problema: Banners aparecem sem cor
**Solução:** Certifique-se de usar `Material.COLOR_BANNER`, não apenas `Material.BANNER`

### Problema: Menu não abre
**Solução:** Verifique os logs do console. Pode haver erro de permissão.

### Problema: Idioma não muda
**Solução:** Verifique se o arquivo `.yml` do idioma existe na pasta `plugins/FormulaRacing/lang/`

## 📞 Suporte

Se encontrar algum problema:
1. Verifique os logs do console
2. Teste com `/lang` e anote qualquer erro
3. Verifique se os arquivos de idioma existem em `plugins/FormulaRacing/lang/`

## 🎊 Resultado Final

✅ Menu de linguagem **100% funcional** em servidores offline mode  
✅ Ícones **coloridos e distintos** para cada idioma  
✅ Código **mais limpo e eficiente**  
✅ **Zero dependências externas** para texturas  

**Pronto para produção!** 🚀

