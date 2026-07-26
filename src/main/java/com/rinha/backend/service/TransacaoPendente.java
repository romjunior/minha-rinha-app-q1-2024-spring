package com.rinha.backend.service;

import com.rinha.backend.controller.TransacaoRequest;
import com.rinha.backend.controller.TransacaoResponse;
import com.rinha.backend.repository.Cliente;

import java.util.concurrent.CompletableFuture;

record TransacaoPendente(Cliente cliente, TransacaoRequest request, CompletableFuture<TransacaoResponse> resposta) {
}
