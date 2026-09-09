package com.bancointer.investrenda.exception;

/**
 * Superclasse de tudo que pode dar errado na integracao com APIs externas -
 * permite ao {@link ManipuladorGlobalExcecao} tratar todas com a mesma
 * resposta HTTP sem precisar conhecer cada subtipo.
 */
public abstract class IntegracaoExternaException extends RuntimeException {
    protected IntegracaoExternaException(String message) {
        super(message);
    }

    protected IntegracaoExternaException(String message, Throwable causa) {
        super(message, causa);
    }
}
