package com.bancointer.investrenda.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoLogRequisicao {

    @Bean
    public FilterRegistrationBean<FiltroLogRequisicao> registroFiltroLogRequisicao() {
        FilterRegistrationBean<FiltroLogRequisicao> registro = new FilterRegistrationBean<>(new FiltroLogRequisicao());
        registro.addUrlPatterns("/produtos/*", "/contas/*", "/aplicacoes/*", "/operacoes/*", "/integracoes/*");
        registro.setOrder(0);
        return registro;
    }
}
