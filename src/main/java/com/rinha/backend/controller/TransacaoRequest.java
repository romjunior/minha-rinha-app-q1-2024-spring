package com.rinha.backend.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransacaoRequest(
    @NotNull @Positive Integer valor,
    @NotNull @Pattern(regexp = "[cd]") String tipo,
    @NotBlank @Size(max = 10) String descricao) {
}
