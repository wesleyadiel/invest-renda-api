package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.domain.enums.StatusAplicacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RespostaAplicacao(
        UUID id,
        UUID contaId,
        String contaCpf,
        UUID produtoId,
        String produtoNome,
        BigDecimal valor,
        StatusAplicacao status,
        String motivoFalha,
        BigDecimal taxaAplicada,
        LocalDate dataVencimento,
        Instant criadoEm
) {

    public static RespostaAplicacao from(Aplicacao aplicacao) {
        return new RespostaAplicacao(
                aplicacao.getId(),
                aplicacao.getConta().getId(),
                aplicacao.getConta().getCpf(),
                aplicacao.getProduto().getId(),
                aplicacao.getProduto().getNome(),
                aplicacao.getValor(),
                aplicacao.getStatus(),
                aplicacao.getMotivoFalha(),
                aplicacao.getTaxaAplicada(),
                aplicacao.getDataVencimento(),
                aplicacao.getCriadoEm()
        );
    }
}
