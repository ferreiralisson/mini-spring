package br.com.minispring.framework.validation;

import java.lang.annotation.*;

// Exige um valor numérico maior que zero; zero também é inválido.
// RECORD_COMPONENT permite ao Validator encontrar a regra em getRecordComponents().
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Positive {}
