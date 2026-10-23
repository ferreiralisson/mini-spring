package br.com.minispring.exemplo.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

public final class ApiResponses {
    private ApiResponses() {}

    public static ResponseEntity<Void> options(String allow) {
        return ResponseEntity.noContent().header(HttpHeaders.ALLOW, allow).build();
    }
}
