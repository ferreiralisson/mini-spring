package br.com.minispring.exemplo.service;

import br.com.minispring.framework.annotation.Service;
import br.com.minispring.framework.web.HttpException;
import br.com.minispring.exemplo.model.*;
import br.com.minispring.exemplo.repository.CursoRepository;

import java.util.List;
import java.util.Locale;

@Service
public final class CursoService {
    private final CursoRepository repository;

    // Dependemos da interface, permitindo trocar o armazenamento ou usar um fake nos testes.
    // A implementação concreta é escolhida pelo container durante a inicialização.
    public CursoService(CursoRepository repository) {
        this.repository = repository;
    }

    public List<Curso> list(String nome) {
        // Normalizar os dois lados permite busca sem distinguir maiúsculas de minúsculas.
        // Locale.ROOT evita que a regra varie conforme o idioma configurado na máquina.
        String filter = nome.toLowerCase(Locale.ROOT);
        return repository.findAll().stream().filter(c -> c.nome().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    public Curso get(long id) {
        return repository.findById(id).orElseThrow(this::notFound);
    }

    public Curso create(CursoInput input) {
        validateBusinessRule(input);
        return repository.create(input.nome().trim(), input.cargaHoraria());
    }

    public Curso update(long id, CursoInput input) {
        validateBusinessRule(input);
        return repository.update(id, input.nome().trim(), input.cargaHoraria()).orElseThrow(this::notFound);
    }

    public void delete(long id) {
        if (!repository.delete(id)) throw notFound();
    }

    // Esta regra pertence ao domínio; @NotBlank e @Positive validam a forma da entrada.
    // A validação do DTO só ocorre automaticamente no pipeline HTTP, não em chamadas diretas.
    private void validateBusinessRule(CursoInput input) {
        if (input.cargaHoraria() > 2000) throw new HttpException(422, "Um curso pode ter no máximo 2000 horas");
    }

    // Simplificação didática: o service conhece HTTP. Uma evolução é lançar exceções
    // de domínio e traduzi-las para status apenas na camada web.
    private HttpException notFound() {
        return new HttpException(404, "Curso não encontrado");
    }
}
