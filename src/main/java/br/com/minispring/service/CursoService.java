package br.com.minispring.service;

import br.com.minispring.model.*;
import br.com.minispring.repository.CursoRepository;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public final class CursoService {
    private final CursoRepository repository;

    // Dependemos da interface, permitindo trocar o armazenamento ou usar um fake nos testes.
    // A implementação concreta é escolhida pelo container durante a inicialização.
    public CursoService(CursoRepository repository) {
        this.repository = repository;
    }

    public Page list(String nome, int page, int size) {
        // Normalizar os dois lados permite busca sem distinguir maiúsculas de minúsculas.
        // Locale.ROOT evita que a regra varie conforme o idioma configurado na máquina.
        String filter = nome.toLowerCase(Locale.ROOT);
        List<Curso> filtered = repository.findAll().stream()
                .filter(c -> c.nome().toLowerCase(Locale.ROOT).contains(filter)).toList();
        int from = (int) Math.min((long) page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new Page(filtered.subList(from, to), filtered.size());
    }

    public record Page(List<Curso> items, long total) {}

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

    public Curso patch(long id, CursoPatch patch) {
        Curso current = get(id);
        CursoInput merged = new CursoInput(
                patch.nome() == null ? current.nome() : patch.nome(),
                patch.cargaHoraria() == null ? current.cargaHoraria() : patch.cargaHoraria());
        return update(id, merged);
    }

    public void delete(long id) {
        if (!repository.delete(id)) throw notFound();
    }

    // Esta regra pertence ao domínio; @NotBlank e @Positive validam a forma da entrada.
    // A validação do DTO só ocorre automaticamente no pipeline HTTP, não em chamadas diretas.
    private void validateBusinessRule(CursoInput input) {
        if (input.cargaHoraria() > 2000) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Um curso pode ter no máximo 2000 horas");
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Curso não encontrado");
    }
}
