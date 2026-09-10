package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Deposita um valor na conta - soma ao saldo atual, nao substitui.")
public record RequisicaoDeposito(

        @Schema(description = "Valor a depositar, em reais. Precisa ser maior que zero.", example = "500.00")
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false, message = "valor deve ser maior que zero")
        BigDecimal valor
) {
}
