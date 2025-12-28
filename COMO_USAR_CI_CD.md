# 🚀 Como Enviar para o GitHub

Siga estes passos para ativar a compilação automática:

## 1. Commit dos arquivos do CI/CD
```powershell
git add .github/
git add README.md
git commit -m "✨ Adicionar CI/CD com GitHub Actions para build automático"
git push
```

## 2. Verificar se funcionou
1. Acesse seu repositório no GitHub
2. Clique na aba **Actions**
3. Você verá o workflow "Build FormulaRacing Plugin" em execução
4. Aguarde alguns minutos até completar (ícone verde ✅)

## 3. Baixar o JAR compilado
1. Na aba **Actions**, clique no workflow concluído
2. Role até a seção **Artifacts**
3. Clique em **FormulaRacing-Plugin** para baixar
4. Extraia o ZIP e copie o arquivo `formularacing-0.2.jar` para a pasta `plugins` do servidor
   - ⚠️ **IMPORTANTE:** Use o arquivo `formularacing-0.2.jar` (contém todas as dependências)
   - ❌ **NÃO USE** o arquivo `original-formularacing-0.2.jar` (versão sem dependências)

---

## 🏷️ Criar uma Release (Opcional)

Para criar uma versão oficial com release automática:

```powershell
# Criar e enviar uma tag de versão
git tag v0.2.1
git push origin v0.2.1
```

Isso irá:
- Compilar o plugin automaticamente
- Criar uma Release no GitHub
- Anexar os JARs na release para download fácil

---

## 📝 Notas Importantes

### Substituir "SEU_USUARIO" no README
No arquivo `README.md`, substitua `SEU_USUARIO` pelo seu username do GitHub para os badges funcionarem corretamente.

Exemplo:
```markdown
# Antes
[![Build Status](https://github.com/SEU_USUARIO/FormulaRacing-Wolf-Network/...

# Depois (exemplo)
[![Build Status](https://github.com/vitoruser/FormulaRacing-Wolf-Network/...
```

### Configurações do Repositório
Certifique-se de que:
- ✅ O repositório tem permissões de Actions habilitadas
- ✅ O repositório tem permissões de escrita para Workflows
  - Vá em **Settings** → **Actions** → **General**
  - Em "Workflow permissions", selecione "Read and write permissions"

---

## ✅ Pronto!

A partir de agora, toda vez que você fizer push para o repositório:
1. GitHub Actions irá compilar automaticamente
2. O JAR estará disponível nos Artifacts
3. Você não precisa mais compilar localmente! 🎉

Para releases oficiais, basta criar uma tag `vX.Y.Z` e enviar.

---

## 📦 Entendendo os Arquivos JAR Gerados

O Maven Shade Plugin gera dois arquivos durante a compilação:

1. **`formularacing-0.2.jar`** ✅
   - JAR principal com **todas as dependências incluídas** (shaded)
   - **Este é o arquivo que você deve usar no servidor**
   - Contém: FastBoard, Adventure API, e outras libs necessárias

2. **`original-formularacing-0.2.jar`** ❌
   - JAR original **sem as dependências**
   - Apenas para referência/debug
   - **NÃO funciona sozinho no servidor**

### Por que isso importa?
- O servidor precisa das bibliotecas (FastBoard, Adventure API) para o plugin funcionar
- O arquivo principal já inclui tudo automaticamente
- Não é necessário instalar nenhuma dependência adicional no servidor


