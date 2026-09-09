package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado atual da integracao com a API de cotacao do dolar.")
public record StatusIntegracaoExterna(

        @Schema(description = "Feature toggle: se a integracao esta habilitada.")
        boolean habilitado,

        @Schema(description = "Se a simulacao de falha esta ativa (forca erro na proxima consulta, para "
                + "testar retry/circuit breaker de forma deterministica).")
        boolean simulandoFalha,

        @Schema(description = "CLOSED (normal) | OPEN (falha rapida, sem chamar a API externa) | "
                + "HALF_OPEN (testando se pode voltar a fechar).", example = "CLOSED",
                allowableValues = {"CLOSED", "OPEN", "HALF_OPEN"})
        String estadoCircuito
) {
}
