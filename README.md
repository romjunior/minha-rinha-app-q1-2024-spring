# Rinha de Backend 2024/Q1 — Spring Boot

Implementação da **Rinha de Backend 2024/Q1** com Java 21, Spring Boot, imagem nativa GraalVM, PostgreSQL e Nginx.

O projeto expõe uma API de transações e extratos para cinco clientes pré-cadastrados. A prioridade da implementação é preservar a consistência do saldo sob concorrência intensa, dentro do orçamento de recursos da competição.

> Este repositório é disponibilizado sob uma licença proprietária e restritiva. Consulte [LICENSE](LICENSE).

## Visão geral

```mermaid
flowchart LR
    C[Cliente de carga / consumidor HTTP] --> N[Nginx :9999]
    N --> A1[API 01\nSpring Boot nativo]
    N --> A2[API 02\nSpring Boot nativo]
    A1 --> P1[HikariCP\naté 12 conexões]
    A2 --> P2[HikariCP\naté 12 conexões]
    P1 --> DB[(PostgreSQL 16)]
    P2 --> DB
```

| Componente | Responsabilidade |
|---|---|
| Nginx | Ponto público, balanceamento entre as duas instâncias e conexões persistentes ao upstream. |
| APIs | Validação HTTP, regras de negócio, optimistic locking e persistência via Spring Data JDBC. |
| PostgreSQL | Fonte de verdade para clientes, saldo corrente e histórico de transações. |

O `docker-compose.yml` mantém o orçamento total da Rinha: **1,5 CPU** e **525 MB**.

| Serviço | CPU | Memória |
|---|---:|---:|
| api01 | 0,46 | 125 MB |
| api02 | 0,46 | 125 MB |
| nginx | 0,18 | 150 MB |
| PostgreSQL | 0,40 | 125 MB |
| **Total** | **1,50** | **525 MB** |

## Endpoints

### `POST /clientes/{id}/transacoes`

Registra um crédito ou débito.

```json
{
  "valor": 1000,
  "tipo": "d",
  "descricao": "compra"
}
```

Respostas:

| Status | Significado |
|---:|---|
| `200` | Transação registrada; devolve `limite` e `saldo`. |
| `404` | Cliente inexistente. |
| `422` | Corpo inválido ou débito que ultrapassa o limite. |
| `503` | Os conflitos concorrentes excederam o limite de retries. |

### `GET /clientes/{id}/extrato`

Retorna saldo, limite, horário do extrato e as últimas dez transações do cliente.

## Consistência em concorrência

Cada transação é processada por uma tentativa curta do Spring Data JDBC: a API lê o cliente cacheado e o saldo, valida o limite em Java, atualiza o saldo com optimistic locking e registra o lançamento na mesma transação. O Nginx mantém as transações de um cliente na mesma API, que as serializa localmente; um conflito de versão permanece como proteção residual e é repetido em uma nova transação.

```mermaid
sequenceDiagram
    participant H as Requisição HTTP
    participant A as API
    participant D as PostgreSQL

    H->>A: POST /clientes/{id}/transacoes
    A->>A: valida valor, tipo e descrição
    N->>A: roteia a URI do cliente de forma estável
    A->>A: serializa operações do mesmo cliente
    A->>D: carrega saldo e cliente cacheado
    A->>A: calcula e valida o novo saldo
    A->>D: UPDATE saldo WHERE version = versão lida + INSERT transação
    Note over A,D: Conflito de versão desfaz a tentativa<br/>e a API repete com dados atuais
    alt cliente inexistente
        D-->>A: CLIENTE_NAO_ENCONTRADO
        A-->>H: 404
    else limite insuficiente
        D-->>A: SALDO_INSUFICIENTE
        A-->>H: 422
    else conflito de versão
        D-->>A: OptimisticLockingFailureException
        A->>A: espera aleatória curta e repete
    else operação aceita
        D-->>A: saldo e limite atualizados
        A-->>H: 200
    end
```

O campo `version` de `saldos` é atualizado a cada gravação. Somente uma tentativa que leu uma versão pode atualizá-la; as demais recebem conflito, relêem o saldo e validam novamente o limite. A afinidade de cliente no Nginx e os locks em stripes evitam esses conflitos no caminho normal, mantendo o optimistic locking como garantia entre processos.

