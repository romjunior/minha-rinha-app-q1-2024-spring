package com.rinha.backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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

@Testcontainers(disabledWithoutDocker = true)
class JdbcRinhaRepositoryConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.11-bookworm");

    private JdbcTemplate jdbc;
    private RinhaRepository repository;

    @BeforeEach
    void preparaClienteComLimiteDeCemCentavos() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcRinhaRepository(new NamedParameterJdbcTemplate(dataSource));
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS clientes (
                id INTEGER PRIMARY KEY,
                nome VARCHAR(50) NOT NULL,
                limite INTEGER NOT NULL
            )
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS saldos (
                id SERIAL PRIMARY KEY,
                cliente_id INTEGER NOT NULL UNIQUE REFERENCES clientes(id),
                valor INTEGER NOT NULL
            )
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS transacoes (
                id SERIAL PRIMARY KEY,
                cliente_id INTEGER NOT NULL REFERENCES clientes(id),
                valor INTEGER NOT NULL,
                tipo CHAR(1) NOT NULL,
                descricao VARCHAR(10) NOT NULL,
                realizada_em TIMESTAMP WITH TIME ZONE NOT NULL
            )
            """);
        jdbc.execute("TRUNCATE transacoes, saldos, clientes RESTART IDENTITY CASCADE");
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
            List<Future<RinhaRepository.ResultadoTransacao>> futuros = new ArrayList<>();
            for (int i = 0; i < requisicoes; i++) {
                futuros.add(executor.submit(() -> {
                    prontos.countDown();
                    assertThat(iniciar.await(10, TimeUnit.SECONDS)).isTrue();
                    return repository.registrarTransacao(1, 1, "d", "teste");
                }));
            }

            assertThat(prontos.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();

            List<RinhaRepository.ResultadoTransacao> resultados = new ArrayList<>();
            for (Future<RinhaRepository.ResultadoTransacao> futuro : futuros) {
                resultados.add(futuro.get(15, TimeUnit.SECONDS));
            }

            assertThat(resultados)
                .extracting(RinhaRepository.ResultadoTransacao::situacao)
                .containsOnly(RinhaRepository.SituacaoTransacao.SUCESSO,
                    RinhaRepository.SituacaoTransacao.SALDO_INSUFICIENTE);
            assertThat(resultados)
                .extracting(RinhaRepository.ResultadoTransacao::situacao)
                .filteredOn(RinhaRepository.SituacaoTransacao.SUCESSO::equals)
                .hasSize(100);
            assertThat(jdbc.queryForObject("SELECT valor FROM saldos WHERE cliente_id = 1", Integer.class))
                .isEqualTo(-100);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transacoes WHERE cliente_id = 1", Integer.class))
                .isEqualTo(100);
        } finally {
            executor.shutdownNow();
        }
    }
}
