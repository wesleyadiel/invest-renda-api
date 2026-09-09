package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.TipoProduto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record RespostaResumoProduto(
        UUID id,
        String nome,
        TipoProduto tipo,
        BigDecimal taxaAnual,
        BigDecimal investimentoMinimo,
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
