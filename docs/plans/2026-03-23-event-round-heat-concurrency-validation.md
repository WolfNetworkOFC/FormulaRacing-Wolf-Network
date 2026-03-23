# Validacao de Concorrencia (Admin x Admin)

## Objetivo
Validar que mutacoes criticas de heat mantem consistencia sob comandos concorrentes.

## Pre-condicoes
- Dois administradores online (Admin A e Admin B).
- Mesmo evento e mesmo round selecionados.
- Heat em estado editavel (`SETUP` ou `LOADED`).

## Cenarios

### C1 - Conflito no mesmo round (adddriver simultaneo)
1. Admin A executa adddriver no heat alvo.
2. Admin B executa adddriver no mesmo instante (mesmo round).
3. Esperado:
   - uma operacao completa com sucesso
   - outra retorna conflito (retry)
   - grid final sem duplicidade e sem gap

### C2 - Add e remove concorrentes no mesmo round
1. Admin A executa adddriver.
2. Admin B executa removedriver no mesmo instante.
3. Esperado:
   - sem corrupcao de posicoes
   - sem piloto em dois heats do mesmo round
   - mensagens consistentes com resultado efetivo

### C3 - Falha de persistencia durante mutacao
1. Simular indisponibilidade momentanea de DB (ou falha controlada).
2. Executar adddriver/removedriver.
3. Esperado:
   - comando retorna erro operacional
   - sem sucesso parcial confirmado
   - estado final recuperavel por reload do heat

## Criterios de aceite
- Nenhum caso gera duplicidade de piloto no round.
- Nenhum caso deixa grid quebrado (ordem/prefixo de posicoes invalido).
- Conflitos concorrentes retornam mensagem orientando retry.
- Logs possuem contexto minimo (roundId, heatId, status).
