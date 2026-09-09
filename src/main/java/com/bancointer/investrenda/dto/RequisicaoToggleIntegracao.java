package com.bancointer.investrenda.dto;

import jakarta.validation.constraints.NotNull;

public record RequisicaoToggleIntegracao(
        @NotNull Boolean ativo
) {
}
