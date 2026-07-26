package com.rinha.backend.service;

import com.rinha.backend.repository.RinhaRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coalesces pending requests from one client into a short database transaction.
 * Nginx routes a client consistently to one API instance, but the database batch
 * itself still locks the balance row, so correctness does not depend on routing.
 */
@Component
class BatchingTransacaoProcessor {

    private static final int MAX_TRANSACOES_POR_LOTE = 32;

    private final RinhaRepository repository;
    private final ConcurrentHashMap<Integer, FilaCliente> filas = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(5, new BatchThreadFactory());

    BatchingTransacaoProcessor(RinhaRepository repository) {
        this.repository = repository;
    }

    RinhaRepository.ResultadoTransacao processar(int clienteId, int valor, String tipo, String descricao) {
        FilaCliente fila = filas.computeIfAbsent(clienteId, ignored -> new FilaCliente());
        Pendente pendente = new Pendente(new RinhaRepository.TransacaoPendente(valor, tipo, descricao));
        fila.pendentes.add(pendente);
        agendar(fila, clienteId);

        try {
            return pendente.resultado.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido enquanto aguardava a transação", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Falha ao processar lote de transações", exception.getCause());
        }
    }

    @PreDestroy
    void encerrar() {
        workers.shutdown();
    }

    private void agendar(FilaCliente fila, int clienteId) {
        if (fila.processando.compareAndSet(false, true)) {
            workers.execute(() -> drenar(fila, clienteId));
        }
    }

    private void drenar(FilaCliente fila, int clienteId) {
        try {
            while (true) {
                List<Pendente> lote = retirarLote(fila);
                if (lote.isEmpty()) {
                    return;
                }

                try {
                    List<RinhaRepository.ResultadoTransacao> resultados = repository.registrarLote(clienteId,
                        lote.stream().map(Pendente::transacao).toList());
                    if (resultados.size() != lote.size()) {
                        throw new IllegalStateException("O resultado do lote não corresponde às transações enviadas");
                    }
                    for (int index = 0; index < lote.size(); index++) {
                        lote.get(index).resultado.complete(resultados.get(index));
                    }
                } catch (RuntimeException exception) {
                    lote.forEach(pendente -> pendente.resultado.completeExceptionally(exception));
                }
            }
        } finally {
            fila.processando.set(false);
            if (!fila.pendentes.isEmpty()) {
                agendar(fila, clienteId);
            }
        }
    }

    private List<Pendente> retirarLote(FilaCliente fila) {
        List<Pendente> lote = new ArrayList<>(MAX_TRANSACOES_POR_LOTE);
        for (int index = 0; index < MAX_TRANSACOES_POR_LOTE; index++) {
            Pendente pendente = fila.pendentes.poll();
            if (pendente == null) {
                break;
            }
            lote.add(pendente);
        }
        return lote;
    }

    private static final class FilaCliente {
        private final ConcurrentLinkedQueue<Pendente> pendentes = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean processando = new AtomicBoolean();
    }

    private record Pendente(
        RinhaRepository.TransacaoPendente transacao,
        CompletableFuture<RinhaRepository.ResultadoTransacao> resultado) {

        private Pendente(RinhaRepository.TransacaoPendente transacao) {
            this(transacao, new CompletableFuture<>());
        }
    }

    private static final class BatchThreadFactory implements ThreadFactory {
        private final AtomicInteger sequencia = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "transacao-batch-" + sequencia.incrementAndGet());
        }
    }
}
