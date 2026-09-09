package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Liga ou desliga um toggle da integracao externa.")
public record RequisicaoToggleIntegracao(

        @Schema(description = "true para ligar, false para desligar.", example = "true")
        @NotNull Boolean ativo
) {
}
