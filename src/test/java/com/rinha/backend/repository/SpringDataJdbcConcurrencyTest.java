package com.rinha.backend.repository;

import com.rinha.backend.MinhaRinhaAppQ12024Application;
import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MinhaRinhaAppQ12024Application.class)
@Testcontainers(disabledWithoutDocker = true)
@DisabledInAotMode
class SpringDataJdbcConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.11-bookworm");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransacaoService service;

    @DynamicPropertySource
    static void configuraDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void preparaClienteComLimiteDeCemCentavos() {
        jdbc.execute("DROP TABLE IF EXISTS transacoes, saldos, clientes CASCADE");
        jdbc.execute("""
            CREATE TABLE clientes (
                id INTEGER PRIMARY KEY,
                nome VARCHAR(50) NOT NULL,
                limite INTEGER NOT NULL
            )
            """);
        jdbc.execute("""
            CREATE TABLE saldos (
                id SERIAL PRIMARY KEY,
                cliente_id INTEGER NOT NULL UNIQUE REFERENCES clientes(id),
                valor INTEGER NOT NULL,
                version BIGINT NOT NULL DEFAULT 0
            )
            """);
        jdbc.execute("""
            CREATE TABLE transacoes (
                id SERIAL PRIMARY KEY,
                cliente_id INTEGER NOT NULL REFERENCES clientes(id),
                valor INTEGER NOT NULL,
                tipo CHAR(1) NOT NULL,
                descricao VARCHAR(10) NOT NULL,
                realizada_em TIMESTAMP WITH TIME ZONE NOT NULL
            )
            """);
        jdbc.update("INSERT INTO clientes (id, nome, limite) VALUES (1, 'cliente', 100)");
        jdbc.update("INSERT INTO saldos (cliente_id, valor) VALUES (1, 0)");
    }

    @Test
    void nuncaAprovaDebitosAlemDoLimiteQuandoRequisicoesConcorrem() throws Exception {
        int requisicoes = 200;
        int concorrencia = 32;
        CountDownLatch prontos = new CountDownLatch(concorrencia);
        CountDownLatch iniciar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concorrencia);

        try {
            List<Future<Situacao>> futuros = new ArrayList<>();
            for (int i = 0; i < requisicoes; i++) {
                futuros.add(executor.submit(() -> {
                    prontos.countDown();
                    assertThat(iniciar.await(10, TimeUnit.SECONDS)).isTrue();
                    try {
                        service.processarTransacao(1, new TransacaoRequest(1, "d", "teste")).join();
                        return Situacao.SUCESSO;
                    } catch (java.util.concurrent.CompletionException exception) {
                        if (!(exception.getCause() instanceof TransacaoService.SaldoInsuficienteException)) {
                            throw exception;
                        }
                        return Situacao.SALDO_INSUFICIENTE;
                    }
                }));
            }

            assertThat(prontos.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();

            List<Situacao> resultados = new ArrayList<>();
            for (Future<Situacao> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }

            assertThat(resultados).containsOnly(Situacao.SUCESSO, Situacao.SALDO_INSUFICIENTE);
            assertThat(resultados).filteredOn(Situacao.SUCESSO::equals).hasSize(100);
            assertThat(jdbc.queryForObject("SELECT valor FROM saldos WHERE cliente_id = 1", Integer.class))
                .isEqualTo(-100);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transacoes WHERE cliente_id = 1", Integer.class))
                .isEqualTo(100);
        } finally {
            executor.shutdownNow();
        }
    }

    private enum Situacao {
        SUCESSO,
        SALDO_INSUFICIENTE
    }
}
