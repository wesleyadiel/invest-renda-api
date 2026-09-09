package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.TipoProduto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RespostaDetalheProduto(
        UUID id,
        String nome,
        TipoProduto tipo,
        String emissor,
        String classificacaoEmissor,
        BigDecimal taxaAnual,
        BigDecimal investimentoMinimo,
        Integer prazoMeses,
        boolean ativo,
        Instant criadoEm,
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
