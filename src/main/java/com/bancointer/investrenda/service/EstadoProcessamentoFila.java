package com.bancointer.investrenda.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Estado em memoria (nao persiste, reseta a cada restart da aplicacao) do
 * toggle de simulacao de falha no processamento da fila de aplicacoes -
 * usado para exercitar o retry do listener (3x, backoff 1s/2s/4s,
 * spring.rabbitmq.listener.simple.retry no application.yml) e o roteamento
 * para a dead-letter queue apos as tentativas se esgotarem, sem depender de
 * um erro real de negocio (conta/produto ausente, saldo insuficiente etc).
 */
@Component
public class EstadoProcessamentoFila {

    private final AtomicBoolean simularFalha = new AtomicBoolean(false);

    public boolean isSimularFalha() {
        return simularFalha.get();
    }

    public void setSimularFalha(boolean valor) {
        simularFalha.set(valor);
    }
}
