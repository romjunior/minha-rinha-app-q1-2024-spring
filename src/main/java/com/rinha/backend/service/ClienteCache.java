package com.rinha.backend.service;

import com.rinha.backend.repository.Cliente;
import com.rinha.backend.repository.ClienteRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ClienteCache {

    private final ClienteRepository clientes;

    public ClienteCache(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @Cacheable(cacheNames = "clientes", key = "#clienteId")
    public Cliente buscarObrigatorio(Integer clienteId) {
        return clientes.findById(clienteId)
            .orElseThrow(TransacaoService.ClienteNaoEncontradoException::new);
    }
}
