package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.TipoProduto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Resumo de um produto, usado na listagem (sem os campos de auditoria/emissor "
        + "do detalhe - ver /produtos/{id} para isso).")
public record RespostaResumoProduto(

        @Schema(description = "Identificador do produto.", example = "78b95996-2f1c-4738-99e0-aa30fa340002")
        UUID id,

        @Schema(example = "CDB Banco Inter 120% CDI")
        String nome,

        TipoProduto tipo,

        @Schema(example = "13.5000")
        BigDecimal taxaAnual,

        @Schema(example = "100.00")
        BigDecimal investimentoMinimo,

        @Schema(example = "12")
        Integer prazoMeses
) implements Serializable {

    public static RespostaResumoProduto from(Produto produto) {
        return new RespostaResumoProduto(
                produto.getId(),
                produto.getNome(),
                produto.getTipo(),
                produto.getTaxaAnual(),
                produto.getInvestimentoMinimo(),
                produto.getPrazoMeses()
        );
    }
}
