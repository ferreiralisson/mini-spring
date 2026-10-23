package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Descreve método HTTP e caminho. A anotação não atende requisições sozinha:
// o Router lê estes valores no bootstrap e o dispatcher invoca o método depois.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Route { String method(); String path() default ""; }
