package com.rinha.backend.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rinha.transacao.lote")
public class TransacaoBatchProperties {
    private int tamanhoMaximo = 64;
    private int capacidadeFilaPorCliente = 256;
    private int janelaColetaMillis = 1;
    public int getTamanhoMaximo() { return tamanhoMaximo; }
    public void setTamanhoMaximo(int tamanhoMaximo) { this.tamanhoMaximo = tamanhoMaximo; }
    public int getCapacidadeFilaPorCliente() { return capacidadeFilaPorCliente; }
    public void setCapacidadeFilaPorCliente(int capacidadeFilaPorCliente) { this.capacidadeFilaPorCliente = capacidadeFilaPorCliente; }
    public int getJanelaColetaMillis() { return janelaColetaMillis; }
    public void setJanelaColetaMillis(int janelaColetaMillis) { this.janelaColetaMillis = janelaColetaMillis; }
}
