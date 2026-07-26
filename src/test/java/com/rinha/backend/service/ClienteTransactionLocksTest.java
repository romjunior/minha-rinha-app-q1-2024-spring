package com.rinha.backend.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteTransactionLocksTest {

    @Test
    void serializaOperacoesDoMesmoCliente() throws Exception {
        ClienteTransactionLocks locks = new ClienteTransactionLocks();
        CountDownLatch primeiraEntrou = new CountDownLatch(1);
        CountDownLatch liberarPrimeira = new CountDownLatch(1);
        CountDownLatch segundaEntrou = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> primeira = executor.submit(() -> locks.executar(1, () -> {
                primeiraEntrou.countDown();
                await(liberarPrimeira);
                return null;
            }));
            assertThat(primeiraEntrou.await(1, TimeUnit.SECONDS)).isTrue();

            Future<?> segunda = executor.submit(() -> locks.executar(1, () -> {
                segundaEntrou.countDown();
                return null;
            }));
            assertThat(segundaEntrou.await(100, TimeUnit.MILLISECONDS)).isFalse();

            liberarPrimeira.countDown();
            primeira.get(1, TimeUnit.SECONDS);
            segunda.get(1, TimeUnit.SECONDS);
            assertThat(segundaEntrou.getCount()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("Tempo esgotado aguardando a operação");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Thread interrompida", exception);
        }
    }
}
