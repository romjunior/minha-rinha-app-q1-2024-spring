package com.rinha.backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcRinhaRepository implements RinhaRepository {

    private static final String REGISTRAR_TRANSACAO_SQL = """
        WITH atualizado AS (
            UPDATE saldos s
            SET valor = CASE WHEN :tipo = 'c' THEN s.valor + :valor ELSE s.valor - :valor END
            FROM clientes c
            WHERE s.cliente_id = :clienteId
              AND c.id = s.cliente_id
              -- The predicate must use the row being updated. PostgreSQL rechecks it after a row lock wait.
              AND (:tipo = 'c' OR s.valor - :valor >= -c.limite)
            RETURNING s.valor
        ), inserido AS (
            INSERT INTO transacoes (cliente_id, valor, tipo, descricao, realizada_em)
            SELECT :clienteId, :valor, :tipo, :descricao, CURRENT_TIMESTAMP
            FROM atualizado
        )
        SELECT
            CASE
                WHEN c.id IS NULL THEN 'CLIENTE_NAO_ENCONTRADO'
                WHEN a.valor IS NULL THEN 'SALDO_INSUFICIENTE'
                ELSE 'SUCESSO'
            END AS situacao,
            a.valor AS saldo,
            c.limite AS limite
        FROM (SELECT 1) base
        LEFT JOIN clientes c ON c.id = :clienteId
        LEFT JOIN atualizado a ON TRUE
        """;

    private static final String EXTRATO_SQL = """
        SELECT s.valor AS saldo, c.limite AS limite,
               t.valor, t.tipo, t.descricao, t.realizada_em
        FROM clientes c
        JOIN saldos s ON s.cliente_id = c.id
        LEFT JOIN LATERAL (
            SELECT valor, tipo, descricao, realizada_em
            FROM transacoes
            WHERE cliente_id = c.id
            ORDER BY realizada_em DESC, id DESC
            LIMIT 10
        ) t ON TRUE
        WHERE c.id = :clienteId
        """;

    private static final String BUSCAR_SALDO_PARA_LOTE_SQL = """
        SELECT s.valor AS saldo, c.limite
        FROM clientes c
        JOIN saldos s ON s.cliente_id = c.id
        WHERE c.id = :clienteId
        FOR UPDATE OF s
        """;

    private static final String ATUALIZAR_SALDO_SQL = """
        UPDATE saldos
        SET valor = :saldo
        WHERE cliente_id = :clienteId
        """;

    private static final String INSERIR_TRANSACAO_SQL = """
        INSERT INTO transacoes (cliente_id, valor, tipo, descricao, realizada_em)
        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    JdbcRinhaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transactionTemplate = new TransactionTemplate(
            new DataSourceTransactionManager(jdbc.getJdbcTemplate().getDataSource()));
    }

    @Override
    public ResultadoTransacao registrarTransacao(int clienteId, int valor, String tipo, String descricao) {
        return jdbc.queryForObject(REGISTRAR_TRANSACAO_SQL, parametros(clienteId, valor, tipo, descricao),
            (rs, rowNum) -> new ResultadoTransacao(
                SituacaoTransacao.valueOf(rs.getString("situacao")),
                rs.getObject("saldo", Integer.class),
                rs.getObject("limite", Integer.class)));
    }

    @Override
    public List<ResultadoTransacao> registrarLote(int clienteId, List<TransacaoPendente> transacoes) {
        return transactionTemplate.execute(status -> registrarLoteNaTransacao(clienteId, transacoes));
    }

    @Override
    public Optional<Extrato> buscarExtrato(int clienteId) {
        List<ExtratoRow> linhas = jdbc.query(EXTRATO_SQL, parametros(clienteId), (rs, rowNum) ->
            new ExtratoRow(
                rs.getInt("saldo"),
                rs.getInt("limite"),
                rs.getObject("valor", Integer.class),
                rs.getString("tipo"),
                rs.getString("descricao"),
                rs.getObject("realizada_em", OffsetDateTime.class)));

        if (linhas.isEmpty()) {
            return Optional.empty();
        }

        ExtratoRow primeiraLinha = linhas.getFirst();
        List<Transacao> transacoes = linhas.stream()
            .filter(linha -> linha.valor() != null)
            .map(linha -> new Transacao(
                linha.valor(), linha.tipo(), linha.descricao(), linha.realizadaEm().toInstant()))
            .toList();
        return Optional.of(new Extrato(primeiraLinha.saldo(), primeiraLinha.limite(), transacoes));
    }

    private MapSqlParameterSource parametros(int clienteId) {
        return new MapSqlParameterSource("clienteId", clienteId);
    }

    private MapSqlParameterSource parametros(int clienteId, int valor, String tipo, String descricao) {
        return parametros(clienteId)
            .addValue("valor", valor)
            .addValue("tipo", tipo)
            .addValue("descricao", descricao);
    }

    private List<ResultadoTransacao> registrarLoteNaTransacao(int clienteId, List<TransacaoPendente> transacoes) {
        Optional<EstadoSaldo> estado = jdbc.query(BUSCAR_SALDO_PARA_LOTE_SQL, parametros(clienteId),
                (rs, rowNum) -> new EstadoSaldo(rs.getInt("saldo"), rs.getInt("limite")))
            .stream()
            .findFirst();

        if (estado.isEmpty()) {
            return transacoes.stream().map(ignored -> ResultadoTransacao.clienteNaoEncontrado()).toList();
        }

        int saldo = estado.get().saldo();
        int limite = estado.get().limite();
        List<ResultadoTransacao> resultados = new ArrayList<>(transacoes.size());
        List<TransacaoPendente> aprovadas = new ArrayList<>(transacoes.size());

        for (TransacaoPendente transacao : transacoes) {
            if ("d".equals(transacao.tipo()) && saldo - transacao.valor() < -limite) {
                resultados.add(ResultadoTransacao.saldoInsuficiente());
                continue;
            }

            saldo += "c".equals(transacao.tipo()) ? transacao.valor() : -transacao.valor();
            aprovadas.add(transacao);
            resultados.add(new ResultadoTransacao(SituacaoTransacao.SUCESSO, saldo, limite));
        }

        if (aprovadas.isEmpty()) {
            return resultados;
        }

        jdbc.update(ATUALIZAR_SALDO_SQL, parametros(clienteId).addValue("saldo", saldo));
        jdbc.getJdbcTemplate().batchUpdate(INSERIR_TRANSACAO_SQL, aprovadas, aprovadas.size(),
            (statement, transacao) -> {
                statement.setInt(1, clienteId);
                statement.setInt(2, transacao.valor());
                statement.setString(3, transacao.tipo());
                statement.setString(4, transacao.descricao());
            });
        return resultados;
    }

    private record ExtratoRow(
        int saldo,
        int limite,
        Integer valor,
        String tipo,
        String descricao,
        OffsetDateTime realizadaEm) {
    }

    private record EstadoSaldo(int saldo, int limite) {
    }
}
