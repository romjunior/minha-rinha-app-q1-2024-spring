package com.rinha.backend.service;

import com.rinha.backend.controller.TransacaoResponse;
import com.rinha.backend.repository.Saldo;
import com.rinha.backend.repository.SaldoRepository;
import com.rinha.backend.repository.Transacao;
import com.rinha.backend.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransacaoBatchAttemptService {
    private final SaldoRepository saldos;
    private final TransacaoRepository transacoes;

    public TransacaoBatchAttemptService(SaldoRepository saldos, TransacaoRepository transacoes) {
        this.saldos = saldos;
        this.transacoes = transacoes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ResultadoLote> processar(List<TransacaoPendente> pendentes) {
        TransacaoPendente primeira = pendentes.getFirst();
        Saldo saldo = saldos.findByClienteId(primeira.cliente().id()).orElseThrow();
        int saldoAtual = saldo.valor();
        List<Transacao> aceitas = new ArrayList<>();
        List<ResultadoLote> resultados = new ArrayList<>(pendentes.size());
        for (TransacaoPendente pendente : pendentes) {
            int proximo = pendente.request().tipo().equals("c")
                ? Math.addExact(saldoAtual, pendente.request().valor())
                : Math.subtractExact(saldoAtual, pendente.request().valor());
            if (proximo < -pendente.cliente().limite()) {
                resultados.add(new ResultadoLote(pendente, null));
                continue;
            }
            saldoAtual = proximo;
            aceitas.add(new Transacao(null, pendente.cliente().id(), pendente.request().valor(), pendente.request().tipo(), pendente.request().descricao(), Instant.now()));
            resultados.add(new ResultadoLote(pendente, new TransacaoResponse(pendente.cliente().limite(), saldoAtual)));
        }
        if (!aceitas.isEmpty()) {
            saldos.save(saldo.comValor(saldoAtual));
            transacoes.saveAll(aceitas);
        }
        return resultados;
    }

    record ResultadoLote(TransacaoPendente pendente, TransacaoResponse resposta) {
        boolean aprovada() { return resposta != null; }
    }
}
