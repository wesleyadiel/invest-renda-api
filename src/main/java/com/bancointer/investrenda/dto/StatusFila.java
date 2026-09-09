package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado ao vivo de uma fila do RabbitMQ.")
public record StatusFila(

        @Schema(description = "Nome da fila no RabbitMQ.", example = "investrenda.aplicacoes.fila")
        String nome,

        @Schema(description = "Se a fila existe de fato no broker no momento da consulta.")
        boolean existe,

        @Schema(description = "Quantidade de mensagens atualmente na fila.", example = "0")
        long quantidadeMensagens,

        @Schema(description = "Quantidade de consumidores ativos ouvindo a fila (para a fila de aplicacoes, "
                + "deveria ser 1 - o listener da propria API).", example = "1")
        int quantidadeConsumidores
) {
}
