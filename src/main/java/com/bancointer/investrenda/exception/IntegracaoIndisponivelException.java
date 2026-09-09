package com.bancointer.investrenda.exception;

/**
 * Lancada pelo metodo de fallback do Resilience4j quando as tentativas de
 * retry se esgotam ou quando o circuit breaker esta aberto (falha rapida,
 * sem nem tentar chamar a API externa).
 */
public class IntegracaoIndisponivelException extends IntegracaoExternaException {
    public IntegracaoIndisponivelException(String message) {
        super(message);
    }
}
