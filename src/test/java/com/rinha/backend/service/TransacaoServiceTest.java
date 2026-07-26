package com.rinha.backend.service;

import com.rinha.backend.controller.ExtratoResponse;
import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.repository.RinhaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransacaoServiceTest {

    private final RinhaRepository repository = mock(RinhaRepository.class);
    private final BatchingTransacaoProcessor transacaoProcessor = mock(BatchingTransacaoProcessor.class);
    private final TransacaoService service = new TransacaoService(repository, transacaoProcessor);

    @Test
    void retornaSaldoELimiteQuandoTransacaoERegistrada() {
        when(transacaoProcessor.processar(1, 100, "c", "pix"))
            .thenReturn(new RinhaRepository.ResultadoTransacao(
                RinhaRepository.SituacaoTransacao.SUCESSO, 100, 1_000));

        var resposta = service.processarTransacao(1, new TransacaoRequest(100, "c", "pix"));

        assertThat(resposta.saldo()).isEqualTo(100);
        assertThat(resposta.limite()).isEqualTo(1_000);
    }

    @Test
    void diferenciaClienteInexistenteDeSaldoInsuficiente() {
        when(transacaoProcessor.processar(6, 100, "d", "pix"))
            .thenReturn(RinhaRepository.ResultadoTransacao.clienteNaoEncontrado());

        assertThatThrownBy(() -> service.processarTransacao(6, new TransacaoRequest(100, "d", "pix")))
            .isInstanceOf(TransacaoService.ClienteNaoEncontradoException.class);
    }

    @Test
    void rejeitaDebitoQueExcedeOLimite() {
        when(transacaoProcessor.processar(1, 101, "d", "pix"))
            .thenReturn(RinhaRepository.ResultadoTransacao.saldoInsuficiente());

        assertThatThrownBy(() -> service.processarTransacao(1, new TransacaoRequest(101, "d", "pix")))
            .isInstanceOf(TransacaoService.SaldoInsuficienteException.class);
    }

    @Test
    void montaExtratoComAsTransacoesDoRepositorio() {
        Instant realizadaEm = Instant.parse("2024-01-17T02:34:38Z");
        when(repository.buscarExtrato(1)).thenReturn(Optional.of(new RinhaRepository.Extrato(
            -100, 1_000, List.of(new RinhaRepository.Transacao(100, "d", "pix", realizadaEm)))));

        ExtratoResponse resposta = service.obterExtrato(1);

        assertThat(resposta.saldo().total()).isEqualTo(-100);
        assertThat(resposta.saldo().limite()).isEqualTo(1_000);
        assertThat(resposta.ultimasTransacoes()).containsExactly(
            new ExtratoResponse.TransacaoExtrato(100, "d", "pix", realizadaEm));
    }
}
