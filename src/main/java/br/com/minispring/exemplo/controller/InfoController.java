package br.com.minispring.exemplo.controller;

import br.com.minispring.exemplo.web.ApiResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public final class InfoController {
    private final String appName;
    public InfoController(@Value("${app.name}") String appName) { this.appName = appName; }

    @GetMapping({"/", ""})
    public Map<String, String> info() { return Map.of("application", appName, "framework", "Spring Boot", "server", "Tomcat"); }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "UP"); }

    @RequestMapping(path = {"/", ""}, method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ApiResponses.options("GET, HEAD, OPTIONS");
    }

    @RequestMapping(path = "/health", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> healthOptions() {
        return ApiResponses.options("GET, HEAD, OPTIONS");
    }
}
