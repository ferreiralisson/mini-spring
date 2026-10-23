package br.com.minispring.exemplo.repository;

import br.com.minispring.exemplo.model.Curso;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * A aplicação depende de uma abstração; o container encontra a implementação.
 */
public interface CursoRepository {
    Curso create(String nome, int cargaHoraria);

    List<Curso> findAll();

    // Optional torna a ausência explícita. O service decide o significado dessa ausência
    // para o caso de uso, em vez de o repository escolher uma resposta HTTP.
    Optional<Curso> findById(long id);

    Optional<Curso> update(long id, String nome, int cargaHoraria);

    Optional<Curso> patch(long id, UnaryOperator<Curso> update);

    boolean delete(long id);
}
