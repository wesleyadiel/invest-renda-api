package com.bancointer.investrenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Envelope padrao de toda resposta da API, sucesso ou erro.")
public record RespostaApi<T>(

        @Schema(description = "true em respostas de sucesso, false em erro.")
        boolean sucesso,

        @Schema(description = "Payload do endpoint em caso de sucesso; sempre null em erro.", nullable = true)
        T dados,

        @Schema(description = "Descricao curta do que aconteceu (sucesso) ou a mensagem de erro.")
        String mensagem,

        @Schema(description = "Lista de erros - so tem mais de um item em erro de validacao (um por campo "
                + "invalido); nos demais casos vem vazia e a causa unica fica em `mensagem`.")
        List<String> erros
) {

    public static <T> RespostaApi<T> criarSucesso(T dados, String mensagem) {
        return new RespostaApi<>(true, dados, mensagem, List.of());
    }

    public static <T> RespostaApi<T> criarErro(String mensagem) {
        return new RespostaApi<>(false, null, mensagem, List.of());
    }

    public static <T> RespostaApi<T> criarErro(String mensagem, List<String> erros) {
        return new RespostaApi<>(false, null, mensagem, erros);
    }
}
