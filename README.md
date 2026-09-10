<div align="center">

# 🏎️ FormulaRacing

**Plugin de corrida estilo F1 para Minecraft Java Edition**

[![Paper](https://img.shields.io/badge/Paper-1.21+-blue.svg)](https://papermc.io)
[![Folia](https://img.shields.io/badge/Folia-Supported-green.svg)](https://papermc.io/software/folia)
[![Geyser](https://img.shields.io/badge/Bedrock-Compatible-orange.svg)](https://geysermc.org)
[![License](https://img.shields.io/badge/License-Private-red.svg)](LICENSE)

*[Corra. Compita. Vença.]*

</div>

---

## Sobre o Projeto

FormulaRacing é um plugin de corrida completo para servidores Paper que simula a experiência de um campeonato de F1 dentro do Minecraft. Com suporte a pistas customizadas, sistema de campeonato, IA para oponentes e física de barcos modificável, o plugin transforma servidores Minecraft em verdadeiros circuitos de corrida.

Desenvolvido e utilizado pela **Wolf Network**, servidor brasileiro de Minecraft.

---

## Funcionalidades Principais

### Sistema de Corrida
- **Time Trial** - Cronometre suas voltas e bata recordes pessoais
- **Quick Race** - Corridas rápidas casuais entre jogadores
- **Duels** - Desafios 1v1 diretos
- **Party Race** - Corridas em grupo com amigos

### Campeonato e Competição
- **Ligas e Campeonatos** - Sistema completo de temporadas
- **ELO/Ranking** - Classificação baseada em habilidade
- **Medalhas e Conquistas** - Recompensas por desempenho
- **Fantasmas (Ghosts)** - Corra contra suas melhores voltas

### Inteligência Artificial
- **Oponentes IA** - Corra contra bots inteligentes
- **Linha de Corrida** - IA aprende e segue linhas ótimas
- **Dificuldade Ajustável** - Do iniciante ao especialista

### Pistas e Editor
- **Editor Completo** - Crie pistas com `/trackedit`
- **Checkpoints** - Sistema de detecção preciso
- **Grid de Largada** - Posicionamento automático
- **Pit Lane** - Configuração de boxes

### Física Customizável
- **OpenBoatUtils** - Modifique física dos barcos
- **DRS, ERS, Push to Pass** - Sistemas de corrida reais
- **Slipperiness por Bloco** - Controle de aderência
- **Step Height** - Barcos subirem blocos

### Compatibilidade Bedrock
- **Geyser/Floodgate** - Suporte completo a jogadores Bedrock
- **UI Adaptada** - Formulários nativos para mobile
- **Scoreboards Otimizados** - Funciona corretamente em ambas edições

---

## Requisitos

| Dependência | Tipo | Obrigatório |
|-------------|------|-------------|
| Paper 1.21+ | Servidor | ✅ Sim |
| WorldEdit | Plugin | ✅ Sim |
| TAB | Plugin | ❌ Não |
| PlaceholderAPI | Plugin | ❌ Não |
| LuckPerms | Plugin | ❌ Não |
| Geyser-Spigot | Plugin | ❌ Não |

---

## Instalação

### Paper/Spigot

```bash
# 1. Baixe o JAR na seção de releases
# 2. Coloque na pasta plugins/
cp FormulaRacing.jar plugins/

# 3. Reinicie o servidor
# 4. Configure o config.yml gerado
```

### Build Manual

```bash
git clone https://github.com/WolfNetworkOFC/FormulaRacing-Wolf-Network.git
cd FormulaRacing-Wolf-Network
./gradlew build

# JAR estará em build/libs/
```

---

## Comandos

### Jogador
| Comando | Descrição |
|---------|-----------|
| `/timetrial` ou `/tt` | Inicia Time Trial |
| `/lonely` | Time trial solitário |
| `/race` ou `/qr` | Quick Races |
| `/party` ou `/p` | Sistema de Party |
| `/duel` | Duelo 1v1 |
| `/voterace` ou `/vr` | Votação de pista |
| `/reset` | Reseta time trial |
| `/resetcp` | Volta ao checkpoint |
| `/spectate` ou `/watch` | Modo espectador |
| `/ghost` | Ativa ghost |
| `/settings` ou `/s` | Configurações |
| `/language` ou `/lang` | Mudar idioma |

### Admin
| Comando | Descrição |
|---------|-----------|
| `/track` ou `/t` | Gerenciar pistas |
| `/trackedit` ou `/te` | Editor de pistas |
| `/event` | Gerenciar eventos |
| `/heat` | Gerenciar baterias |
| `/round` | Gerenciar rodadas |
| `/admin` ou `/fra` | Comandos admin |
| `/debug` | Modos de debug |
| `/frr` | Recarregar config |

---

## Configuração Rápida

```yaml
# config.yml - Exemplo
general:
  language: pt_BR
  max-tracks: 50

physics:
  default-slipperiness: 0.98
  step-height: 0.6
  water-elevation: true

scoreboard:
  max-rows: 15
  update-interval: 500ms

bedrock:
  enabled: true
  use-forms: true
  scoreboard-width: 320
```

---

## Compatibilidade Bedrock

O plugin detecta automaticamente jogadores Bedrock via Floodgate e adapta:

- **Scoreboards** - Largura ajustada para não cortar texto
- **UI** - Forms API nativa em vez de inventários
- **Comandos** - Completar e sugestões adaptadas
- **Physics** - Física equivalente via comandos de servidor

### Limitações Conhecidas

- Jogadores Bedrock precisam ter OpenBoatUtils instalado para física customizada completa
- Algumas animações podem diferir ligeiramente entre edições

---

## Desenvolvimento

### Estrutura do Projeto

```
src/main/java/dev/EfraGroup/formulaRacing/
├── AI/              # Sistema de oponentes IA
├── Command/         # Todos os comandos
├── Controllers/     # Controladores de corrida
├── Database/        # Persistência de dados
├── Ghost/           # Sistema de fantasmas
├── Gui/             # Interface de inventário
├── Heat/            # Sistema de baterias
├── League/          # Ligas e campeonatos
├── Listener/        # Eventos do servidor
├── Utils/           # Utilitários
└── FormulaRacing.java # Classe principal
```

### Build

```bash
./gradlew build        # Build completo
./gradlew runClient    # Testar em cliente
./gradlew test         # Executar testes
```

---

## Créditos

**Desenvolvimento:**
- [WolfNetworkOFC](https://github.com/WolfNetworkOFC)
- EfraGroup

**Contribuidores:**
- Todos os membros da Wolf Network

**Agradecimentos:**
- Comunidade GeyserMC pelo suporte cross-play
- PaperMC pela API robusta

---

## Licença

Este projeto é de uso privado da Wolf Network. Contate para permissão de uso.

---

<div align="center">

**[Wolf Network](https://wolfnetwork.com.br)** • **[Discord](https://discord.gg/wolfnetwork)**

© 2025 Wolf Network. Todos os direitos reservados.

</div>
