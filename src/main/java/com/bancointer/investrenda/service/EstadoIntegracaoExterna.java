package com.bancointer.investrenda.service;

import com.bancointer.investrenda.config.PropriedadesIntegracaoExterna;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Estado em memoria (nao persiste, reseta a cada restart da aplicacao) dos
 * dois toggles da integracao externa: o feature flag em si e o modo de
 * simulacao de falha usado para exercitar o retry/circuit breaker sem
 * depender da API externa de verdade estar fora do ar.
 */
@Component
public class EstadoIntegracaoExterna {

    private final AtomicBoolean habilitado;
    private final AtomicBoolean simularFalha = new AtomicBoolean(false);

    public EstadoIntegracaoExterna(PropriedadesIntegracaoExterna propriedades) {
        this.habilitado = new AtomicBoolean(propriedades.isHabilitado());
    }

    public boolean isHabilitado() {
        return habilitado.get();
    }

    public void setHabilitado(boolean valor) {
        habilitado.set(valor);
    }

    public boolean isSimularFalha() {
        return simularFalha.get();
    }

    public void setSimularFalha(boolean valor) {
        simularFalha.set(valor);
    }
}
