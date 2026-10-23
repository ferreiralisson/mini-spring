package br.com.minispring.exemplo.controller;

import br.com.minispring.framework.annotation.*;
import java.util.Map;

@RestController
public final class InfoController {
    private final String appName;
    // @Value injeta configuração na construção do bean, não um parâmetro HTTP.
    // O nome é resolvido uma vez ao iniciar esta aplicação.
    public InfoController(@Value("app.name") String appName) { this.appName = appName; }

    @Route(method = "GET", path = "/")
    public Map<String, String> info() { return Map.of("application", appName, "framework", "Mini Spring", "server", "Jetty"); }

    // Este health check comprova que a aplicação responde; não testa banco ou outros serviços.
    @Route(method = "GET", path = "/health")
    public Map<String, String> health() { return Map.of("status", "UP"); }
}
