package com.bancointer.investrenda.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * investrenda.integracaoexterna.* no application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "investrenda.integracaoexterna")
public class PropriedadesIntegracaoExterna {

    /** Estado inicial do feature toggle ao subir a aplicacao. */
    private boolean habilitado = true;

    /** Base da AwesomeAPI (cotacoes economicas, publica, sem autenticacao). */
    private String urlBase = "https://economia.awesomeapi.com.br";
}
