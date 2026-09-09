package com.bancointer.investrenda.dto;

public record StatusIntegracaoExterna(
        boolean habilitado,
        boolean simulandoFalha,
        String estadoCircuito
) {
}
