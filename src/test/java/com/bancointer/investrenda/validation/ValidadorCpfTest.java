package com.bancointer.investrenda.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorCpfTest {

    private final ValidadorCpf validador = new ValidadorCpf();
    private final ConstraintValidatorContext context = null; // nao usado pela implementacao

    @Test
    void deveAceitarNuloPoisNotNullTrataObrigatoriedade() {
        assertThat(validador.isValid(null, context)).isTrue();
    }

    @Test
    void deveAceitarCpfValido() {
        assertThat(validador.isValid("11144477735", context)).isTrue();
    }

    @Test
    void deveRejeitarCpfComDigitosVerificadoresErrados() {
        assertThat(validador.isValid("11144477736", context)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00000000000", "11111111111", "22222222222", "99999999999"
    })
    void deveRejeitarSequenciasDeDigitosRepetidos(String cpf) {
        assertThat(validador.isValid(cpf, context)).isFalse();
    }

    @Test
    void deveRejeitarTamanhoErrado() {
        assertThat(validador.isValid("1114447773", context)).isFalse();
        assertThat(validador.isValid("111444777355", context)).isFalse();
    }

    @Test
    void deveRejeitarCaracteresNaoNumericos() {
        assertThat(validador.isValid("1114447773a", context)).isFalse();
        assertThat(validador.isValid("111.444.777-35", context)).isFalse();
    }
}
