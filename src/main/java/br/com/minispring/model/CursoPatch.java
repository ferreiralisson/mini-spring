package br.com.minispring.model;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Atualização parcial: campos ausentes (null) permanecem como estão. */
public record CursoPatch(@Pattern(regexp = ".*\\S.*", message = "não deve estar em branco") String nome, @Positive Integer cargaHoraria) {}