```mermaid
erDiagram
    CLIENTES ||--|| SALDOS : possui
    CLIENTES ||--o{ TRANSACOES : registra

    CLIENTES {
        int id PK
        varchar nome
        int limite
    }
    SALDOS {
        int id PK
        int cliente_id UK
        int valor
        bigint version
    }
    TRANSACOES {
        int id PK
        int cliente_id
        int valor
        char tipo
        varchar descricao
        timestamptz realizada_em
    }
```

## Como executar

Pré-requisitos: Docker, Docker Compose e Java 21 para executar os testes locais.

```bash
./build-image.sh
docker compose up
```

A API fica disponível em `http://localhost:9999`.

Para encerrar os containers:

```bash
docker compose down
```

## Testes

```bash
./gradlew test
```

Além de testes de serviço e controller, o projeto possui um teste de integração com Testcontainers que envia débitos concorrentes ao mesmo cliente. Ele confirma que apenas os débitos permitidos são aceitos e que o saldo e o histórico final permanecem coerentes.

## Resultado de carga registrado

No cenário oficial de crédito da Rinha, a configuração atual concluiu todas as requisições sem erro:

| Métrica | Resultado |
|---|---:|
| Requisições | 61.503 |
| Erros | 0 |
| Vazão média | 251,03 req/s |
| Tempo médio | 2.542 ms |
| P50 | 2.662 ms |
| P95 | 5.241 ms |
| P99 | 6.482 ms |
| Máximo | 7.353 ms |
| Abaixo de 800 ms | 12.826 (21%) |

Esse resultado é uma referência de uma execução local: hardware, Docker, versão do banco e carga concorrente influenciam diretamente os números.

## Aprendizados e decisões de desempenho

### O pool de conexões é um limite de concorrência, não um acelerador

Elevar indiscriminadamente o HikariCP de 12 para 16 conexões por API criou mais sessões concorrendo pelas mesmas linhas de saldo e levou a esgotamento do pool, timeouts e respostas `504`. O tamanho atual limita a pressão sobre o banco e mantém as threads HTTP alinhadas ao número de conexões.

### O hot spot são cinco saldos

O teste concentra operações em apenas cinco clientes. Escritas para um mesmo cliente precisam ser serializadas para manter o saldo correto; portanto, adicionar conexões não remove essa dependência. Quando a taxa de chegada supera a capacidade de processar essas filas, a latência cresce junto com o número de usuários ativos.

### Backpressure precisa respeitar a memória

O Tomcat limita as conexões e a fila de aceitação a 512 por API. Isso impede que milhares de requisições pendentes consumam todo o orçamento de memória; capacidade sustentável continua sendo a métrica principal.

### A transação deve ser atômica e curta

A atualização do saldo e o `INSERT` no histórico ocorrem na mesma transação Spring. O `@Version` impede que duas tentativas gravem a partir do mesmo saldo; o coordenador de retries abre uma nova transação após cada conflito. O índice `(cliente_id, realizada_em DESC, id DESC)` mantém o extrato das últimas dez transações eficiente.

### Ajustes de PostgreSQL devem respeitar o orçamento

O banco usa buffers e memória de trabalho modestos, número máximo de conexões compatível com os pools e `synchronous_commit=off`. A última opção reduz a espera por flush de WAL e preserva atomicidade para sessões ativas, mas pode perder transações recentemente confirmadas em uma queda abrupta do PostgreSQL. É uma decisão de desempenho deliberada para este ambiente de benchmark.

### Próximos experimentos

Para reduzir ainda mais a parcela acima de 800 ms, o próximo experimento é batching por cliente: um worker por stripe pode calcular vários lançamentos em Java e persistir o conjunto em uma transação curta, sempre mantendo a ordem e a atomicidade das operações.

## Licença

Copyright © 2026. Todos os direitos reservados.

O código não pode ser usado, copiado, modificado, distribuído, sublicenciado ou explorado comercialmente sem autorização prévia e expressa do titular. Consulte o arquivo [LICENSE](LICENSE) para os termos completos.
