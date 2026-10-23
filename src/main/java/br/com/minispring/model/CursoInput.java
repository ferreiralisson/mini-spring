package br.com.minispring.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** DTO de entrada separado do modelo: o cliente não escolhe o ID. */
// O DTO define o contrato de entrada e não possui id. As anotações são metadados:
// é o Validator, chamado pelo dispatcher, que efetivamente verifica essas restrições.
public record CursoInput(@NotBlank String nome, @Positive int cargaHoraria) {}
