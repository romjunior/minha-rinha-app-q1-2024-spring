# Premissas da migração para Spring Data JDBC

- Priorizar uma implementação idiomática com Java e Spring Data JDBC em vez de SQL complexo e específico do PostgreSQL.
- Modelar `Cliente`, `Saldo` e `Transacao` como aggregate roots independentes, com repositories tipados do Spring Data JDBC.
- Preferir operações CRUD e métodos derivados pelo Spring Data JDBC. Usar `@Query` apenas quando a consulta não puder ser expressa adequadamente por um método derivado.
- Não usar `NamedParameterJdbcTemplate`, SQL em strings, CTEs manuais ou `nativeQuery`. O atributo `nativeQuery` não faz parte do Spring Data JDBC.
- Garantir consistência concorrente com optimistic locking: `Saldo` deve ter `@Version`, e conflitos devem ser repetidos em Java em uma nova transação.
- Manter as tentativas transacionais curtas. O coordenador de retry não deve abrir uma transação externa; ele deve repetir somente conflitos otimistas.
- Limitar retries a 12 tentativas, aguardando aleatoriamente entre 1 e 3 ms a cada conflito. Após o limite, responder `503 Service Unavailable`.
- Preservar os contratos existentes: cliente inexistente retorna `404` e débito acima do limite retorna `422`.
- Manter o extrato em transação somente leitura, com isolamento `REPEATABLE_READ`, carregando cliente, saldo e as 10 últimas transações e montando a resposta em Java.
- Usar hash da URI no Nginx para manter as transações de um cliente na mesma API. Processar cada cliente em uma fila assíncrona e em batches ordenados; o optimistic locking permanece como proteção residual.
- Cachear os clientes, pois seus limites são imutáveis no cenário da aplicação.
- Limitar as filas HTTP e as filas por cliente para caber no orçamento de memória; não aumentar timeouts como resposta a saturação.
- A estrutura do banco permanece a atual, adicionando somente a coluna `version` em `saldos` para optimistic locking.
- Preservar e adaptar os testes de serviço, contrato HTTP, retry e concorrência com PostgreSQL/Testcontainers.
