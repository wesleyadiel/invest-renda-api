package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.Conta;

import java.math.BigDecimal;
import java.util.UUID;

public record RespostaConta(
        UUID id,
        String cpf,
        String nomeTitular,
        BigDecimal saldo
) {

    public static RespostaConta from(Conta conta) {
        return new RespostaConta(
                conta.getId(),
                conta.getCpf(),
                conta.getNomeTitular(),
                conta.getSaldo()
        );
    }
}
