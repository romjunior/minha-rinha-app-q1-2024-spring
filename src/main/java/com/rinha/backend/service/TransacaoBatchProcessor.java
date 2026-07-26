package com.rinha.backend.service;

import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.controller.TransacaoResponse;
import com.rinha.backend.repository.Cliente;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
public class TransacaoBatchProcessor {
    private static final int QUANTIDADE_DE_CLIENTES = 5;
    private final ClienteCache clientes;
    private final TransacaoBatchAttemptService tentativaService;
    private final TransacaoRetryProperties retryProperties;
    private final TransacaoBatchProperties batchProperties;
    private final List<BlockingQueue<TransacaoPendente>> filas = new ArrayList<>(QUANTIDADE_DE_CLIENTES);
    private ExecutorService workers;

    public TransacaoBatchProcessor(ClienteCache clientes, TransacaoBatchAttemptService tentativaService,
                                   TransacaoRetryProperties retryProperties, TransacaoBatchProperties batchProperties) {
        this.clientes = clientes;
        this.tentativaService = tentativaService;
        this.retryProperties = retryProperties;
        this.batchProperties = batchProperties;
    }

    @PostConstruct
    void iniciarWorkers() {
        for (int indice = 0; indice < QUANTIDADE_DE_CLIENTES; indice++) {
            filas.add(new ArrayBlockingQueue<>(batchProperties.getCapacidadeFilaPorCliente()));
        }
        workers = Executors.newFixedThreadPool(QUANTIDADE_DE_CLIENTES, Thread.ofPlatform().name("transacao-lote-", 0).factory());
        filas.forEach(fila -> workers.submit(() -> consumir(fila)));
    }

    public CompletableFuture<TransacaoResponse> submeter(Integer clienteId, TransacaoRequest request) {
        Cliente cliente = clientes.buscarObrigatorio(clienteId);
        CompletableFuture<TransacaoResponse> resposta = new CompletableFuture<>();
        if (!filas.get(Math.floorMod(clienteId - 1, QUANTIDADE_DE_CLIENTES)).offer(new TransacaoPendente(cliente, request, resposta))) {
            resposta.completeExceptionally(new TransacaoService.ConflitoConcorrenciaException());
        }
        return resposta;
    }

    private void consumir(BlockingQueue<TransacaoPendente> fila) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                List<TransacaoPendente> lote = coletarLote(fila, fila.take());
                processar(lote);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private List<TransacaoPendente> coletarLote(BlockingQueue<TransacaoPendente> fila, TransacaoPendente primeira) throws InterruptedException {
        List<TransacaoPendente> lote = new ArrayList<>(batchProperties.getTamanhoMaximo());
        lote.add(primeira);
        long prazo = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(batchProperties.getJanelaColetaMillis());
        while (lote.size() < batchProperties.getTamanhoMaximo()) {
            long restante = prazo - System.nanoTime();
            if (restante <= 0) break;
            TransacaoPendente proxima = fila.poll(restante, TimeUnit.NANOSECONDS);
            if (proxima == null) break;
            lote.add(proxima);
        }
        return lote;
    }

    private void processar(List<TransacaoPendente> lote) {
        for (int tentativa = 1; tentativa <= retryProperties.getMaxTentativas(); tentativa++) {
            try {
                completar(tentativaService.processar(lote));
                return;
            } catch (OptimisticLockingFailureException exception) {
                if (tentativa == retryProperties.getMaxTentativas()) {
                    completarComErro(lote, new TransacaoService.ConflitoConcorrenciaException(exception));
                    return;
                }
                aguardar();
            } catch (RuntimeException exception) {
                completarComErro(lote, exception);
                return;
            }
        }
    }

    private void completar(List<TransacaoBatchAttemptService.ResultadoLote> resultados) {
        resultados.forEach(resultado -> {
            if (resultado.aprovada()) resultado.pendente().resposta().complete(resultado.resposta());
            else resultado.pendente().resposta().completeExceptionally(new TransacaoService.SaldoInsuficienteException());
        });
    }
    private void completarComErro(List<TransacaoPendente> lote, RuntimeException exception) {
        lote.forEach(pendente -> pendente.resposta().completeExceptionally(exception));
    }
    private void aguardar() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(retryProperties.getEsperaMinMillis(), retryProperties.getEsperaMaxMillis() + 1));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TransacaoService.ConflitoConcorrenciaException(exception);
        }
    }
    @PreDestroy
    void encerrarWorkers() { workers.shutdownNow(); }
}
