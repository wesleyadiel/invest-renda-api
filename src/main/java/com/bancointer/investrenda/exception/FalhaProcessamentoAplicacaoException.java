package com.bancointer.investrenda.exception;

/**
 * Falha simulada via toggle de teste (PUT /operacoes/fila/simular-falha),
 * lancada antes de qualquer acesso ao banco no
 * ConsumidorRequisicaoAplicacao. Nao tem tratamento no
 * ManipuladorGlobalExcecao de proposito: quem lanca essa excecao e o
 * listener RabbitMQ, nao a camada HTTP - ela precisa propagar para o
 * mecanismo de retry do container (spring.rabbitmq.listener.simple.retry).
 */
public class FalhaProcessamentoAplicacaoException extends RuntimeException {
    public FalhaProcessamentoAplicacaoException(String message) {
        super(message);
    }
}
