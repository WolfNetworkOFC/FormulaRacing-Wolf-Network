# 🚀 GUIA RÁPIDO - Compilar e Testar

## ⚡ Passo a Passo

### 1️⃣ Compilar o Plugin
```powershell
cd C:\Users\vitor\IdeaProjects\FormulaRacing-Wolf-Network
mvn clean package
```

**Resultado esperado:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: 10-30 segundos
```

O arquivo gerado estará em:
```
target/formularacing-0.2.jar
```

---

### 2️⃣ Instalar no Servidor

**Opção A: Copiar manualmente**
```powershell
# Pare o servidor primeiro
stop

# Copie o JAR
Copy-Item target/formularacing-0.2.jar -Destination "CAMINHO_DO_SERVIDOR/plugins/"

# Inicie o servidor
start
```

**Opção B: Usar plugin reload (NÃO RECOMENDADO para primeira instalação)**
```
/reload confirm
```

---

### 3️⃣ Verificar Instalação

No console do servidor, procure por:
```
[FormulaRacing] Plugin habilitado com sucesso!
```

---

### 4️⃣ Testar no Jogo

#### Teste Básico:
```
/lang
```

**Resultado esperado:**
- ✅ Menu abre com banners coloridos
- ✅ Banner azul para English
- ✅ Banner verde para Português (Brasil)
- ✅ Banner vermelho para Português (Portugal)
- ✅ Bússola no canto inferior direito

#### Teste de Seleção:
1. Clique em um banner de idioma diferente
2. Menu fecha
3. Recebe mensagem: "Seu idioma foi alterado para: [Idioma]"

#### Teste de Comandos:
```
/lang list          → Lista idiomas disponíveis
/lang set pt_BR     → Muda para português do Brasil
/lang help          → Mostra ajuda
```

---

## 🔍 Checklist de Testes

### ✅ Testes Obrigatórios:

- [ ] Plugin compila sem erros
- [ ] Plugin carrega no servidor
- [ ] Comando `/lang` abre o menu
- [ ] Banners aparecem com cores corretas:
  - [ ] 🔵 Azul para en_US
  - [ ] 🟢 Verde-Lima para pt_BR
  - [ ] 🔴 Vermelho para pt_PT
- [ ] Clicar em banner muda o idioma
- [ ] Mensagem de confirmação aparece
- [ ] Idioma atual tem checkmark (✓) verde
- [ ] Item de informação (bússola) aparece
- [ ] Comando `/lang set pt_BR` funciona
- [ ] Comando `/lang list` mostra idiomas
- [ ] Comando `/lang help` mostra ajuda

### ✅ Testes de Offline Mode:

- [ ] Funciona com `online-mode=false`
- [ ] Banners carregam instantaneamente
- [ ] Não aparecem cabeças de Steve
- [ ] Sem erros no console

---

## 🐛 Possíveis Problemas

### Problema 1: Erro ao compilar
```
[ERROR] Failed to execute goal
```

**Solução:**
```powershell
# Limpe o cache do Maven
mvn clean

# Tente novamente
mvn package
```

---

### Problema 2: Menu não abre
```
/lang
[Erro] Unknown command
```

**Solução:**
- Verifique se o plugin está carregado: `/plugins`
- Verifique permissões no `plugin.yml`
- Recarregue o servidor

---

### Problema 3: Banners sem cor
Os banners aparecem brancos/genéricos.

**Solução:**
- Verifique se está usando `Material.COLOR_BANNER` (ex: `BLUE_BANNER`)
- Recompile o plugin
- Reinicie o servidor (não use /reload)

---

### Problema 4: Console mostra erros
```
[ERROR] Could not load 'plugins/formularacing-0.2.jar'
```

**Solução:**
1. Verifique a versão do Java: `java -version` (deve ser 17+)
2. Verifique a versão do servidor (deve ser 1.21+)
3. Verifique dependências no pom.xml

---

## 📊 Verificação de Sucesso

### ✅ Tudo Funcionando:
```
Jogador: /lang
[Sistema] Abre menu com banners coloridos
[Jogador] Clica no banner verde (Brasil)
[Sistema] Seu idioma foi alterado para: Português (Brasil)
[Jogador] /lang
[Sistema] Banner verde agora tem ✓ verde
```

### ❌ Algo Errado:
```
Jogador: /lang
[Sistema] Unknown command
OU
[Sistema] Menu abre mas com itens errados
OU
[Sistema] Menu não fecha ao clicar
```

→ Veja seção "Possíveis Problemas" acima

---

## 🎯 Comandos de Teste Rápido

### Teste Completo (copie e cole):
```
/lang
/lang list
/lang set pt_BR
/lang
/lang set en_US
/lang
/lang help
```

**Resultado esperado de cada comando:**
1. `/lang` → Menu abre
2. `/lang list` → Lista: en_US, pt_BR, pt_PT
3. `/lang set pt_BR` → "Seu idioma foi alterado para: Português (Brasil)"
4. `/lang` → Menu abre com ✓ no banner verde
5. `/lang set en_US` → "Your language was updated to: English (United States)"
6. `/lang` → Menu abre com ✓ no banner azul
7. `/lang help` → Mostra comandos disponíveis

---

## 📝 Log de Console Esperado

### Inicialização:
```
[INFO] [FormulaRacing] Enabling FormulaRacing v0.2
[INFO] [FormulaRacing] Sistema de linguagem inicializado
[INFO] [FormulaRacing] 3 idiomas carregados: en_US, pt_BR, pt_PT
[INFO] [FormulaRacing] Plugin habilitado com sucesso!
```

### Ao abrir menu:
```
[DEBUG] Jogador 'Nome' abriu menu de linguagem
[DEBUG] Idioma atual: pt_BR
```

### Ao trocar idioma:
```
[INFO] Jogador 'Nome' mudou idioma de pt_BR para en_US
```

---

## 🎊 Pronto!

Se todos os testes passaram:
✅ **O sistema está 100% funcional!**

Aproveite o novo sistema de linguagem com:
- 🔵 Banners coloridos
- ⚡ Performance otimizada
- 🏴‍☠️ Suporte offline mode
- 🎨 Visual intuitivo

---

## 📚 Documentação Adicional

Para mais detalhes, consulte:
- `LANGUAGE_SYSTEM_README.md` - Documentação completa
- `OFFLINE_MODE_FIX.md` - Detalhes técnicos da solução
- `ANTES_DEPOIS.md` - Comparação visual
- `TROUBLESHOOTING_STEVE_HEADS.md` - Guia de problemas

---

**Qualquer dúvida, consulte os arquivos de documentação criados!** 📖

