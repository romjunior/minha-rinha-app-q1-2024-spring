package com.rinha.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port of the persistence operations required by the Rinha use cases.
 * It deliberately exposes projections, rather than generic CRUD aggregates.
 */
public interface RinhaRepository {

    ResultadoTransacao registrarTransacao(
        int clienteId, int valor, String tipo, String descricao);

    Optional<Extrato> buscarExtrato(int clienteId);

    record ResultadoTransacao(SituacaoTransacao situacao, Integer saldo, Integer limite) {
        public static ResultadoTransacao clienteNaoEncontrado() {
            return new ResultadoTransacao(SituacaoTransacao.CLIENTE_NAO_ENCONTRADO, null, null);
        }

        public static ResultadoTransacao saldoInsuficiente() {
            return new ResultadoTransacao(SituacaoTransacao.SALDO_INSUFICIENTE, null, null);
        }
    }

    enum SituacaoTransacao {
        SUCESSO,
        CLIENTE_NAO_ENCONTRADO,
        SALDO_INSUFICIENTE
    }

    record Extrato(int saldo, int limite, List<Transacao> transacoes) {
    }

    record Transacao(int valor, String tipo, String descricao, Instant realizadaEm) {
    }
}
