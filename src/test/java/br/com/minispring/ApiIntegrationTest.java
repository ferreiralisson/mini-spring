package br.com.minispring;

import br.com.minispring.exemplo.Application;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Application.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiIntegrationTest {
    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int port;

    private HttpClient client;

    @BeforeEach
    void start() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void stop() {
        client.close();
    }

    private HttpRequest request(String method, String path, String body, String contentType) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10));
        if (contentType != null) builder.header("Content-Type", contentType);
        return builder.method(method, body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return send(method, path, body, body == null ? null : "application/json");
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
        return client.send(request(method, path, body, contentType), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void fullCrudAndApplicationInfoThroughSpringBoot() throws Exception {
        JsonNode info = json.readTree(send("GET", "/", null).body());
        assertEquals("Escola Spring Boot", info.get("application").asText());
        assertEquals("Spring Boot", info.get("framework").asText());
        assertEquals("Tomcat", info.get("server").asText());
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
    }

    @Test
    void rejectsInvalidInputsWithStructuredErrors() throws Exception {
        var invalid = send("POST", "/cursos", "{\"nome\":\" \",\"cargaHoraria\":0}");
        assertEquals(400, invalid.statusCode());
        var error = json.readTree(invalid.body());
        assertEquals(2, error.get("details").size());
        assertEquals(invalid.headers().firstValue("X-Request-Id").orElseThrow(), error.get("requestId").asText());

        for (String body : List.of("{broken", "null", "", "{}", "{\"nome\":\"Java\",\"cargaHoraria\":1.5}",
                "{\"nome\":\"Java\",\"cargaHoraria\":\"40\"}", "{} {}",
                "{\"nome\":\"Java\",\"cargaHoraria\":40,\"id\":7}")) {
            assertEquals(400, send("POST", "/cursos", body).statusCode(), body);
        }
        assertEquals(400, send("GET", "/cursos/abc", null).statusCode());
        assertEquals(422, send("POST", "/cursos", "{\"nome\":\"Java\",\"cargaHoraria\":2001}").statusCode());
        assertEquals(415, send("POST", "/cursos", "{}", "text/plain").statusCode());
        assertEquals(413, send("POST", "/cursos", "x".repeat(1_048_577)).statusCode());
    }

    @Test
    void distinguishesNotFoundAndMethodNotAllowed() throws Exception {
        assertEquals(404, send("GET", "/inexistente", null).statusCode());
        var response = send("POST", "/cursos/1", "{}");
        assertEquals(405, response.statusCode());
        assertEquals("DELETE, GET, HEAD, OPTIONS, PATCH, PUT",
            response.headers().firstValue("Allow").orElseThrow());
    }

    @Test
    void patchMergesFieldsAndRejectsInvalidUpdatesAtomically() throws Exception {
        var created = send("POST", "/cursos", "{\"nome\":\"Java\",\"cargaHoraria\":40}");
        String location = created.headers().firstValue("Location").orElseThrow();
        var patched = send("PATCH", location, "{\"nome\":\"Java avançado\"}", "application/merge-patch+json");
        assertEquals(200, patched.statusCode());
        assertEquals("Java avançado", json.readTree(patched.body()).get("nome").asText());
        assertEquals(40, json.readTree(patched.body()).get("cargaHoraria").asInt());

        var tooManyHours = send("PATCH", location, "{\"cargaHoraria\":2001}", "application/merge-patch+json");
        assertEquals(422, tooManyHours.statusCode());
        assertEquals(40, json.readTree(send("GET", location, null).body()).get("cargaHoraria").asInt());

        for (String body : List.of("{\"nome\":null}", "{\"id\":9}", "{\"nome\":\" \"}",
                "{\"cargaHoraria\":1.5}", "[]", "null")) {
            assertEquals(400, send("PATCH", location, body, "application/merge-patch+json").statusCode(), body);
        }
        assertEquals(415, send("PATCH", location, "{\"nome\":\"Nope\"}", "application/json").statusCode());
        assertEquals(404, send("PATCH", "/cursos/999", "{\"nome\":\"Nope\"}",
            "application/merge-patch+json").statusCode());

        var versioned = send("POST", "/api/v1/cursos", "{\"nome\":\"Python\",\"cargaHoraria\":30}");
        String versionedLocation = versioned.headers().firstValue("Location").orElseThrow();
        assertTrue(versionedLocation.startsWith("/api/v1/cursos/"));
        assertEquals(200, send("PATCH", versionedLocation, "{\"cargaHoraria\":35}",
            "application/merge-patch+json").statusCode());
        assertEquals(35, json.readTree(send("GET", versionedLocation, null).body()).get("cargaHoraria").asInt());
        assertEquals(200, send("PUT", versionedLocation,
            "{\"nome\":\"Python avançado\",\"cargaHoraria\":50}").statusCode());
        assertEquals(204, send("DELETE", versionedLocation, null).statusCode());
    }

    @Test
    void versionedListPaginatesAndLegacyListKeepsItsArrayContract() throws Exception {
        send("POST", "/api/v1/cursos", "{\"nome\":\"Zebra\",\"cargaHoraria\":30}");
        send("POST", "/api/v1/cursos", "{\"nome\":\"Alpha\",\"cargaHoraria\":20}");
        send("POST", "/api/v1/cursos", "{\"nome\":\"Alpine\",\"cargaHoraria\":10}");

        var firstPage = json.readTree(send("GET", "/api/v1/cursos", null).body());
        assertEquals(3, firstPage.get("totalElements").asInt());
        assertEquals(1, firstPage.get("totalPages").asInt());
        assertEquals(0, firstPage.get("page").asInt());
        assertEquals(20, firstPage.get("size").asInt());
        assertEquals(1, firstPage.get("items").get(0).get("id").asInt());

        var secondPage = json.readTree(send("GET", "/api/v1/cursos?page=1&size=2", null).body());
        assertEquals(1, secondPage.get("items").size());
        assertEquals(3, secondPage.get("items").get(0).get("id").asInt());
        var sorted = json.readTree(send("GET", "/api/v1/cursos?sort=nome,desc", null).body());
        assertEquals("Zebra", sorted.get("items").get(0).get("nome").asText());
        var filtered = json.readTree(send("GET", "/api/v1/cursos?nome=alp", null).body());
        assertEquals(2, filtered.get("totalElements").asInt());
        var empty = json.readTree(send("GET", "/api/v1/cursos?page=99", null).body());
        assertEquals(0, empty.get("items").size());
        assertEquals(3, empty.get("totalElements").asInt());

        for (String query : List.of("?page=-1", "?size=0", "?size=101", "?sort=unknown,asc",
                "?sort=id,side", "?page=nope")) {
            assertEquals(400, send("GET", "/api/v1/cursos" + query, null).statusCode(), query);
        }
        assertTrue(json.readTree(send("GET", "/cursos", null).body()).isArray());
    }

    @Test
    void headAndOptionsFollowHttpSemantics() throws Exception {
        send("POST", "/cursos", "{\"nome\":\"Java\",\"cargaHoraria\":40}");
        var get = send("GET", "/cursos/1", null);
        var head = send("HEAD", "/cursos/1", null);
        assertEquals(get.statusCode(), head.statusCode());
        assertEquals(get.headers().firstValue("Content-Length"), head.headers().firstValue("Content-Length"));
        assertEquals("", head.body());

        var missingHead = send("HEAD", "/cursos/999", null);
        assertEquals(404, missingHead.statusCode());
        assertEquals("", missingHead.body());
        var options = send("OPTIONS", "/cursos/1", null);
        assertEquals(204, options.statusCode());
        assertEquals("DELETE, GET, HEAD, OPTIONS, PATCH, PUT", options.headers().firstValue("Allow").orElseThrow());

        var collectionOptions = send("OPTIONS", "/api/v1/cursos", null);
        assertEquals(204, collectionOptions.statusCode());
        assertEquals("GET, HEAD, OPTIONS, POST", collectionOptions.headers().firstValue("Allow").orElseThrow());
        assertEquals(200, send("HEAD", "/health", null).statusCode());
        assertEquals(204, send("OPTIONS", "/", null).statusCode());
        assertEquals(404, send("OPTIONS", "/nao-existe", null).statusCode());
    }

    @Test
    void concurrentRequestsProduceUniqueIds() throws Exception {
        var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < 30; i++) {
            futures.add(client.sendAsync(request("POST", "/cursos",
                "{\"nome\":\"Curso\",\"cargaHoraria\":10}", "application/json"),
                HttpResponse.BodyHandlers.ofString()));
        }
        var ids = new HashSet<Long>();
        for (var future : futures) {
            var response = future.join();
            assertEquals(201, response.statusCode());
            ids.add(json.readTree(response.body()).get("id").asLong());
        }
        assertEquals(30, ids.size());
        assertEquals(30, json.readTree(send("GET", "/cursos", null).body()).size());
    }

    @Test
    void concurrentPatchesPreserveChangesToDifferentFields() throws Exception {
        var created = send("POST", "/cursos", "{\"nome\":\"Java\",\"cargaHoraria\":40}");
        String location = created.headers().firstValue("Location").orElseThrow();
        var namePatch = client.sendAsync(request("PATCH", location, "{\"nome\":\"Java concorrente\"}",
            "application/merge-patch+json"), HttpResponse.BodyHandlers.ofString());
        var hoursPatch = client.sendAsync(request("PATCH", location, "{\"cargaHoraria\":80}",
            "application/merge-patch+json"), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, namePatch.join().statusCode());
        assertEquals(200, hoursPatch.join().statusCode());
        var current = json.readTree(send("GET", location, null).body());
        assertEquals("Java concorrente", current.get("nome").asText());
        assertEquals(80, current.get("cargaHoraria").asInt());
    }
}
