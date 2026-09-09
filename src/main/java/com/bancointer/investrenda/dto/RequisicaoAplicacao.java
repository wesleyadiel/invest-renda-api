package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.validation.CPF;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Solicitacao de aplicacao (compra de investimento) em um produto.")
public record RequisicaoAplicacao(

        @Schema(description = "CPF da conta que vai aplicar (precisa existir).", example = "11144477735")
        @NotNull @Pattern(regexp = "\\d{11}", message = "cpf deve conter 11 digitos numericos")
        @CPF
        String cpf,

        @Schema(description = "Identificador do produto a aplicar (precisa existir e estar ativo).",
                example = "78b95996-2f1c-4738-99e0-aa30fa340002")
        @NotNull
        UUID produtoId,

        @Schema(description = "Valor a aplicar, em reais. Precisa ser >= ao investimento minimo do produto "
                + "(validado na hora); o saldo so e conferido depois, de forma assincrona.", example = "300.00")
        @NotNull @Positive
        BigDecimal valor
) {
}
