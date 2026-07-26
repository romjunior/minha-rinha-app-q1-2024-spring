package com.rinha.backend.repository;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TransacaoRepository extends CrudRepository<Transacao, Integer> {

    List<Transacao> findTop10ByClienteIdOrderByRealizadaEmDescIdDesc(Integer clienteId);
}
