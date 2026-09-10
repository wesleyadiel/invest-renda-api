package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado das duas filas envolvidas no processamento de aplicacoes.")
public record RespostaStatusFila(

        @Schema(description = "Fila principal, onde a aplicacao entra assim que solicitada.")
        StatusFila aplicacoes,

        @Schema(description = "Dead-letter queue - mensagens que falharam nas tentativas de reprocessamento "
                + "caem aqui. Ter mensagens presas nesta fila indica que algo precisa de investigacao manual.")
        StatusFila filaMorta,

        @Schema(description = "Se a simulacao de falha no processamento esta ativa (forca erro no consumidor "
                + "antes de tocar o banco, para testar retry/dead-letter de forma deterministica).")
        boolean simulandoFalha
) {
}
