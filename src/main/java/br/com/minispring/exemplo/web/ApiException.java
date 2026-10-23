package br.com.minispring.exemplo.web;

import java.util.List;

public final class ApiException extends RuntimeException {
    private final int status;
    private final List<String> details;

    public ApiException(int status, String message) {
        this(status, message, List.of());
    }

    public ApiException(int status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = List.copyOf(details);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(400, "Falha de validação", List.of(detail));
    }

    public int status() {
        return status;
    }

    public List<String> details() {
        return details;
    }
}
