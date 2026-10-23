package br.com.minispring.framework.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Ponto de extensão para preocupações transversais. Uma instância atende várias threads. */
public interface RequestInterceptor {
    default void before(HttpServletRequest request, HttpServletResponse response) {}
    default void after(HttpServletRequest request, HttpServletResponse response) {}
}
