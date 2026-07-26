package com.rinha.backend;

import com.rinha.backend.service.TransacaoBatchProperties;
import com.rinha.backend.service.TransacaoRetryProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties({TransacaoRetryProperties.class, TransacaoBatchProperties.class})
@EnableCaching
public class MinhaRinhaAppQ12024Application {

	public static void main(String[] args) {
		SpringApplication.run(MinhaRinhaAppQ12024Application.class, args);
	}

}
