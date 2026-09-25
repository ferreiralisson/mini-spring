package br.com.minispring;

import br.com.minispring.framework.MiniApplication;
import br.com.minispring.framework.context.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UnexpectedErrorIntegrationTest {
    @Test void unexpectedFailureReturnsJsonWithoutLeakingImplementationDetails() throws Exception {
        try (var app = MiniApplication.start("br.com.minispring.failurefixture", new AppConfig(new Properties(), Map.of()), 0);
             var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/failure"))
                .timeout(Duration.ofSeconds(5)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(500, response.statusCode());
            var body = new ObjectMapper().readTree(response.body());
            assertEquals("Erro interno do servidor", body.get("message").asText());
            assertFalse(response.body().contains("detalhe-interno-do-teste"));
            assertEquals(response.headers().firstValue("X-Request-Id").orElseThrow(), body.get("requestId").asText());
        }
    }
}
