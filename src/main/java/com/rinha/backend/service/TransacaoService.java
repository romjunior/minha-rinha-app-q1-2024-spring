package com.rinha.backend.service;

import com.rinha.backend.controller.ExtratoResponse;
import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.controller.TransacaoResponse;
import com.rinha.backend.repository.Cliente;
import com.rinha.backend.repository.Saldo;
import com.rinha.backend.repository.SaldoRepository;
import com.rinha.backend.repository.TransacaoRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TransacaoService {

    private final TransacaoAttemptService tentativaService;
    private final ClienteCache clientes;
    private final SaldoRepository saldos;
    private final TransacaoRepository transacoes;
    private final TransacaoRetryProperties retryProperties;
    private final ClienteTransactionLocks locks;

    public TransacaoService(
        TransacaoAttemptService tentativaService,
        ClienteCache clientes,
        SaldoRepository saldos,
        TransacaoRepository transacoes,
        TransacaoRetryProperties retryProperties,
        ClienteTransactionLocks locks) {
        this.tentativaService = tentativaService;
        this.clientes = clientes;
        this.saldos = saldos;
        this.transacoes = transacoes;
        this.retryProperties = retryProperties;
        this.locks = locks;
    }

    public TransacaoResponse processarTransacao(Integer clienteId, TransacaoRequest request) {
        return locks.executar(clienteId, () -> processarComRetry(clienteId, request));
    }

    private TransacaoResponse processarComRetry(Integer clienteId, TransacaoRequest request) {
        for (int tentativa = 1; tentativa <= retryProperties.getMaxTentativas(); tentativa++) {
            try {
                return tentativaService.processar(clienteId, request);
            } catch (OptimisticLockingFailureException exception) {
                if (tentativa == retryProperties.getMaxTentativas()) {
                    throw new ConflitoConcorrenciaException(exception);
                }
                aguardarAntesDaProximaTentativa();
            }
        }
        throw new ConflitoConcorrenciaException();
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

    private void aguardarAntesDaProximaTentativa() {
        int esperaMinima = retryProperties.getEsperaMinMillis();
        int esperaMaxima = retryProperties.getEsperaMaxMillis();
        int espera = ThreadLocalRandom.current().nextInt(esperaMinima, esperaMaxima + 1);
        try {
            Thread.sleep(espera);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConflitoConcorrenciaException(exception);
        }
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
