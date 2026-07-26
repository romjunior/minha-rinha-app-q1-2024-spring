package com.rinha.backend.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class ClienteTransactionLocks {

    private static final int QUANTIDADE_DE_STRIPES = 16;

    private final ReentrantLock[] locks = new ReentrantLock[QUANTIDADE_DE_STRIPES];

    public ClienteTransactionLocks() {
        for (int indice = 0; indice < QUANTIDADE_DE_STRIPES; indice++) {
            locks[indice] = new ReentrantLock();
        }
    }

    public <T> T executar(Integer clienteId, Supplier<T> operacao) {
        ReentrantLock lock = locks[Math.floorMod(clienteId, QUANTIDADE_DE_STRIPES)];
        lock.lock();
        try {
            return operacao.get();
        } finally {
            lock.unlock();
        }
    }
}
