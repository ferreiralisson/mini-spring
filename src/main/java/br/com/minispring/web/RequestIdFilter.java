package br.com.minispring.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final int MAX_BODY_SIZE = 1_048_576;
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private final ObjectMapper objectMapper;

    public RequestIdFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        long startedAt = System.nanoTime();
        try {
            HttpServletRequest requestWithBody = bufferBody(request, response);
            if (requestWithBody != null) filterChain.doFilter(requestWithBody, response);
        } finally {
            log.info("{} {} -> {} ({} ms) requestId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    (System.nanoTime() - startedAt) / 1_000_000,
                    requestId);
        }
    }

    private HttpServletRequest bufferBody(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!request.getMethod().equals("POST")
                && !request.getMethod().equals("PUT")
                && !request.getMethod().equals("PATCH")) {
            return request;
        }
        if (request.getContentLengthLong() > MAX_BODY_SIZE) {
            writeTooLarge(request, response);
            return null;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_SIZE + 1);
        if (body.length > MAX_BODY_SIZE) {
            writeTooLarge(request, response);
            return null;
        }
        return new CachedBodyRequest(request, body);
    }

    private void writeTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        response.setContentType("application/problem+json");
        var problem = org.springframework.http.ProblemDetail.forStatusAndDetail(
                HttpStatus.CONTENT_TOO_LARGE, "Corpo da requisição excede 1 MiB");
        problem.setTitle("Content Too Large");
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", java.time.Instant.now());
        problem.setProperty("details", java.util.List.of());
        problem.setProperty("requestId", request.getAttribute(ATTRIBUTE));
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Leitura não bloqueante não é suportada");
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding() == null ? "UTF-8" : getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }
}
