package com.rinha.backend.controller;

import com.rinha.backend.service.TransacaoService.ClienteNaoEncontradoException;
import com.rinha.backend.service.TransacaoService.SaldoInsuficienteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ClienteNaoEncontradoException.class)
    ResponseEntity<Void> clienteNaoEncontrado() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler({SaldoInsuficienteException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Void> requisicaoInvalida() {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
    }
}
