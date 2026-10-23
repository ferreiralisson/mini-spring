package br.com.minispring.framework.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.slf4j.LoggerFactory;

public final class LoggingInterceptor implements RequestInterceptor {
    @Override public void before(HttpServletRequest request, HttpServletResponse response) {
        // nanoTime mede duração sem depender de ajustes no relógio de calendário.
        // Guardar no request isola o estado; campos no interceptor seriam compartilhados.
        request.setAttribute("startNanos", System.nanoTime());
        request.setAttribute("requestId", UUID.randomUUID().toString());
        // O mesmo identificador aparece no header, no corpo de erro e no log do servidor.
        response.setHeader("X-Request-Id", request.getAttribute("requestId").toString());
    }
    @Override public void after(HttpServletRequest request, HttpServletResponse response) {
        // Mede o processamento no Servlet, não o tempo até o cliente receber todos os bytes.
        long elapsed = (System.nanoTime() - (long) request.getAttribute("startNanos")) / 1_000_000;
        LoggerFactory.getLogger(LoggingInterceptor.class).info("{} {} -> {} ({} ms) requestId={}",
            request.getMethod(), request.getRequestURI(), response.getStatus(), elapsed, request.getAttribute("requestId"));
    }
}
