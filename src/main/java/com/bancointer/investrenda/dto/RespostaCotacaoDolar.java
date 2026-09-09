package com.bancointer.investrenda.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RespostaCotacaoDolar(
        String par,
        BigDecimal cotacaoCompra,
        BigDecimal cotacaoVenda,
        Instant consultadoEm
) {
}
