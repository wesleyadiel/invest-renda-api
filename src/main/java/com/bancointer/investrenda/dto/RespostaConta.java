package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.Conta;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Dados de uma conta e seu saldo atual.")
public record RespostaConta(

        @Schema(description = "Identificador da conta.", example = "0ff4300d-ed80-4f91-a610-81c48e3aef41")
        UUID id,

        @Schema(example = "11144477735")
        String cpf,

        @Schema(example = "Maria Teste")
        String nomeTitular,

        @Schema(description = "Saldo atual da conta, em reais.", example = "700.00")
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
