package com.rinha.backend.repository;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface SaldoRepository extends CrudRepository<Saldo, Integer> {

    Optional<Saldo> findByClienteId(Integer clienteId);
}
