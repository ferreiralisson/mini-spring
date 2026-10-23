package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Indica que o argumento vem da desserialização do corpo JSON.
// Cada método aceita no máximo um corpo; o DTO não é um bean singleton.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface RequestBody {  }
