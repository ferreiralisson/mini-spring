package br.com.minispring.exemplo.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.TreeSet;

@RestControllerAdvice
public final class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
        return error(request, exception.status(), exception.getMessage(), exception.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
                                                       HttpServletRequest request) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
            .map(this::validationMessage)
            .toList();
        return error(request, 400, "Falha de validação", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpServletRequest request) {
        return error(request, 400, "JSON inválido ou incompatível com os campos esperados", List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleInvalidParameter(Exception exception, HttpServletRequest request) {
        String parameter = exception instanceof MethodArgumentTypeMismatchException mismatch
            ? mismatch.getName()
            : ((MissingServletRequestParameterException) exception).getParameterName();
        return error(request, 400, "Parâmetro inválido: " + parameter, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpServletRequest request) {
        return error(request, 415, "Tipo de mídia não suportado", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
                                                            HttpServletRequest request) {
        var allowed = new TreeSet<String>();
        if (exception.getSupportedHttpMethods() != null) {
            exception.getSupportedHttpMethods().forEach(method -> allowed.add(method.name()));
        }
        if (allowed.contains("GET")) allowed.add("HEAD");
        if (!allowed.isEmpty()) allowed.add("OPTIONS");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .header(HttpHeaders.ALLOW, String.join(", ", allowed))
            .body(ApiError.from(request, 405, "Método HTTP não permitido", List.of()));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(HttpServletRequest request) {
        return error(request, 404, "Rota não encontrada", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        logger.error("Falha na requisição {}", request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE), exception);
        return error(request, 500, "Erro interno do servidor", List.of());
    }

    private String validationMessage(FieldError error) {
        String message = error.getDefaultMessage();
        return error.getField() + " " + (message == null ? "é inválido" : message);
    }

    private ResponseEntity<ApiError> error(HttpServletRequest request, int status, String message,
                                            List<String> details) {
        return ResponseEntity.status(status).body(ApiError.from(request, status, message, details));
    }
}
