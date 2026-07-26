package com.rinha.backend.service;

import com.rinha.backend.controller.ExtratoResponse;
import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.controller.TransacaoResponse;
import com.rinha.backend.repository.Cliente;
import com.rinha.backend.repository.Saldo;
import com.rinha.backend.repository.SaldoRepository;
import com.rinha.backend.repository.Transacao;
import com.rinha.backend.repository.TransacaoRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransacaoServiceTest {
    private final TransacaoBatchProcessor processador = mock(TransacaoBatchProcessor.class);
    private final ClienteCache clientes = mock(ClienteCache.class);
    private final SaldoRepository saldos = mock(SaldoRepository.class);
    private final TransacaoRepository transacoes = mock(TransacaoRepository.class);
    private final TransacaoService service = new TransacaoService(processador, clientes, saldos, transacoes);

    @Test
    void delegaTransacaoParaProcessadorAssincrono() {
        TransacaoResponse esperada = new TransacaoResponse(1_000, 100);
        when(processador.submeter(any(), any())).thenReturn(CompletableFuture.completedFuture(esperada));

        assertThat(service.processarTransacao(1, new TransacaoRequest(100, "c", "pix")).join())
            .isEqualTo(esperada);
    }

    @Test
    void montaExtratoComAsEntidadesDosRepositories() {
        Instant realizadaEm = Instant.parse("2024-01-17T02:34:38Z");
        when(clientes.buscarObrigatorio(1)).thenReturn(new Cliente(1, "cliente", 1_000));
        when(saldos.findByClienteId(1)).thenReturn(Optional.of(new Saldo(1, 1, -100, 0L)));
        when(transacoes.findTop10ByClienteIdOrderByRealizadaEmDescIdDesc(1)).thenReturn(List.of(
            new Transacao(1, 1, 100, "d", "pix", realizadaEm)));

        ExtratoResponse resposta = service.obterExtrato(1);

        assertThat(resposta.saldo().total()).isEqualTo(-100);
        assertThat(resposta.saldo().limite()).isEqualTo(1_000);
        assertThat(resposta.ultimasTransacoes()).containsExactly(
            new ExtratoResponse.TransacaoExtrato(100, "d", "pix", realizadaEm));
    }
}
