package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Marca classes elegíveis para o container. ANNOTATION_TYPE permite usá-la
// também sobre @Service, @Repository e @RestController (meta-anotações).
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface Component {  }
