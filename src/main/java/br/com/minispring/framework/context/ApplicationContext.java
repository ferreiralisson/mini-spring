package br.com.minispring.framework.context;

import br.com.minispring.framework.annotation.Value;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;

/** Container IoC: um singleton por classe, com injeção pelo único construtor público. */
public final class ApplicationContext {
    // Definição é uma classe elegível; bean é a instância criada a partir dela.
    // O cache abaixo implementa singleton por contexto, não por variável static global.
    private final Set<Class<?>> definitions;
    private final Map<Class<?>, Object> singletons = new LinkedHashMap<>();
    // Esta pilha acompanha apenas a construção em andamento e permite enxergar ciclos.
    private final Deque<Class<?>> creating = new ArrayDeque<>();
    private final AppConfig config;

    public ApplicationContext(Set<Class<?>> definitions, AppConfig config) {
        this.definitions = new LinkedHashSet<>(definitions);
        this.config = config;
        // Criação antecipada: erros de configuração aparecem antes de abrir a porta HTTP.
        this.definitions.stream().sorted(Comparator.comparing(Class::getName)).forEach(this::getBean);
    }

    // synchronized protege a criação/cache. O monitor é reentrante: a mesma thread
    // pode chamar getBean novamente para resolver as dependências do construtor.
    // Isso não torna os métodos dos beans automaticamente seguros para concorrência.
    public synchronized <T> T getBean(Class<T> requestedType) {
        // Se foi solicitada CursoRepository, isAssignableFrom encontra uma classe que
        // implementa essa interface. Não escolhemos silenciosamente entre duas implementações.
        var candidates = definitions.stream().filter(requestedType::isAssignableFrom).toList();
        if (candidates.isEmpty()) throw new IllegalStateException("Dependência não registrada: " + requestedType.getName());
        if (candidates.size() > 1) throw new IllegalStateException("Dependência ambígua: " + requestedType.getName() + " -> " + candidates);
        Class<?> type = candidates.getFirst();
        // Dependências diferentes que pedem o mesmo componente recebem o mesmo objeto.
        if (singletons.containsKey(type)) return requestedType.cast(singletons.get(type));
        // Pedir novamente um tipo ainda em construção significa A → B → A.
        // Sem esta verificação, a recursão continuaria até esgotar a pilha da JVM.
        if (creating.contains(type)) throw new IllegalStateException("Dependência circular: " + creating + " -> " + type.getSimpleName());
        creating.addLast(type);
        try {
            var constructors = type.getConstructors();
            if (constructors.length != 1) throw new IllegalStateException("Componente deve ter um único construtor público: " + type.getName());
            var constructor = constructors[0];
            // Primeiro construímos as dependências; depois chamamos o construtor do componente.
            // Este é o ponto da DI: argumentos resolvidos externamente são entregues ao objeto.
            Object[] arguments = Arrays.stream(constructor.getParameters()).map(this::resolve).toArray();
            // Reflection equivale a executar new com a classe e os argumentos descobertos em runtime.
            Object bean = constructor.newInstance(arguments);
            singletons.put(type, bean);
            return requestedType.cast(bean);
        // Reflection encapsula uma exceção lançada pelo construtor; a causa preserva o erro real.
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Construtor falhou: " + type.getName(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Não foi possível criar " + type.getName(), e);
        } finally {
            // Mesmo se a construção falhar, o tipo precisa sair da pilha de criação.
            creating.removeLast();
        }
    }

    private Object resolve(Parameter parameter) {
        // Um parâmetro do construtor vem de configuração (@Value) ou de outro bean.
        // Isso é diferente dos argumentos HTTP, que são resolvidos a cada requisição.
        var value = parameter.getAnnotation(Value.class);
        if (value == null) return getBean(parameter.getType());
        String raw = config.get(value.value());
        if (parameter.getType() == String.class) return raw;
        if (parameter.getType() == int.class || parameter.getType() == Integer.class) return Integer.valueOf(raw);
        throw new IllegalStateException("@Value suporta String e int: " + parameter);
    }

    // A cópia impede que quem recebe a coleção remova ou acrescente beans no cache.
    public Collection<Object> beans() { return List.copyOf(singletons.values()); }
}
