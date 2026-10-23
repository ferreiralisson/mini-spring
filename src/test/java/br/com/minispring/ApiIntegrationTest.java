package br.com.minispring;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.name=Escola de teste", "server.address=127.0.0.1"})
class ApiIntegrationTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private static HttpClient client;
    @LocalServerPort
    private int port;

    @BeforeAll
    static void createClient() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterAll
    static void closeClient() {
        client.close();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return send(method, path, body, body == null ? null : "application/json");
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (contentType != null) builder.header("Content-Type", contentType);
        var request = builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode readJson(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    @Test
    void fullCrudThroughSpringMvc() throws Exception {
        var info = send("GET", "/", null);
        assertEquals("Escola de teste", readJson(info).get("application").asString());
        assertTrue(readJson(info).get("framework").asString().startsWith("Spring Boot "));
        assertEquals("Tomcat", readJson(info).get("server").asString());
        assertEquals(200, send("GET", "/health", null).statusCode());

        String name = "Java-" + UUID.randomUUID();
        var created = send("POST", "/cursos", "{\"nome\":\" " + name + " \",\"cargaHoraria\":40}");
        assertEquals(201, created.statusCode());
        assertTrue(created.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        assertFalse(created.headers().firstValue("X-Request-Id").orElseThrow().isBlank());
        URI location = URI.create(created.headers().firstValue("Location").orElseThrow());
        String path = location.getPath();
        var fetched = send("GET", path, null);
        assertEquals(name, readJson(fetched).get("nome").asString());
        assertTrue(fetched.headers().firstValue("ETag").isPresent());
        assertEquals(1, readJson(send("GET", "/cursos?nome=" + name, null)).size());
        assertEquals(0, readJson(send("GET", "/cursos?nome=Python-" + UUID.randomUUID(), null)).size());

        var updated = send("PUT", path, "{\"nome\":\"Java avançado\",\"cargaHoraria\":80}");
        assertEquals(200, updated.statusCode());
        assertEquals(80, readJson(updated).get("cargaHoraria").asInt());
        var removed = send("DELETE", path, null);
        assertEquals(204, removed.statusCode());
        assertTrue(removed.body().isEmpty());
        assertEquals(404, send("GET", path, null).statusCode());
        assertEquals(404, send("DELETE", path, null).statusCode());
        assertEquals(404, send("PUT", path, "{\"nome\":\"Java\",\"cargaHoraria\":20}").statusCode());
    }

    @Test
    void rejectsInvalidInputsWithStructuredErrors() throws Exception {
        var invalid = send("POST", "/cursos", "{\"nome\":\" \",\"cargaHoraria\":0}");
        assertEquals(400, invalid.statusCode());
        JsonNode error = readJson(invalid);
        assertEquals(2, error.get("details").size());
        assertEquals(invalid.headers().firstValue("X-Request-Id").orElseThrow(), error.get("requestId").asString());

        for (String body : List.of(
                "{broken",
                "null",
                "",
                "{}",
                "{\"nome\":\"Java\",\"cargaHoraria\":1.5}",
                "{\"nome\":\"Java\",\"cargaHoraria\":\"40\"}",
                "{} {}",
                "{\"nome\":\"Java\",\"cargaHoraria\":40,\"id\":7}")) {
            assertEquals(400, send("POST", "/cursos", body).statusCode(), body);
        }
        assertEquals(400, send("GET", "/cursos/abc", null).statusCode());
        assertEquals(422, send("POST", "/cursos", "{\"nome\":\"Java\",\"cargaHoraria\":2001}")
                .statusCode());
        assertEquals(415, send("POST", "/cursos", "{}", "text/plain").statusCode());
        assertEquals(413, send("POST", "/cursos", "x".repeat(1_048_577)).statusCode());
    }

    @Test
    void acceptsCourseHourBoundaries() throws Exception {
        for (int hours : List.of(1, 2000)) {
            var created = send("POST", "/cursos",
            "{\"nome\":\"Limite-" + hours + "-" + UUID.randomUUID() + "\",\"cargaHoraria\":" + hours + "}");
            assertEquals(201, created.statusCode());
            assertEquals(hours, readJson(created).get("cargaHoraria").asInt());
        }
    }

    @Test
    void errorsUseProblemJson() throws Exception {
        var response = send("GET", "/cursos/999999", null);
        assertEquals(404, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
        assertEquals("/cursos/999999", readJson(response).get("instance").asString());
        assertEquals(404, readJson(response).get("status").asInt());
    }

    @Test
    void patchUpdatesOnlyProvidedFields() throws Exception {
        var created = send("POST", "/cursos", "{\"nome\":\"Patch\",\"cargaHoraria\":40}");
        String path = URI.create(created.headers().firstValue("Location").orElseThrow()).getPath();
        var patched = send("PATCH", path, "{\"cargaHoraria\":60}");
        assertEquals(200, patched.statusCode());
        assertEquals("Patch", readJson(patched).get("nome").asString());
        assertEquals(60, readJson(patched).get("cargaHoraria").asInt());

        var nullPatch = send("PATCH", path, "{\"nome\":null}");
        assertEquals(200, nullPatch.statusCode());
        assertEquals("Patch", readJson(nullPatch).get("nome").asString());
        assertEquals(60, readJson(nullPatch).get("cargaHoraria").asInt());

        var emptyPatch = send("PATCH", path, "{}");
        assertEquals(200, emptyPatch.statusCode());
        assertEquals("Patch", readJson(emptyPatch).get("nome").asString());
        assertEquals(60, readJson(emptyPatch).get("cargaHoraria").asInt());

        assertEquals(400, send("PATCH", path, "{\"cargaHoraria\":-1}").statusCode());
        assertEquals(422, send("PATCH", path, "{\"cargaHoraria\":3000}").statusCode());
        assertEquals(404, send("PATCH", "/cursos/999999", "{}").statusCode());
    }

    @Test
    void listIsPaginatedAndResponsesHaveEtag() throws Exception {
        String name = "Pagina-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) send("POST", "/cursos", "{\"nome\":\"" + name + "\",\"cargaHoraria\":10}");
        var page = send("GET", "/cursos?nome=" + name + "&page=1&size=2", null);
        assertEquals(1, readJson(page).size());
        assertEquals("3", page.headers().firstValue("X-Total-Count").orElseThrow());
        assertEquals(400, send("GET", "/cursos?size=0", null).statusCode());
        assertEquals(400, send("GET", "/cursos?size=101", null).statusCode());

        String etag = page.headers().firstValue("ETag").orElseThrow();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/cursos?nome=" + name + "&page=1&size=2"))
                .header("If-None-Match", etag).GET().build();
        assertEquals(304, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void distinguishesUnknownRoutesFromUnsupportedMethods() throws Exception {
        assertEquals(404, send("GET", "/inexistente", null).statusCode());
        var response = send("PUT", "/cursos", "{}");
        assertEquals(405, response.statusCode());
        String allow = response.headers().firstValue("Allow").orElseThrow();
        assertTrue(allow.contains("GET"));
        assertTrue(allow.contains("POST"));
    }

    @Test
    void concurrentRequestsProduceUniqueIds() throws Exception {
        String name = "Concorrente-" + UUID.randomUUID();
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>();
            for (int i = 0; i < 30; i++) {
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/cursos"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"nome\":\"" + name + "\",\"cargaHoraria\":10}"))
                        .build();
                futures.add(http.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
            }
            Set<Long> ids = new HashSet<>();
            for (var future : futures) {
                var response = future.join();
                assertEquals(201, response.statusCode());
                ids.add(json.readTree(response.body()).get("id").asLong());
            }
            assertEquals(30, ids.size());
        }

        assertEquals(30, readJson(send("GET", "/cursos?nome=" + name + "&size=100", null)).size());
    }
}
