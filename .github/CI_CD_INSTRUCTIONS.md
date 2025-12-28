# 🤖 CI/CD - Compilação Automática

Este repositório está configurado com GitHub Actions para compilar automaticamente o plugin FormulaRacing.

## 📋 Workflows Disponíveis

### 1. Build Automático (`build.yml`)
**Dispara quando:**
- Você faz push para as branches: `main`, `master` ou `develop`
- Abre um Pull Request
- Execução manual

**O que faz:**
- Compila o plugin com Maven
- Gera os arquivos `.jar`
- Salva como artifact por 30 dias

**Como baixar o JAR compilado:**
1. Vá em **Actions** no GitHub
2. Clique no workflow executado
3. Role até **Artifacts**
4. Baixe o `FormulaRacing-Plugin.zip`
5. Extraia o `.jar` e use no servidor

---

### 2. Release Automática (`release.yml`)
**Dispara quando:**
- Você cria uma tag de versão (ex: `v0.2.0`)

**O que faz:**
- Compila o plugin
- Cria uma Release oficial no GitHub
- Anexa os arquivos `.jar` na release

**Como criar uma release:**
```bash
# Commit suas alterações
git add .
git commit -m "Nova versão do plugin"

# Crie e envie a tag
git tag v0.2.1
git push origin v0.2.1
```

Depois vá em **Releases** no GitHub para ver a release criada automaticamente!

---

## 🚀 Como Usar

### Desenvolvimento Diário
Apenas faça push normalmente:
```bash
git add .
git commit -m "Suas alterações"
git push
```

O GitHub irá compilar automaticamente e você pode baixar o JAR nos Artifacts.

### Lançar Nova Versão
Quando quiser criar uma versão oficial:
```bash
git tag v0.3.0
git push origin v0.3.0
```

Uma release será criada automaticamente com os JARs anexados!

---

## 🔧 Configurações

- **Java Version:** 17
- **Build Tool:** Maven
- **Retenção de Artifacts:** 30 dias
- **Testes:** Desabilitados no build (adicione `-DskipTests`)

---

## 📝 Notas

- Os artifacts ficam disponíveis por 30 dias
- Você pode executar os workflows manualmente em **Actions > Workflow > Run workflow**
- Para releases, use tags no formato `vX.Y.Z` (ex: `v1.0.0`)

