package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Identifica a camada de regras de negócio. @Component permite sua descoberta;
// neste mini framework, não adiciona comportamento além do registro como bean.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Component
public @interface Service {  }
