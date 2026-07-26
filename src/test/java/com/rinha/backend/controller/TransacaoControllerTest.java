package com.rinha.backend.controller;

import com.rinha.backend.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransacaoControllerTest {

    private TransacaoService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(TransacaoService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TransacaoController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void aceitaTransacaoValida() throws Exception {
        when(service.processarTransacao(eq(1), any(TransacaoRequest.class)))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(new TransacaoResponse(1_000, 100)));

        MvcResult resultado = mockMvc.perform(post("/clientes/1/transacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":100,\"tipo\":\"c\",\"descricao\":\"pix\"}"))
            .andReturn();
        mockMvc.perform(asyncDispatch(resultado))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limite").value(1_000))
            .andExpect(jsonPath("$.saldo").value(100));
    }

    @Test
    void rejeitaPayloadInvalidoCom422() throws Exception {
        mockMvc.perform(post("/clientes/1/transacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":0,\"tipo\":\"x\",\"descricao\":\"\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void retorna404ParaClienteInexistente() throws Exception {
        doThrow(new TransacaoService.ClienteNaoEncontradoException())
            .when(service).obterExtrato(6);

        mockMvc.perform(get("/clientes/6/extrato"))
            .andExpect(status().isNotFound());
    }

    @Test
    void retorna503QuandoOsConflitosDeConcorrenciaSeEsgotam() throws Exception {
        when(service.processarTransacao(eq(1), any(TransacaoRequest.class)))
            .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(
                new TransacaoService.ConflitoConcorrenciaException()));

        MvcResult resultado = mockMvc.perform(post("/clientes/1/transacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":100,\"tipo\":\"c\",\"descricao\":\"pix\"}"))
            .andReturn();
        mockMvc.perform(asyncDispatch(resultado))
            .andExpect(status().isServiceUnavailable());
    }
}
