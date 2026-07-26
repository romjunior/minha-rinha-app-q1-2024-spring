package com.rinha.backend.service;

import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.controller.TransacaoResponse;
import com.rinha.backend.repository.Cliente;
import com.rinha.backend.repository.Saldo;
import com.rinha.backend.repository.SaldoRepository;
import com.rinha.backend.repository.Transacao;
import com.rinha.backend.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TransacaoAttemptService {

    private final ClienteCache clientes;
    private final SaldoRepository saldos;
    private final TransacaoRepository transacoes;

    public TransacaoAttemptService(
        ClienteCache clientes,
        SaldoRepository saldos,
        TransacaoRepository transacoes) {
        this.clientes = clientes;
        this.saldos = saldos;
        this.transacoes = transacoes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransacaoResponse processar(Integer clienteId, TransacaoRequest request) {
        Cliente cliente = clientes.buscarObrigatorio(clienteId);
        Saldo saldo = saldos.findByClienteId(clienteId)
            .orElseThrow(() -> new IllegalStateException("Cliente sem saldo cadastrado: " + clienteId));

        int novoSaldo = calcularNovoSaldo(cliente, saldo, request);
        Saldo saldoAtualizado = saldos.save(saldo.comValor(novoSaldo));
        transacoes.save(new Transacao(
            null, clienteId, request.valor(), request.tipo(), request.descricao(), Instant.now()));

        return new TransacaoResponse(cliente.limite(), saldoAtualizado.valor());
    }

    private int calcularNovoSaldo(Cliente cliente, Saldo saldo, TransacaoRequest request) {
        if (request.tipo().equals("c")) {
            return Math.addExact(saldo.valor(), request.valor());
        }

        int novoSaldo = Math.subtractExact(saldo.valor(), request.valor());
        if (novoSaldo < -cliente.limite()) {
            throw new TransacaoService.SaldoInsuficienteException();
        }
        return novoSaldo;
    }
}
