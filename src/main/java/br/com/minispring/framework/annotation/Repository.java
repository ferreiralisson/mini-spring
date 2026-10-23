package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Identifica a camada de armazenamento. Não gera consultas nem transações;
// a implementação continua sendo responsabilidade da classe anotada.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Component
public @interface Repository {  }
