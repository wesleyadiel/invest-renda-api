package com.bancointer.investrenda.dto;

import java.util.List;

/**
 * Envelope padrao de toda resposta da API (sucesso ou erro) - inclusive as
 * tratadas pelo ManipuladorGlobalExcecao. "dados" e nulo em erros; "erros" e
 * vazio em sucessos, e so tem mais de um item quando ha varias violacoes de
 * validacao de uma vez (os outros tipos de erro carregam uma causa so, que
 * ja fica em "mensagem").
 */
public record RespostaApi<T>(
        boolean sucesso,
        T dados,
        String mensagem,
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
