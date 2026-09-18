package br.com.minispring.exemplo.repository;

import br.com.minispring.framework.annotation.Repository;
import br.com.minispring.exemplo.model.Curso;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public final class InMemoryCursoRepository implements CursoRepository {
    // Beans singleton são compartilhados entre requisições concorrentes.
    private final ConcurrentHashMap<Long, Curso> courses = new ConcurrentHashMap<>();
    // incrementAndGet gera IDs de forma atômica; um simples long++ pode perder incrementos
    // quando duas threads executam ao mesmo tempo. A sequência reinicia junto com a aplicação.
    private final AtomicLong sequence = new AtomicLong();

    public Curso create(String nome, int cargaHoraria) {
        long id = sequence.incrementAndGet();
        var curso = new Curso(id, nome, cargaHoraria);
        courses.put(id, curso);
        return curso;
    }

    // Ordenamos porque ConcurrentHashMap não garante ordem de iteração. Esta listagem
    // não é um snapshot transacional: outras requisições podem modificar o mapa durante a leitura.
    public List<Curso> findAll() {
        return courses.values().stream().sorted(Comparator.comparingLong(Curso::id)).toList();
    }

    public Optional<Curso> findById(long id) {
        return Optional.ofNullable(courses.get(id));
    }

    // computeIfPresent consulta e substitui atomicamente para esta chave. Isso evita
    // recriar um curso removido por outra thread entre uma consulta e uma gravação.
    // Essa garantia por operação não equivale a uma transação envolvendo vários cursos.
    public Optional<Curso> update(long id, String nome, int cargaHoraria) {
        return Optional.ofNullable(courses.computeIfPresent(id, (key, old) -> new Curso(id, nome, cargaHoraria)));
    }

    public boolean delete(long id) {
        return courses.remove(id) != null;
    }
}
