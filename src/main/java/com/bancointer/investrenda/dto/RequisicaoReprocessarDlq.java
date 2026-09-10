package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Reprocessa uma mensagem presa na dead-letter queue, opcionalmente ajustando o valor "
        + "antes de reenviar para a fila principal.")
public record RequisicaoReprocessarDlq(

        @Schema(description = "Identificador da aplicacao a reprocessar - o mesmo retornado em "
                + "GET /operacoes/fila/dlq.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull UUID aplicacaoId,

        @Schema(description = "Novo valor, se quiser corrigir antes de reenviar (ex: reduzir para caber no "
                + "saldo disponivel). Se omitido, reenvia com o valor original da mensagem.", nullable = true)
        @DecimalMin(value = "0.0", inclusive = false, message = "novoValor deve ser maior que zero")
        BigDecimal novoValor
) {
}
