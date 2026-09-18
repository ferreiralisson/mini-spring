package br.com.minispring.exemplo.controller;

import br.com.minispring.framework.annotation.*;
import br.com.minispring.framework.web.HttpResult;
import br.com.minispring.exemplo.model.*;
import br.com.minispring.exemplo.service.CursoService;
import java.util.List;

@RestController("/cursos")
public final class CursoController {
    private final CursoService service;
    // DI por construtor: o container fornece o service. O controller não escolhe
    // como construí-lo, e a dependência fica explícita para quem lê ou testa a classe.
    public CursoController(CursoService service) { this.service = service; }

    // GET /cursos?nome=java. Sem query param, o default vazio lista todos os cursos.
    // Retornamos objetos Java; o dispatcher usa Jackson para produzir JSON.
    @Route(method = "GET")
    public List<Curso> list(@RequestParam("nome") String nome) { return service.list(nome); }

    // O "id" da anotação corresponde ao {id} da rota. O dispatcher converte texto em long.
    @Route(method = "GET", path = "/{id}")
    public Curso get(@PathVariable("id") long id) { return service.get(id); }

    // Binding por requisição: input vem do JSON, não do container IoC.
    // A validação das anotações do DTO já ocorreu quando este método começa.
    @Route(method = "POST")
    public HttpResult create(@RequestBody CursoInput input) {
        Curso curso = service.create(input);
        return HttpResult.created("/cursos/" + curso.id(), curso);
    }

    // Uma mesma chamada combina dados da URL (identidade) e do corpo (novos valores).
    @Route(method = "PUT", path = "/{id}")
    public Curso update(@PathVariable("id") long id, @RequestBody CursoInput input) {
        return service.update(id, input);
    }

    // Não há representação para devolver após excluir; usamos explicitamente 204 sem corpo.
    @Route(method = "DELETE", path = "/{id}")
    public HttpResult delete(@PathVariable("id") long id) {
        service.delete(id);
        return HttpResult.noContent();
    }
}
