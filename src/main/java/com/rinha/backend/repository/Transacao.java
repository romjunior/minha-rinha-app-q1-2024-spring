package com.rinha.backend.repository;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("transacoes")
public record Transacao(
    @Id Integer id,
    @Column("cliente_id") Integer clienteId,
    int valor,
    String tipo,
    String descricao,
    @Column("realizada_em") Instant realizadaEm) {
}
