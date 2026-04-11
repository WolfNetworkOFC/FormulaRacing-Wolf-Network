# Reimplementacao de Comandos Event/Round/Heat

## Status
- Modo: design validado (sem implementacao)
- Escopo: reimplementacao completa por fluxo (comando + dominio + persistencia relacionada)
- Compatibilidade: 100% com comandos atuais
- Revisao multi-agente: REVISE (aplicado pacote minimo de revisoes neste documento)

## Understanding Summary
- Reimplementar os comandos ligados a `event`, `round` e `heat` com foco em estabilidade de fluxo.
- Corrigir comportamentos bugados, com prioridade para `/heat adddriver` e `/heat removedriver`.
- Usar o TimingSystem como referencia de paridade comportamental.
- Preservar aliases, parametros e permissoes atuais (sem quebra de UX operacional).
- Garantir resposta de comando rapida, com operacoes pesadas/criticas controladas com consistencia.
- Operacoes criticas devem seguir fail-fast com rollback e sem estado parcial.
- Criterio de sucesso: paridade comportamental + bugs criticos zerados.

## Assumptions
- Nao havera migracao de schema SQL nesta etapa.
- Nao serao introduzidos novos recursos funcionais.
- Mudancas estruturais serao internas (organizacao por servicos/casos de uso), sem alterar contrato de comandos.
- A camada de comando permanece fina; regras complexas migram para servicos.
- Paridade com TimingSystem vale para validacoes, consistencia de grid e comportamento transacional dos fluxos criticos.

## Fronteira de Escopo (Congelada)
- **Incluido nesta fase**:
  - Fluxos de comando de `event/round/heat` ligados a mutacao de estado e gestao de pilotos/heat.
  - Reimplementacao completa por fluxo com foco operacional em `adddriver` e `removedriver`.
  - Ajustes internos de servico/dominio/persistencia sem mudar contratos externos.
- **Explicitamente fora**:
  - Novas features.
  - Migracao de schema.
  - Mudancas de assinatura de comando/permissao.

## Nao-Objetivos
- Adicionar novas features de evento/corrida.
- Fazer migracao estrutural de tabelas.

## Decision Log
1. Escopo fechado como reimplementacao completa por fluxo (nao patch pontual).
2. Compatibilidade de comando definida como 100%.
3. Referencia escolhida: paridade comportamental com TimingSystem.
4. Performance alvo: resposta imediata para comando.
5. Escala alvo: servidor unico medio (dezenas de pilotos, poucos eventos simultaneos).
6. Seguranca/permissoes: rigor maximo.
7. Confiabilidade: fail-fast com rollback.
8. Nao-objetivos confirmados: sem novos recursos e sem migracao de schema.
9. Abordagem escolhida: Service Layer transacional por fluxo (Opcao 1).
10. Comandos permanecem finos; servicos orquestram validacao, dominio, persistencia e sincronizacao runtime.
11. Fluxos criticos (`adddriver`/`removedriver`) serao atomicos e sem sucesso parcial.
12. Lock logico por `heatId` para reduzir conflito entre operacoes administrativas concorrentes.
13. Contratos padronizados entre camadas: `CommandContext` e `CommandResult`.
14. Taxonomia de erro: validacao, conflito e persistencia.
15. Observabilidade enxuta: inicio, decisao de validacao, resultado final (commit/rollback).
16. Aceite por checklist funcional focado em paridade e estabilidade.
17. Revisao multi-agente exigiu pacote minimo: thread model, lock policy, tx policy, recovery pos-commit, matriz de compatibilidade/paridade, UX operacional e gates de aceite NFR.

## Design Final

### 1) Arquitetura
- **Command Layer (`EventCommand`, `RoundCommand`, `HeatCommand`)**
  - Responsavel apenas por parse, permissao, resolucao de contexto e envio de mensagens.
  - Nao deve executar regra de negocio complexa nem escrever em multiplos componentes diretamente.
