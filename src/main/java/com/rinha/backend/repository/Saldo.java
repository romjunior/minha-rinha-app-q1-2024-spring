package com.rinha.backend.repository;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("saldos")
public record Saldo(
    @Id Integer id,
    @Column("cliente_id") Integer clienteId,
    int valor,
    @Version Long version) {

    public Saldo comValor(int novoValor) {
        return new Saldo(id, clienteId, novoValor, version);
    }
}
