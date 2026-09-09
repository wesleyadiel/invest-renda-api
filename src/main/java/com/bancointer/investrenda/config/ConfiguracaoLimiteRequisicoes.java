package com.bancointer.investrenda.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra o FiltroLimiteRequisicoes so nos endpoints de negocio da API -
 * nao no /actuator/health (usado pelo HEALTHCHECK do Docker a cada poucos
 * segundos, nunca deveria ser limitado) nem no Swagger.
 */
@Configuration
@EnableConfigurationProperties(PropriedadesLimiteRequisicoes.class)
public class ConfiguracaoLimiteRequisicoes {

    @Bean
    public FilterRegistrationBean<FiltroLimiteRequisicoes> registroFiltroLimiteRequisicoes(
            PropriedadesLimiteRequisicoes propriedades, ObjectMapper objectMapper) {
        FilterRegistrationBean<FiltroLimiteRequisicoes> registro =
                new FilterRegistrationBean<>(new FiltroLimiteRequisicoes(propriedades, objectMapper));
        registro.addUrlPatterns("/produtos/*", "/contas/*", "/aplicacoes/*", "/operacoes/*");
        registro.setOrder(1);
        return registro;
    }
}
