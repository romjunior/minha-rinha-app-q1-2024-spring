package com.rinha.backend.service;

import com.rinha.backend.controller.ExtratoResponse;
import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.repository.Cliente;
import com.rinha.backend.repository.Saldo;
import com.rinha.backend.repository.SaldoRepository;
import com.rinha.backend.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Service
public class TransacaoService {

    private final TransacaoBatchProcessor processador;
    private final ClienteCache clientes;
    private final SaldoRepository saldos;
    private final TransacaoRepository transacoes;

    public TransacaoService(
        TransacaoBatchProcessor processador,
        ClienteCache clientes,
        SaldoRepository saldos,
        TransacaoRepository transacoes) {
        this.processador = processador;
        this.clientes = clientes;
        this.saldos = saldos;
        this.transacoes = transacoes;
    }

    public CompletableFuture<com.rinha.backend.controller.TransacaoResponse> processarTransacao(
        Integer clienteId,
        TransacaoRequest request) {
        return processador.submeter(clienteId, request);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExtratoResponse obterExtrato(Integer clienteId) {
        Cliente cliente = clientes.buscarObrigatorio(clienteId);
        Saldo saldo = saldos.findByClienteId(clienteId)
            .orElseThrow(() -> new IllegalStateException("Cliente sem saldo cadastrado: " + clienteId));

        var transacoesExtrato = transacoes.findTop10ByClienteIdOrderByRealizadaEmDescIdDesc(clienteId).stream()
            .map(t -> new ExtratoResponse.TransacaoExtrato(t.valor(), t.tipo(), t.descricao(), t.realizadaEm()))
            .toList();

        ExtratoResponse.SaldoExtrato saldoExtrato = new ExtratoResponse.SaldoExtrato(
            saldo.valor(), Instant.now(), cliente.limite());

        return new ExtratoResponse(saldoExtrato, transacoesExtrato);
    }

    public static class ClienteNaoEncontradoException extends RuntimeException {}
    public static class SaldoInsuficienteException extends RuntimeException {}
    public static class ConflitoConcorrenciaException extends RuntimeException {
        public ConflitoConcorrenciaException() {
        }

        public ConflitoConcorrenciaException(Throwable cause) {
            super(cause);
        }
    }
}
