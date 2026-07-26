package com.rinha.backend.service;

import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.repository.Cliente;
import com.rinha.backend.repository.Saldo;
import com.rinha.backend.repository.SaldoRepository;
import com.rinha.backend.repository.Transacao;
import com.rinha.backend.repository.TransacaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransacaoAttemptServiceTest {

    private final ClienteCache clientes = mock(ClienteCache.class);
    private final SaldoRepository saldos = mock(SaldoRepository.class);
    private final TransacaoRepository transacoes = mock(TransacaoRepository.class);
    private final TransacaoAttemptService service = new TransacaoAttemptService(clientes, saldos, transacoes);

    @Test
    void atualizaSaldoERegistraTransacaoNaMesmaTentativa() {
        when(clientes.buscarObrigatorio(1)).thenReturn(new Cliente(1, "cliente", 1_000));
        when(saldos.findByClienteId(1)).thenReturn(Optional.of(new Saldo(1, 1, 10, 0L)));
        when(saldos.save(new Saldo(1, 1, 110, 0L))).thenReturn(new Saldo(1, 1, 110, 1L));

        var resposta = service.processar(1, new TransacaoRequest(100, "c", "pix"));

        assertThat(resposta.saldo()).isEqualTo(110);
        ArgumentCaptor<Transacao> transacao = ArgumentCaptor.forClass(Transacao.class);
        verify(transacoes).save(transacao.capture());
        assertThat(transacao.getValue())
            .usingRecursiveComparison()
            .ignoringFields("realizadaEm")
            .isEqualTo(new Transacao(null, 1, 100, "c", "pix", null));
    }

    @Test
    void rejeitaDebitoQuandoSaldoResultanteExcedeOLimite() {
        when(clientes.buscarObrigatorio(1)).thenReturn(new Cliente(1, "cliente", 100));
        when(saldos.findByClienteId(1)).thenReturn(Optional.of(new Saldo(1, 1, 0, 0L)));

        assertThatThrownBy(() -> service.processar(1, new TransacaoRequest(101, "d", "pix")))
            .isInstanceOf(TransacaoService.SaldoInsuficienteException.class);

        verify(saldos, never()).save(any());
        verify(transacoes, never()).save(any());
    }

    @Test
    void diferenciaClienteInexistente() {
        when(clientes.buscarObrigatorio(6)).thenThrow(new TransacaoService.ClienteNaoEncontradoException());

        assertThatThrownBy(() -> service.processar(6, new TransacaoRequest(100, "d", "pix")))
            .isInstanceOf(TransacaoService.ClienteNaoEncontradoException.class);
    }
}
