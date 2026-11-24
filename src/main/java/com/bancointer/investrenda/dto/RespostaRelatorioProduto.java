package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.domain.Produto;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

@Schema(description = "Relatorio de um produto com todas as aplicacoes (compras) que ja o utilizaram, "
        + "mais recentes primeiro. Nao passa por cache - reflete o estado atual das aplicacoes, "
        + "inclusive as ainda PENDENTE.")
public record RespostaRelatorioProduto(

        RespostaDetalheProduto produto,

        @Schema(description = "Quantidade de aplicacoes retornadas.", example = "3")
        int totalAplicacoes,

        @ArraySchema(schema = @Schema(implementation = RespostaAplicacao.class))
        List<RespostaAplicacao> aplicacoes
) implements Serializable {

    public static RespostaRelatorioProduto from(Produto produto, List<Aplicacao> aplicacoes) {
        List<RespostaAplicacao> respostaAplicacoes = aplicacoes.stream()
                .map(RespostaAplicacao::from)
                .toList();
        return new RespostaRelatorioProduto(
                RespostaDetalheProduto.from(produto),
                respostaAplicacoes.size(),
                respostaAplicacoes
        );
    }
}
