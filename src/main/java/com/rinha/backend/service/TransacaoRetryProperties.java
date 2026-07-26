package com.rinha.backend.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rinha.transacao.retry")
public class TransacaoRetryProperties {

    private int maxTentativas = 12;
    private int esperaMinMillis = 1;
    private int esperaMaxMillis = 3;

    public int getMaxTentativas() {
        return maxTentativas;
    }

    public void setMaxTentativas(int maxTentativas) {
        this.maxTentativas = maxTentativas;
    }

    public int getEsperaMinMillis() {
        return esperaMinMillis;
    }

    public void setEsperaMinMillis(int esperaMinMillis) {
        this.esperaMinMillis = esperaMinMillis;
    }

    public int getEsperaMaxMillis() {
        return esperaMaxMillis;
    }

    public void setEsperaMaxMillis(int esperaMaxMillis) {
        this.esperaMaxMillis = esperaMaxMillis;
    }
}
