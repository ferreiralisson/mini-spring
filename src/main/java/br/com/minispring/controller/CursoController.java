package br.com.minispring.controller;

import br.com.minispring.model.Curso;
import br.com.minispring.model.CursoInput;
import br.com.minispring.model.CursoPatch;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import br.com.minispring.service.CursoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/cursos")
public final class CursoController {
    private final CursoService service;

    public CursoController(CursoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<Curso>> list(
            @RequestParam(defaultValue = "") String nome,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = service.list(nome, page, size);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.total()))
                .body(result.items());
    }

    @GetMapping("/{id}")
    public Curso get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<Curso> create(@Valid @RequestBody CursoInput input) {
        if (input == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corpo JSON inválido");
        }
        Curso curso = service.create(input);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(curso.id()).toUri()).body(curso);
    }

    @PutMapping("/{id}")
    public Curso update(@PathVariable long id, @Valid @RequestBody CursoInput input) {
        if (input == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corpo JSON inválido");
        }
        return service.update(id, input);
    }

    @PatchMapping("/{id}")
    public Curso patch(@PathVariable long id, @Valid @RequestBody CursoPatch patch) {
        return service.patch(id, patch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
