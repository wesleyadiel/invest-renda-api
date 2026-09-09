package com.bancointer.investrenda.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Libera o front-end de validacao (Angular, rodando em outra porta) a
 * consumir a API durante o desenvolvimento local. Nao existe autenticacao
 * neste projeto, entao isto nao expoe nada que ja nao estivesse acessivel
 * diretamente via HTTP.
 */
@Configuration
public class ConfiguracaoCors implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // qualquer porta local: o dev server do Angular muda de porta
                // se a 4200 default ja estiver ocupada por outro projeto.
                .allowedOriginPatterns("http://localhost:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
