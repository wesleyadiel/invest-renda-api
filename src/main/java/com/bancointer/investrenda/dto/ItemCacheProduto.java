package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Uma entrada de cache de detalhe de produto (chave `produto::{id}` no Redis).")
public record ItemCacheProduto(

        @Schema(description = "Identificador do produto cujo detalhe esta em cache.")
        UUID produtoId,

        @Schema(description = "Tempo restante ate essa entrada expirar, em segundos.", example = "570")
        Long ttlSegundos
) {
}
