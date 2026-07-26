package com.rinha.backend.service;

import com.rinha.backend.controller.ExtratoResponse;
import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.controller.TransacaoResponse;
import com.rinha.backend.repository.Cliente;
import com.rinha.backend.repository.Saldo;
import com.rinha.backend.repository.SaldoRepository;
import com.rinha.backend.repository.Transacao;
import com.rinha.backend.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransacaoServiceTest {

    private final TransacaoAttemptService tentativaService = mock(TransacaoAttemptService.class);
    private final ClienteCache clientes = mock(ClienteCache.class);
    private final SaldoRepository saldos = mock(SaldoRepository.class);
    private final TransacaoRepository transacoes = mock(TransacaoRepository.class);
    private final TransacaoRetryProperties retryProperties = new TransacaoRetryProperties();
    private TransacaoService service;

    @BeforeEach
    void setUp() {
        retryProperties.setEsperaMinMillis(0);
        retryProperties.setEsperaMaxMillis(0);
        service = new TransacaoService(
            tentativaService, clientes, saldos, transacoes, retryProperties, new ClienteTransactionLocks());
    }

    @Test
    void retornaSaldoELimiteQuandoTransacaoERegistrada() {
        when(tentativaService.processar(1, new TransacaoRequest(100, "c", "pix")))
            .thenReturn(new TransacaoResponse(1_000, 100));

        var resposta = service.processarTransacao(1, new TransacaoRequest(100, "c", "pix"));

        assertThat(resposta.saldo()).isEqualTo(100);
        assertThat(resposta.limite()).isEqualTo(1_000);
    }

    @Test
    void repeteUmaTentativaQuandoOtimisticLockEntraEmConflito() {
        TransacaoRequest request = new TransacaoRequest(100, "c", "pix");
        when(tentativaService.processar(1, request))
            .thenThrow(new OptimisticLockingFailureException("conflito"))
            .thenReturn(new TransacaoResponse(1_000, 100));

        TransacaoResponse resposta = service.processarTransacao(1, request);

        assertThat(resposta).isEqualTo(new TransacaoResponse(1_000, 100));
        verify(tentativaService, times(2)).processar(1, request);
    }

    @Test
    void retornaConflitoControladoQuandoAsTentativasSeEsgotam() {
        retryProperties.setMaxTentativas(1);
        when(tentativaService.processar(eq(1), any()))
            .thenThrow(new OptimisticLockingFailureException("conflito"));

        assertThatThrownBy(() -> service.processarTransacao(1, new TransacaoRequest(100, "c", "pix")))
            .isInstanceOf(TransacaoService.ConflitoConcorrenciaException.class);
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
