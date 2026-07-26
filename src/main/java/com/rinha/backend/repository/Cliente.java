package com.rinha.backend.repository;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("clientes")
public record Cliente(
    @Id Integer id,
    String nome,
    int limite) {
}
