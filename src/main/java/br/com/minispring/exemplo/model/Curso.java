package br.com.minispring.exemplo.model;

// Um record gera construtor, accessors, equals/hashCode e toString. Como seus campos
// aqui são primitivas e String, este modelo é imutável; atualizar significa criar outro Curso.
public record Curso(long id, String nome, int cargaHoraria) {}