- **Service Layer (casos de uso)**
  - Casos de uso dedicados, por exemplo:
    - `AddDriverToHeatUseCase`
    - `RemoveDriverFromHeatUseCase`
    - `CreateRoundUseCase`
    - `CreateHeatUseCase`
  - Sequencia unica: validar -> aplicar regra de dominio -> persistir atomicamente -> sincronizar runtime.
- **Dominio (`Heats`, `Rounds`, `Events`)**
  - Mantem invariantes e regras (duplicidade, limites de grid, estados validos).
- **Persistencia (`EventsDatabaseManager`)**
  - Operacoes atomicas para mutacao de grid e pilotos, com rollback em erro.

### 2) Contratos entre camadas
- **`CommandContext`**
  - `actorUuid`, contexto resolvido (`eventId`, `roundId`, `heatId`), alvo (`driverUuid`), metadados de permissao.
- **`CommandResult`**
  - `status`: `SUCCESS`, `VALIDATION_ERROR`, `CONFLICT`, `PERSISTENCE_ERROR`.
  - `messageKey` + placeholders para i18n.
  - `details` tecnico apenas para log.

### 2.1) Contrato de Mensageria Operacional (Admin)
- `SUCCESS`: inclui confirmacao do estado final aplicado (`event/round/heat`, piloto alvo, posicao final quando aplicavel).
- `VALIDATION_ERROR`: motivo especifico + acao esperada (ex.: estado nao editavel, posicao invalida, piloto ja presente).
- `CONFLICT`: informa contencao concorrente no recurso e orienta retry.
- `PERSISTENCE_ERROR`: informa falha operacional e confirma ausencia de sucesso parcial.
- Distincao obrigatoria de wording entre: `nao encontrado`, `invalido`, `sem permissao`.

### 3) Fluxo critico: `/heat adddriver`
1. Resolver contexto e alvo.
2. Validar estado do heat (somente editavel), capacidade e duplicidade no round.
3. Definir posicao de insercao (informada ou final da fila).
4. Persistir atomicamente com shift de posicoes + insert.
5. Atualizar memoria em conformidade com estado persistido.
6. Responder sucesso apenas apos convergencia memoria/DB.

### 4) Fluxo critico: `/heat removedriver`
1. Resolver contexto e alvo.
2. Validar pertencimento ao heat e estado editavel.
3. Persistir atomicamente: detectar posicao, remover e reindexar subsequentes.
4. Atualizar memoria refletindo a nova ordem.
5. Retornar sucesso apenas apos convergencia memoria/DB.

### 5) Confiabilidade e concorrencia
- Fail-fast com rollback para erros de persistencia.
- Sem sucesso parcial ao jogador.
- Lock logico em nivel de `roundId` + `heatId` para proteger invariantes de round e mutacoes locais do heat.

### 5.1) Politica de Lock (Obrigatoria)
- Regra canonica:
  - `adddriver` e `removedriver` usam **lock primario por `roundId`** (invariante de membro unico no round).
  - Operacoes estritamente locais de heat (sem tocar invariante de round) podem usar lock por `heatId`.
- `tryLock` com timeout curto e retorno `CONFLICT` em contencao.
- Liberacao obrigatoria em `finally`.
- Ordem fixa de aquisicao (`roundId` -> `heatId`) quando ambos forem necessarios, para evitar deadlock.
- Escopo de lock limitado ao caso de uso critico.

### 5.2) Politica Transacional (Obrigatoria)
- Cada mutacao critica (`adddriver`/`removedriver`) roda em uma unica transacao de banco.
- Conflito/deadlock: retorna `CONFLICT` com retry limitado (sem loop infinito).
- Semantica idempotente para evitar efeito duplicado em reexecucao acidental do mesmo comando:
  - chave de operacao em memoria com TTL curto: `opKey = actorUuid + command + roundId + heatId + targetUuid + position`;
  - enquanto `opKey` estiver em execucao/sucesso recente, retries repetidos retornam mesmo resultado logico;
  - verificacao final de estado (read-after-write) antes de confirmar sucesso em comandos de mutacao.

