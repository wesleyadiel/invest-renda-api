package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.enums.TipoProduto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Dados de cadastro/edicao de um produto de renda fixa. Usado tanto no "
        + "POST (criar) quanto no PUT (editar) - mesmo payload nos dois casos.")
public record RequisicaoProduto(

        @Schema(description = "Nome comercial do produto.", example = "CDB Banco Inter 120% CDI")
        @NotNull @Size(min = 3, max = 120)
        String nome,

        @Schema(description = "Tipo do produto de renda fixa.", example = "CDB")
        @NotNull
        TipoProduto tipo,

        @Schema(description = "Instituicao emissora do produto.", example = "Banco Inter")
        @NotNull @Size(min = 2, max = 120)
        String emissor,

        @Schema(description = "Classificacao de risco do emissor (rating).", example = "AA+")
        @NotNull @Size(min = 1, max = 10)
        String classificacaoEmissor,

        @Schema(description = "Taxa de rendimento anual, em percentual.", example = "13.5")
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal taxaAnual,

        @Schema(description = "Valor minimo exigido para aplicar neste produto, em reais.", example = "100.00")
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal investimentoMinimo,

        @Schema(description = "Prazo de vencimento do produto, em meses.", example = "12")
        @NotNull @Positive
        Integer prazoMeses,

        @Schema(description = "Se o produto esta disponivel para novas aplicacoes. "
                + "Quando omitido: `true` na criacao, mantem o valor atual na edicao.", example = "true")
        Boolean ativo
) {
}
