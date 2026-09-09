package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.validation.CPF;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Dados para abertura de uma conta.")
public record RequisicaoConta(

        @Schema(description = "CPF do titular, 11 digitos numericos, com digito verificador validado "
                + "(nao aceita sequencias como \"00000000000\").", example = "11144477735")
        @NotNull @Pattern(regexp = "\\d{11}", message = "cpf deve conter 11 digitos numericos")
        @CPF
        String cpf,

        @Schema(description = "Nome do titular da conta.", example = "Maria Teste")
        @NotNull @Size(min = 3, max = 120)
        String nomeTitular,

        @Schema(description = "Saldo inicial da conta, em reais. Omitido = 0.", example = "1000.00")
        @DecimalMin(value = "0.0", message = "saldo inicial nao pode ser negativo")
        BigDecimal saldoInicial
) {
}
