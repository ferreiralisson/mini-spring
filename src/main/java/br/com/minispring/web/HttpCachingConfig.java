package br.com.minispring.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/** Gera ETag nas respostas de /cursos e responde 304 quando If-None-Match coincide. */
@Configuration
public class HttpCachingConfig {
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        var registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/cursos", "/cursos/*");
        return registration;
    }
}
