package br.com.minispring;

import br.com.minispring.framework.MiniApplication;
import br.com.minispring.framework.context.AppConfig;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class ApiIntegrationTest {
    private MiniApplication app;
    private HttpClient client;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void start() throws Exception {
        var properties = new Properties();
        properties.setProperty("app.name", "Escola de teste");
        app = MiniApplication.start("br.com.minispring.exemplo", new AppConfig(properties, Map.of()), 0);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
    @AfterEach void stop() throws Exception {
        if (client != null) client.close();
        if (app != null) app.close();
    }
    private HttpRequest request(String method, String path, String body, String contentType) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).timeout(Duration.ofSeconds(10));
        if (contentType != null) builder.header("Content-Type", contentType);
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return client.send(request(method, path, body, body == null ? null : "application/json"), HttpResponse.BodyHandlers.ofString());
    }
    @Test void fullCrudThroughRealJetty() throws Exception {
        assertEquals("Escola de teste", json.readTree(send("GET", "/", null).body()).get("application").asText());
        assertEquals(200, send("GET", "/health", null).statusCode());
        var created = send("POST", "/cursos", "{\"nome\":\" Java básico \",\"cargaHoraria\":40}");
        assertEquals(201, created.statusCode());
        assertTrue(created.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        assertFalse(created.headers().firstValue("X-Request-Id").orElseThrow().isBlank());
        String location = created.headers().firstValue("Location").orElseThrow();
        assertEquals("Java básico", json.readTree(send("GET", location, null).body()).get("nome").asText());
        assertEquals(1, json.readTree(send("GET", "/cursos?nome=JAVA", null).body()).size());
        assertEquals(0, json.readTree(send("GET", "/cursos?nome=python", null).body()).size());
        var updated = send("PUT", location, "{\"nome\":\"Java avançado\",\"cargaHoraria\":80}");
        assertEquals(200, updated.statusCode());
        assertEquals(80, json.readTree(updated.body()).get("cargaHoraria").asInt());
        var removed = send("DELETE", location, null);
        assertEquals(204, removed.statusCode());
        assertEquals("", removed.body());
        assertEquals(404, send("GET", location, null).statusCode());
        assertEquals(404, send("DELETE", location, null).statusCode());
        assertEquals(404, send("PUT", location, "{\"nome\":\"Java\",\"cargaHoraria\":20}").statusCode());
    }
    @Test void rejectsInvalidInputsWithStructuredErrors() throws Exception {
        var invalid = send("POST", "/cursos", "{\"nome\":\" \",\"cargaHoraria\":0}");
        assertEquals(400, invalid.statusCode());
        var error = json.readTree(invalid.body());
        assertEquals(2, error.get("details").size());
        assertEquals(invalid.headers().firstValue("X-Request-Id").orElseThrow(), error.get("requestId").asText());
        for (String body : List.of("{broken", "null", "", "{}", "{\"nome\":\"Java\",\"cargaHoraria\":1.5}",
                "{\"nome\":\"Java\",\"cargaHoraria\":\"40\"}", "{} {}", "{\"nome\":\"Java\",\"cargaHoraria\":40,\"id\":7}")) {
            assertEquals(400, send("POST", "/cursos", body).statusCode(), body);
        }
        assertEquals(400, send("GET", "/cursos/abc", null).statusCode());
        assertEquals(422, send("POST", "/cursos", "{\"nome\":\"Java\",\"cargaHoraria\":2001}").statusCode());
        assertEquals(415, client.send(request("POST", "/cursos", "{}", "text/plain"), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(413, send("POST", "/cursos", "x".repeat(1_048_577)).statusCode());
    }
    @Test void distinguishesRouteErrorsAndSendsAllowHeader() throws Exception {
        assertEquals(404, send("GET", "/inexistente", null).statusCode());
        var response = send("PATCH", "/cursos/1", "{}");
        assertEquals(405, response.statusCode());
        assertEquals("DELETE, GET, PUT", response.headers().firstValue("Allow").orElseThrow());
    }
    @Test void concurrentRequestsProduceUniqueIds() throws Exception {
        var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < 30; i++) futures.add(client.sendAsync(
            request("POST", "/cursos", "{\"nome\":\"Curso\",\"cargaHoraria\":10}", "application/json"), HttpResponse.BodyHandlers.ofString()));
        var ids = new HashSet<Long>();
        for (var future : futures) {
            var response = future.join();
            assertEquals(201, response.statusCode());
            ids.add(json.readTree(response.body()).get("id").asLong());
        }
        assertEquals(30, ids.size());
        assertEquals(30, json.readTree(send("GET", "/cursos", null).body()).size());
    }
}
