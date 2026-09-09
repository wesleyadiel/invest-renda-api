package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.TipoProduto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Detalhe completo de um produto, incluindo emissor, classificacao de risco, "
        + "estado (ativo/inativo) e datas de auditoria.")
public record RespostaDetalheProduto(

        @Schema(description = "Identificador do produto.", example = "78b95996-2f1c-4738-99e0-aa30fa340002")
        UUID id,

        @Schema(example = "CDB Banco Inter 120% CDI")
        String nome,

        TipoProduto tipo,

        @Schema(example = "Banco Inter")
        String emissor,

        @Schema(example = "AA+")
        String classificacaoEmissor,

        @Schema(example = "13.5000")
        BigDecimal taxaAnual,

        @Schema(example = "100.00")
        BigDecimal investimentoMinimo,

        @Schema(example = "12")
        Integer prazoMeses,

        @Schema(description = "Se o produto esta disponivel para novas aplicacoes.")
        boolean ativo,

        @Schema(description = "Data/hora de criacao do registro (preenchida automaticamente).")
        Instant criadoEm,

        @Schema(description = "Data/hora da ultima atualizacao do registro (preenchida automaticamente).")
        Instant atualizadoEm
) implements Serializable {

    public static RespostaDetalheProduto from(Produto produto) {
        return new RespostaDetalheProduto(
                produto.getId(),
                produto.getNome(),
                produto.getTipo(),
                produto.getEmissor(),
                produto.getClassificacaoEmissor(),
                produto.getTaxaAnual(),
                produto.getInvestimentoMinimo(),
                produto.getPrazoMeses(),
                produto.isAtivo(),
                produto.getCriadoEm(),
                produto.getAtualizadoEm()
        );
    }
}
