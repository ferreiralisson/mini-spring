package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Associa um parâmetro de construtor a uma chave de configuração, como app.name.
// A resolução acontece na criação do bean; não há atualização automática em runtime.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Value { String value(); }