### 5.3) Falha Pos-Commit e Recuperacao
- Se commit no DB ocorrer e sincronizacao runtime falhar, o fluxo marca falha operacional.
- Acao imediata: recarregar estado autoritativo do recurso a partir do DB antes de liberar resultado final.
- Usuario recebe erro operacional sem sucesso parcial confirmado.
- Se houver timeout/resultado incerto, o fluxo executa verificacao deterministica de estado no DB (read-after-write) antes de decidir entre `SUCCESS` ou `PERSISTENCE_ERROR`.

### 6) Performance e escala
- Validacoes leves e resposta rapida de comando.
- Operacoes criticas executadas de forma controlada para nao bloquear indevidamente.
- Sem lock global do plugin; isolamento por recurso com regra canonica de lock (round para invariantes compartilhadas, heat para mutacoes locais).

### 6.1) Contrato de Threading (Paper/Spigot)
- Trabalho de DB/transacao/lock fora da main thread.
- Mutacao de estado Bukkit (teleporte, entidades, scoreboard/actionbar) somente na main thread.
- Handoff explicito entre fases async e main thread.
- Proibido acesso Bukkit thread-unsafe em tarefas async.

### 7) Seguranca e permissoes
- Gate explicito de permissao por subcomando.
- Isolamento de contexto selecionado por jogador.
- Mensagens de erro ao usuario sem vazamento tecnico interno.
- Defesa em profundidade: servicos validam precondicoes de autorizacao/contexto mesmo quando chamados fora da camada de comando.

### 8) Observabilidade
- Tres logs estrategicos por operacao critica:
  1) inicio com IDs,
  2) resultado de validacao,
  3) commit/rollback.

### 8.1) Campos Estruturados e SLOs
- Campos obrigatorios de log: `opId`, `eventId`, `roundId`, `heatId`, `driverUuid`, `status`, `durationMs`, `lockWaitMs`, `txOutcome`.
- SLO operacional desta fase:
  - latencia alvo do comando administrativo: resposta perceptivelmente imediata;
  - taxa de erro por conflito/persistencia monitorada por tipo de operacao.

### 9) Edge Cases obrigatorios
- Dois admins alterando o mesmo heat ao mesmo tempo.
- Comando em estado de heat nao editavel.
- Posicao invalida ou gap/duplicidade de grid.
- Divergencia pos-reload entre memoria e banco.
- Alvo offline/UUID ambiguo.
- Falha de banco no meio da operacao.

### 10) Criterios de Aceite
- Paridade comportamental com TimingSystem nos fluxos-alvo.
- Bugs criticos de `/heat adddriver` e `/heat removedriver` zerados.
- Compatibilidade 100% de comandos/permissoes.
- Sem novos recursos e sem migracao de schema.

### 10.1) Matriz de Compatibilidade e Paridade (Gate obrigatorio)
- Para cada subcomando tocado: alias, permissao, assinatura de parametros, mensagem de sucesso, mensagens de falha, efeitos em DB e runtime.
- Baseline de paridade definido por cenarios alvo do TimingSystem (mesmos cenarios, resultado esperado equivalente).

### 10.2) Mapeamento Edge Case -> Teste de Aceite
- Concorrencia no mesmo round/heat -> deve retornar `CONFLICT` sem corrupcao de grid.
- Estado nao editavel -> `VALIDATION_ERROR` sem mutacao.
- Posicao invalida -> `VALIDATION_ERROR` com orientacao.
- Falha DB no meio da operacao -> rollback e sem sucesso parcial.
- Falha pos-commit de runtime sync -> recarga autoritativa e erro operacional controlado.

## Proximo Gate
- Antes de implementacao por se tratar de mudanca de impacto alto em fluxo administrativo e integridade de grid, realizar handoff do design e Decision Log para revisao com `multi-agent-brainstorming`.
