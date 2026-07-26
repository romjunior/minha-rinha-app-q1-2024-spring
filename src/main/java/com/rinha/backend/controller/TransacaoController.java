package com.rinha.backend.controller;

import com.rinha.backend.service.TransacaoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clientes")
public class TransacaoController {

    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @PostMapping("/{id}/transacoes")
    public TransacaoResponse criarTransacao(@PathVariable Integer id, @Valid @RequestBody TransacaoRequest request) {
        return transacaoService.processarTransacao(id, request);
    }

    @GetMapping("/{id}/extrato")
    public ExtratoResponse obterExtrato(@PathVariable Integer id) {
        return transacaoService.obterExtrato(id);
    }
}
