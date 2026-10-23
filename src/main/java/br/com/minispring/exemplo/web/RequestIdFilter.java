package br.com.minispring.exemplo.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public final class RequestIdFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_ATTRIBUTE = "requestId";
    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final Logger logger = LoggerFactory.getLogger(RequestIdFilter.class);

    private final ObjectMapper json;

    public RequestIdFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader("X-Request-Id", requestId);
        long started = System.nanoTime();
        try {
            HttpServletRequest requestWithBody = cacheBoundedBody(request, response);
            if (requestWithBody == null) return;
            chain.doFilter(requestWithBody, response);
        } finally {
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            logger.info("{} {} -> {} ({} ms) requestId={}", request.getMethod(), request.getRequestURI(),
                response.getStatus(), elapsedMillis, requestId);
        }
    }

    private HttpServletRequest cacheBoundedBody(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!List.of("POST", "PUT", "PATCH").contains(request.getMethod().toUpperCase(Locale.ROOT))) return request;
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            writeTooLarge(request, response);
            return null;
        }

        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            writeTooLarge(request, response);
            return null;
        }
        return new CachedBodyRequest(request, body);
    }

    private void writeTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiError error = ApiError.from(request, 413, "Corpo excede 1 MiB", List.of());
        byte[] body = json.writeValueAsBytes(error);
        response.setStatus(413);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
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
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("A API não oferece leitura HTTP assíncrona");
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding() == null ? StandardCharsets.UTF_8.name() : getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }
}
