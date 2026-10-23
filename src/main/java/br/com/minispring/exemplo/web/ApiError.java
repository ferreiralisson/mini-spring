package br.com.minispring.exemplo.web;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ApiError(String timestamp, int status, String message, String path,
                       List<String> details, String requestId) {
    public ApiError {
        details = List.copyOf(details);
    }

    public static ApiError from(HttpServletRequest request, int status, String message, List<String> details) {
        return new ApiError(Instant.now().toString(), status, message, request.getRequestURI(), details,
            Objects.toString(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE), ""));
    }
}
