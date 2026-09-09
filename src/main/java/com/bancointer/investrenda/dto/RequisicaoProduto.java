package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.enums.TipoProduto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RequisicaoProduto(

        @NotNull @Size(min = 3, max = 120)
        String nome,

        @NotNull
        TipoProduto tipo,

        @NotNull @Size(min = 2, max = 120)
        String emissor,

        @NotNull @Size(min = 1, max = 10)
        String classificacaoEmissor,

        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal taxaAnual,

        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal investimentoMinimo,

        @NotNull @Positive
        Integer prazoMeses,

        Boolean ativo
) {
}
