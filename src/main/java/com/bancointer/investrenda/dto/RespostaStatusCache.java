package com.bancointer.investrenda.dto;

import java.util.List;

public record RespostaStatusCache(
        boolean listaProdutosCacheada,
        Long listaProdutosTtlSegundos,
        List<ItemCacheProduto> itensCacheProduto
) {
}
