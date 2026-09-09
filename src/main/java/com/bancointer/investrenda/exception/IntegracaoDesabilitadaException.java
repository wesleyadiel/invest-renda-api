package com.bancointer.investrenda.exception;

/**
 * A integracao foi desligada deliberadamente via feature toggle
 * (PUT /integracoes/habilitar). Diferente de uma falha real, entao fica de
 * fora do retry/circuit breaker (application.yml, resilience4j.*.ignore-
 * exceptions) - nao faz sentido tentar de novo algo que foi desligado de
 * proposito.
 */
public class IntegracaoDesabilitadaException extends IntegracaoExternaException {
    public IntegracaoDesabilitadaException(String message) {
        super(message);
    }
}
