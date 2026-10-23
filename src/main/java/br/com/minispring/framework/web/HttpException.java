package br.com.minispring.framework.web;

import java.util.List;

// Representa uma falha que o dispatcher sabe traduzir para HTTP.
// Lançar esta exceção não escreve na conexão: a escrita continua centralizada no Servlet.
public final class HttpException extends RuntimeException {
    private final int status;
    private final List<String> details;
    public HttpException(int status, String message) { this(status, message, List.of()); }
    public HttpException(int status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = List.copyOf(details);
    }
    public int status() { return status; }
    public List<String> details() { return details; }
}
