package br.com.minispring.framework.validation;

import java.lang.annotation.*;

// Exige texto não nulo e com pelo menos um caractere que não seja espaço em branco.
// RECORD_COMPONENT permite ao Validator encontrar a regra em getRecordComponents().
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface NotBlank {}
