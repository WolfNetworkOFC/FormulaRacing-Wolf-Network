# Matriz de Compatibilidade e Paridade (Fase Atual)

## Escopo desta matriz
- Comandos alterados na implementacao desta fase.
- Objetivo: garantir contrato externo estavel e comportamento previsivel.

## HeatCommand

### `/heat adddriver|join <heat> <player>`
- Permissao: `formularacing.event.admin`
- Assinatura mantida: sim
- Mudanca interna: delega para `HeatDriverCommandService`
- Efeito em dominio: adiciona piloto no heat, valida estado editavel, bloqueia duplicidade no round
- Efeito em DB: insert com shift atomico de grid
- Confirmacao de sucesso: inclui posicao final aplicada
- Falhas principais:
  - contexto invalido
  - estado de heat nao editavel
  - piloto ja no heat
  - piloto ja em outro heat do mesmo round
  - heat lotado
  - conflito de edicao concorrente
  - erro de persistencia/sincronizacao

### `/heat adddriver|join <heat> <player> <position>`
- Permissao: `formularacing.event.admin`
- Compatibilidade: adicionada como extensao sem quebrar assinatura antiga
- Efeito em dominio/DB: igual ao comando acima, com posicao alvo explicita
- Falha adicional: posicao invalida

### `/heat removedriver|leave <heat> <player>`
- Permissao: `formularacing.event.admin`
- Assinatura mantida: sim
- Mudanca interna: delega para `HeatDriverCommandService`
- Efeito em dominio: remove piloto do heat (somente estado editavel)
- Efeito em DB: delete + reindexacao atomica de grid
- Confirmacao de sucesso: identifica heat e piloto removido
- Falhas principais:
  - contexto invalido
  - estado de heat nao editavel
  - piloto nao estava no heat
  - conflito de edicao concorrente
  - erro de persistencia/sincronizacao

## RoundCommand

### `/round clear|removedrivers <round>`
- Permissao: `formularacing.event.admin`
- Assinatura mantida: sim
- Mudanca interna: limpeza de DB agora com operacao sincrona por heat (`clearHeatDriversSync`)
- Efeito em dominio: limpa pilotos do heat em memoria e reordena grid vazio
- Efeito em DB: `DELETE` de todos pilotos do heat, com retorno de sucesso/erro por heat
- Comportamento em falha: interrompe fluxo no primeiro heat que falhar e reporta erro especifico

### `/round fill|fillheats <round> <random|sorted> <all|signed|reserves>`
- Permissao: `formularacing.event.admin`
- Assinatura mantida: sim
- Mudanca interna: preenchimento agora usa `HeatDriverCommandService` para mutacao consistente
- Efeito em dominio: valida estado editavel de cada heat antes de mutar
- Efeito em DB: insercao atomica por piloto com reindexacao segura
- Comportamento em falha: exibe motivo por piloto (duplicado, conflito, persistencia, etc.)

## EventCommand

### `/event create|new <name> <track>` e `/event createfull ...`
- Compatibilidade: mantida
- Ajuste de thread-safety: callback agora volta para main thread antes de enviar mensagens ao jogador
- Efeito funcional: inalterado

### `/event delete|remove|del <event>`
- Compatibilidade: mantida
- Ajuste: validacao explicita de `event == null` para evitar falha de contexto

### `/event settrack|track <event> <track...>`
- Compatibilidade: mantida
- Ajuste: sucesso condicionado ao retorno real de `event.setTrack(...)`
- Comportamento em falha: mensagem de erro quando pista invalida/nao encontrada

## Notas de paridade
- Fluxos de `adddriver/removedriver` agora seguem o comportamento transacional esperado (estilo TimingSystem):
  - validacao antes de mutar
  - mutacao atomica de grid no DB
  - sincronizacao de estado em memoria apos persistencia
  - sem confirmacao de sucesso em estado parcial
