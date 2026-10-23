package br.com.minispring.exemplo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CursoInput(
    @NotBlank(message = "não pode estar em branco") String nome,
    @Positive(message = "deve ser positivo") int cargaHoraria
) {}
