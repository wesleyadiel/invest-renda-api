package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.validation.CPF;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record RequisicaoAplicacao(

        @NotNull @Pattern(regexp = "\\d{11}", message = "cpf deve conter 11 digitos numericos")
        @CPF
        String cpf,

        @NotNull
        UUID produtoId,

        @NotNull @Positive
        BigDecimal valor
) {
}
