package com.bancointer.investrenda.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PropriedadesIntegracaoExterna.class)
public class ConfiguracaoIntegracaoExterna {

    @Bean
    public RestClient restClientCotacaoDolar(PropriedadesIntegracaoExterna propriedades) {
        return RestClient.builder()
                .baseUrl(propriedades.getUrlBase())
                .build();
    }
}
