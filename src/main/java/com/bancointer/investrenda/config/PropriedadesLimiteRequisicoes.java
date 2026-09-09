package com.bancointer.investrenda.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * investrenda.limiterequisicoes.* no application.yml. Um bucket por
 * cliente (identificado por IP), compartilhado entre todos os endpoints da
 * API.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "investrenda.limiterequisicoes")
public class PropriedadesLimiteRequisicoes {

    /** Liga/desliga o rate limit sem precisar remover o filtro. */
    private boolean habilitado = true;

    /** Quantas requisicoes um cliente pode fazer de uma vez (rajada). */
    private int capacidade = 120;

    /** Quantos tokens sao devolvidos ao bucket a cada "duracaoReposicaoSegundos". */
    private int tokensReposicao = 120;

    /** Janela, em segundos, em que "tokensReposicao" sao devolvidos. */
    private int duracaoReposicaoSegundos = 60;
}
