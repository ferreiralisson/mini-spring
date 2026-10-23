package br.com.minispring.exemplo.service;

import br.com.minispring.exemplo.web.ApiException;
import br.com.minispring.exemplo.model.*;
import br.com.minispring.exemplo.repository.CursoRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public final class CursoService {
    private final CursoRepository repository;
    private final Validator validator;

    public CursoService(CursoRepository repository, Validator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public List<Curso> list(String nome) {
        // Normalizar os dois lados permite busca sem distinguir maiúsculas de minúsculas.
        // Locale.ROOT evita que a regra varie conforme o idioma configurado na máquina.
        String filter = nome.toLowerCase(Locale.ROOT);
        return repository.findAll().stream().filter(c -> c.nome().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    public CursoPage listPage(String nome, int page, int size, String sort) {
        if (page < 0) throw ApiException.badRequest("Parâmetro inválido: page");
        if (size < 1 || size > 100) throw ApiException.badRequest("Parâmetro inválido: size");

        String[] sortParts = sort.split(",", -1);
        if (sortParts.length != 2) throw ApiException.badRequest("Parâmetro inválido: sort");
        String field = sortParts[0].trim();
        String direction = sortParts[1].trim().toLowerCase(Locale.ROOT);
        if (!Set.of("id", "nome", "cargaHoraria").contains(field)
                || !Set.of("asc", "desc").contains(direction))
            throw ApiException.badRequest("Parâmetro inválido: sort");

        Comparator<Curso> comparator = switch (field) {
            case "id" -> Comparator.comparingLong(Curso::id);
            case "nome" -> Comparator.comparing(Curso::nome, String.CASE_INSENSITIVE_ORDER);
            case "cargaHoraria" -> Comparator.comparingInt(Curso::cargaHoraria);
            default -> throw new IllegalStateException("Campo de ordenação validado sem comparator");
        };
        if (direction.equals("desc")) comparator = comparator.reversed();
        if (!field.equals("id")) comparator = comparator.thenComparingLong(Curso::id);

        String filter = nome.toLowerCase(Locale.ROOT);
        List<Curso> filtered = repository.findAll().stream()
            .filter(c -> c.nome().toLowerCase(Locale.ROOT).contains(filter))
            .sorted(comparator)
            .toList();
        long totalElements = filtered.size();
        int totalPages = (int) ((totalElements + size - 1) / size);
        long offset = (long) page * size;
        List<Curso> items = offset >= totalElements
            ? List.of()
            : filtered.subList((int) offset, (int) Math.min(offset + size, totalElements));
        return new CursoPage(items, page, size, totalElements, totalPages);
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

    public Curso patch(long id, CursoPatchInput patch) {
        return repository.patch(id, current -> {
            String nome = patch.nome() == null ? current.nome() : patch.nome().trim();
            int cargaHoraria = patch.cargaHoraria() == null ? current.cargaHoraria() : patch.cargaHoraria();
            CursoInput merged = new CursoInput(nome, cargaHoraria);
            var violations = validator.validate(merged);
            if (!violations.isEmpty()) {
                List<String> details = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .toList();
                throw new ApiException(400, "Falha de validação", details);
            }
            validateBusinessRule(merged);
            return new Curso(current.id(), nome, cargaHoraria);
        }).orElseThrow(this::notFound);
    }

    public void delete(long id) {
        if (!repository.delete(id)) throw notFound();
    }

    // Esta regra pertence ao domínio; @NotBlank e @Positive validam a forma da entrada.
    // A validação do DTO só ocorre automaticamente no pipeline HTTP, não em chamadas diretas.
    private void validateBusinessRule(CursoInput input) {
        if (input.cargaHoraria() > 2000)
            throw new ApiException(422, "Um curso pode ter no máximo 2000 horas");
    }

    private ApiException notFound() {
        return new ApiException(404, "Curso não encontrado");
    }
}
