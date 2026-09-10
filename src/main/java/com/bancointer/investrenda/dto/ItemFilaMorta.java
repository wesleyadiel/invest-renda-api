package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Uma mensagem presa na dead-letter queue, pronta para reprocessamento manual.")
public record ItemFilaMorta(

        @Schema(description = "Identificador da aplicacao - use este valor em POST /operacoes/fila/dlq/reprocessar.")
        UUID aplicacaoId,

        @Schema(description = "Conta associada a aplicacao.")
        UUID contaId,

        @Schema(description = "Produto associado a aplicacao.")
        UUID produtoId,

        @Schema(description = "Valor original da aplicacao (o que estava na mensagem quando ela caiu na DLQ).")
        BigDecimal valor
) {
}
