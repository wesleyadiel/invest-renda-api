package com.bancointer.investrenda.dto;

public record StatusFila(
        String nome,
        boolean existe,
        long quantidadeMensagens,
        int quantidadeConsumidores
) {
}
