package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Associa um argumento do método a um segmento nomeado, como {id}.
// O valor explícito evita depender do nome de parâmetro preservado no bytecode.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface PathVariable { String value(); }
