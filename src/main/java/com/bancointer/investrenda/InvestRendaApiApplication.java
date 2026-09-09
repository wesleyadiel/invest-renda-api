package com.bancointer.investrenda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Ordem explicita entre cache e transacao: sem isso, o Spring nao garante se
 * o @CacheEvict roda antes ou depois do commit no Postgres. Order menor =
 * advice mais externo, entao o cache (0) envolve a transacao (1) e so
 * invalida o Redis depois que o commit realmente aconteceu.
 */
@SpringBootApplication
@EnableCaching(order = InvestRendaApiApplication.CACHE_ADVICE_ORDER)
@EnableTransactionManagement(order = InvestRendaApiApplication.TRANSACTION_ADVICE_ORDER)
public class InvestRendaApiApplication {

    static final int CACHE_ADVICE_ORDER = 0;
    static final int TRANSACTION_ADVICE_ORDER = 1;

    public static void main(String[] args) {
        SpringApplication.run(InvestRendaApiApplication.class, args);
    }
}
