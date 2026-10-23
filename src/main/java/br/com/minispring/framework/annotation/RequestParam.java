package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Lê um argumento da query string, como ?nome=java. Se ausente, usa defaultValue.
// Para um parâmetro numérico opcional, informe um default que possa ser convertido.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface RequestParam { String value(); String defaultValue() default ""; }
