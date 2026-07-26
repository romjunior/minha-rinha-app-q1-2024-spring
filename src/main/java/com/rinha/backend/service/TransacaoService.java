package com.rinha.backend.service;

import com.rinha.backend.controller.ExtratoResponse;
import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.controller.TransacaoResponse;
import com.rinha.backend.repository.RinhaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TransacaoService {

    private final RinhaRepository repository;

    public TransacaoService(RinhaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TransacaoResponse processarTransacao(Integer clienteId, TransacaoRequest request) {
        RinhaRepository.ResultadoTransacao transacao = repository.registrarTransacao(
            clienteId, request.valor(), request.tipo(), request.descricao());

        if (transacao.situacao() == RinhaRepository.SituacaoTransacao.CLIENTE_NAO_ENCONTRADO) {
            throw new ClienteNaoEncontradoException();
        }
        if (transacao.situacao() == RinhaRepository.SituacaoTransacao.SALDO_INSUFICIENTE) {
            throw new SaldoInsuficienteException();
        }
        return new TransacaoResponse(transacao.limite(), transacao.saldo());
    }

    @Transactional(readOnly = true)
    public ExtratoResponse obterExtrato(Integer clienteId) {
        RinhaRepository.Extrato extrato = repository.buscarExtrato(clienteId)
            .orElseThrow(ClienteNaoEncontradoException::new);

        var transacoesExtrato = extrato.transacoes().stream()
            .map(t -> new ExtratoResponse.TransacaoExtrato(t.valor(), t.tipo(), t.descricao(), t.realizadaEm()))
            .toList();

        ExtratoResponse.SaldoExtrato saldoExtrato = new ExtratoResponse.SaldoExtrato(
            extrato.saldo(), Instant.now(), extrato.limite());

        return new ExtratoResponse(saldoExtrato, transacoesExtrato);
    }

    public static class ClienteNaoEncontradoException extends RuntimeException {}
    public static class SaldoInsuficienteException extends RuntimeException {}
}
