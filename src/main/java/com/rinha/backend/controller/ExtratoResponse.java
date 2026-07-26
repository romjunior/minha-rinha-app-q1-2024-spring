package com.rinha.backend.controller;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record ExtratoResponse(
    SaldoExtrato saldo,
    @JsonProperty("ultimas_transacoes") List<TransacaoExtrato> ultimasTransacoes) {

    public record SaldoExtrato(
        int total,
        @JsonProperty("data_extrato") Instant dataExtrato,
        int limite) {
    }

    public record TransacaoExtrato(
        int valor,
        String tipo,
        String descricao,
        @JsonProperty("realizada_em") Instant realizadaEm) {
    }
}
