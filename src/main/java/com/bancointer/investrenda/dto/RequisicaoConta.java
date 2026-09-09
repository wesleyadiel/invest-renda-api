package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.validation.CPF;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RequisicaoConta(

        @NotNull @Pattern(regexp = "\\d{11}", message = "cpf deve conter 11 digitos numericos")
        @CPF
        String cpf,

        @NotNull @Size(min = 3, max = 120)
        String nomeTitular,

        @DecimalMin(value = "0.0", message = "saldo inicial nao pode ser negativo")
        BigDecimal saldoInicial
) {
}
