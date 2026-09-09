package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Cotacao do dolar consultada na AwesomeAPI.")
public record RespostaCotacaoDolar(

        @Schema(description = "Par de moedas consultado.", example = "USD/BRL")
        String par,

        @Schema(example = "5.1066")
        BigDecimal cotacaoCompra,

        @Schema(example = "5.1073")
        BigDecimal cotacaoVenda,

        @Schema(description = "Momento em que a consulta foi feita.")
        Instant consultadoEm
) {
}
