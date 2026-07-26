package com.rinha.backend.service;

import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.controller.TransacaoResponse;
import com.rinha.backend.repository.Cliente;
import com.rinha.backend.repository.Saldo;
import com.rinha.backend.repository.SaldoRepository;
import com.rinha.backend.repository.TransacaoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransacaoBatchAttemptServiceTest {
    private final SaldoRepository saldos = mock(SaldoRepository.class);
    private final TransacaoRepository transacoes = mock(TransacaoRepository.class);
    private final TransacaoBatchAttemptService service = new TransacaoBatchAttemptService(saldos, transacoes);

    @Test
    void gravaUmSaldoParaMultiplasTransacoesDoMesmoCliente() {
        Cliente cliente = new Cliente(1, "cliente", 100);
        when(saldos.findByClienteId(1)).thenReturn(Optional.of(new Saldo(1, 1, 0, 0L)));
        when(saldos.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        List<TransacaoPendente> lote = List.of(
            pendente(cliente, new TransacaoRequest(50, "c", "pix")),
            pendente(cliente, new TransacaoRequest(25, "d", "pix")),
            pendente(cliente, new TransacaoRequest(200, "d", "pix")));

        List<TransacaoBatchAttemptService.ResultadoLote> resultados = service.processar(lote);

        assertThat(resultados).extracting(TransacaoBatchAttemptService.ResultadoLote::aprovada)
            .containsExactly(true, true, false);
        assertThat(resultados.get(1).resposta()).isEqualTo(new TransacaoResponse(100, 25));
        verify(saldos, times(1)).save(new Saldo(1, 1, 25, 0L));
        verify(transacoes, times(1)).saveAll(any());
    }

    private TransacaoPendente pendente(Cliente cliente, TransacaoRequest request) {
        return new TransacaoPendente(cliente, request, new CompletableFuture<>());
    }
}
