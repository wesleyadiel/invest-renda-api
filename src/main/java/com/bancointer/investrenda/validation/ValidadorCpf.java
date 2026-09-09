package com.bancointer.investrenda.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Algoritmo padrao de CPF: 9 digitos base + 2 digitos verificadores
 * calculados por modulo 11. Rejeita tambem sequencias como "00000000000"
 * ou "11111111111", que teriam digitos verificadores validos mas nunca
 * sao CPFs reais emitidos.
 */
public class ValidadorCpf implements ConstraintValidator<CPF, String> {

    @Override
    public boolean isValid(String cpf, ConstraintValidatorContext context) {
        if (cpf == null) {
            return true;
        }
        return cpfValido(cpf);
    }

    private boolean cpfValido(String cpf) {
        if (cpf.length() != 11 || !cpf.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }

        int[] digitos = cpf.chars().map(c -> c - '0').toArray();

        int primeiroDigitoVerificador = calcularDigitoVerificador(digitos, 9, 10);
        if (primeiroDigitoVerificador != digitos[9]) {
            return false;
        }

        int segundoDigitoVerificador = calcularDigitoVerificador(digitos, 10, 11);
        return segundoDigitoVerificador == digitos[10];
    }

    private int calcularDigitoVerificador(int[] digitos, int quantidadeDigitos, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < quantidadeDigitos; i++) {
            soma += digitos[i] * (pesoInicial - i);
        }
        int resto = 11 - (soma % 11);
        return resto >= 10 ? 0 : resto;
    }
}
