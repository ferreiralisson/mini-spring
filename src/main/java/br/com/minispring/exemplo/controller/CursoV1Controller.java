package br.com.minispring.exemplo.controller;

import br.com.minispring.exemplo.model.*;
import br.com.minispring.exemplo.service.CursoService;
import br.com.minispring.exemplo.web.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/cursos")
public final class CursoV1Controller {
    private final CursoService service;

    public CursoV1Controller(CursoService service) {
        this.service = service;
    }

    @GetMapping
    public CursoPage list(@RequestParam(defaultValue = "") String nome,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(defaultValue = "id,asc") String sort) {
        return service.listPage(nome, page, size, sort);
    }

    @GetMapping("/{id}")
    public Curso get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Curso> create(@Valid @RequestBody CursoInput input) {
        Curso curso = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/cursos/" + curso.id())).body(curso);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Curso update(@PathVariable long id, @Valid @RequestBody CursoInput input) {
        return service.update(id, input);
    }

    @PatchMapping(path = "/{id}", consumes = "application/merge-patch+json")
    public Curso patch(@PathVariable long id, @RequestBody JsonNode body) {
        return service.patch(id, CursoPatchInput.from(body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ApiResponses.options("GET, HEAD, OPTIONS, POST");
    }

    @RequestMapping(path = "/{id}", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> optionsForCourse() {
        return ApiResponses.options("DELETE, GET, HEAD, OPTIONS, PATCH, PUT");
    }
}
