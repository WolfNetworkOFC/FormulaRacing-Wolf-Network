# Script de Atualização do Banco de Dados
# Execute este comando no banco de dados SQLite ou MySQL para corrigir idiomas antigos

## Para SQLite:
```sql
-- Atualizar todos os jogadores com idioma 'en' para 'en_US'
UPDATE fr_players SET lang = 'en_US' WHERE lang = 'en';

-- Verificar se há outros idiomas com formato incorreto
SELECT DISTINCT lang FROM fr_players;
```

## Para MySQL:
```sql
-- Atualizar todos os jogadores com idioma 'en' para 'en_US'
UPDATE fr_players SET lang = 'en_US' WHERE lang = 'en';

-- Verificar se há outros idiomas com formato incorreto
SELECT DISTINCT lang FROM fr_players;
```

## Verificações Adicionais:

```sql
-- Ver quantos jogadores têm cada idioma
SELECT lang, COUNT(*) as total FROM fr_players GROUP BY lang;

-- Atualizar jogadores com lang NULL para en_US
UPDATE fr_players SET lang = 'en_US' WHERE lang IS NULL OR lang = '';
```

## Nota:
Após executar estes comandos, reinicie o servidor para que as mudanças tenham efeito completo.

