package com.bancointer.investrenda.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que a string e um CPF matematicamente valido (digitos
 * verificadores corretos), nao so uma sequencia de 11 digitos. Nulo e
 * considerado valido - use junto com {@code @NotNull} para exigir presenca.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidadorCpf.class)
@Documented
public @interface CPF {

    String message() default "CPF invalido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
