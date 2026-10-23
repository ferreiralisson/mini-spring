package br.com.minispring;

import br.com.minispring.framework.annotation.*;
import br.com.minispring.framework.web.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RouterTest {
    @RestController("/items") public static class Routes {
        @Route(method="GET", path="/{id}") public String item(@PathVariable("id") long id) { return "item"; }
        @Route(method="GET", path="/resumo") public String summary() { return "summary"; }
    }
    @RestController("/items") public static class Duplicate {
        @Route(method="GET", path="/{other}") public String item(@PathVariable("other") long id) { return "duplicate"; }
    }
    @RestController public static class Invalid {
        @Route(method="GET", path="/bad") public String bad(String missingAnnotation) { return ""; }
    }
    @Test void literalRouteHasPriority() {
        var router = new Router(List.of(new Routes()));
        assertEquals("summary", router.match("GET", "/items/resumo").method().getName());
        assertEquals("42", router.match("GET", "/items/42/").variables().get("id"));
    }
    @Test void distinguishesNotFoundAndMethodNotAllowed() {
        var router = new Router(List.of(new Routes()));
        assertEquals(404, assertThrows(HttpException.class, () -> router.match("GET", "/unknown")).status());
        assertEquals(405, assertThrows(HttpException.class, () -> router.match("POST", "/items/1")).status());
    }
    @Test void rejectsDuplicateTemplatesEvenWithDifferentVariableNames() {
        assertThrows(IllegalStateException.class, () -> new Router(List.of(new Routes(), new Duplicate())));
    }
    @Test void rejectsUnboundParametersAtStartup() {
        assertThrows(IllegalStateException.class, () -> new Router(List.of(new Invalid())));
    }
}
