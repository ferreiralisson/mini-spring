package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Registra um bean e permite ao Router procurar seus métodos @Route.
// value define o prefixo comum das rotas deste controller.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Component
public @interface RestController { String value() default ""; }
