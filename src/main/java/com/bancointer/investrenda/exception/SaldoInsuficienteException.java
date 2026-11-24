package com.bancointer.investrenda.exception;

/**
 * Falha transitoria ao consultar a cotacao (erro de rede, timeout, resposta
 * invalida da API externa, ou falha simulada via toggle de teste). E o unico
 * tipo, entre os de integracao externa, marcado como retryable no
 * Resilience4j (application.yml) - as demais sao permanentes e nao valem
 * retry.
 */
public class SaldoInsuficienteException extends IntegracaoExternaException {
    public SaldoInsuficienteException(String message) {
        super(message);
    }

    public SaldoInsuficienteException(String message, Throwable causa) {
        super(message, causa);
    }
}
