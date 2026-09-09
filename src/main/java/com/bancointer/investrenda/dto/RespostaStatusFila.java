package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado das duas filas envolvidas no processamento de aplicacoes.")
public record RespostaStatusFila(

        @Schema(description = "Fila principal, onde a aplicacao entra assim que solicitada.")
        StatusFila aplicacoes,

        @Schema(description = "Dead-letter queue - mensagens que falharam nas tentativas de reprocessamento "
                + "caem aqui. Ter mensagens presas nesta fila indica que algo precisa de investigacao manual.")
        StatusFila filaMorta
) {
}
