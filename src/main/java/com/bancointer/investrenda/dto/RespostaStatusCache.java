package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Estado ao vivo do cache Redis de produtos.")
public record RespostaStatusCache(

        @Schema(description = "Se a listagem de produtos (GET /produtos) esta cacheada no momento.")
        boolean listaProdutosCacheada,

        @Schema(description = "Tempo restante ate a listagem expirar do cache, em segundos - null se nao "
                + "estiver cacheada.", example = "599", nullable = true)
        Long listaProdutosTtlSegundos,

        @Schema(description = "Detalhes de produto individualmente em cache (chave `produto::{id}`).")
        List<ItemCacheProduto> itensCacheProduto
) {
}
