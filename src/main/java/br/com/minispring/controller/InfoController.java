package br.com.minispring.controller;

import java.util.Map;
import org.springframework.boot.SpringBootVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class InfoController {
    private final String appName;

    public InfoController(@Value("${app.name}") String appName) {
        this.appName = appName;
    }

    @GetMapping("/")
    public Map<String, String> info() {
        return Map.of(
                "application", appName,
                "framework", "Spring Boot " + SpringBootVersion.getVersion(),
                "server", "Tomcat");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
