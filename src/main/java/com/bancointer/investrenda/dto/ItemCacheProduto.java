package com.bancointer.investrenda.dto;

import java.util.UUID;

public record ItemCacheProduto(
        UUID produtoId,
        Long ttlSegundos
) {
}
