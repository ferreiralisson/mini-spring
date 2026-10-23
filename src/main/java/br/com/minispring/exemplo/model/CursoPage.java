package br.com.minispring.exemplo.model;

import java.util.List;

public record CursoPage(List<Curso> items, int page, int size, long totalElements, int totalPages) {}
