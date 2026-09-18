package br.com.minispring.framework.web;

import br.com.minispring.framework.annotation.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Pattern;

/** Registro de rotas imutável após o bootstrap; templates viram expressões regulares. */
public final class Router {
    // Match liga uma requisição ao bean já existente, ao método Java e aos valores da URL.
    // Endpoint guarda os metadados preparados no bootstrap; não é recriado por requisição.
    public record Match(Object controller, Method method, Map<String, String> variables) {}
    private record Endpoint(String verb, String template, Pattern pattern, List<String> names,
                            Object controller, Method method, int literals) {}
    private final List<Endpoint> endpoints = new ArrayList<>();

    public Router(Collection<Object> beans) {
        var signatures = new HashSet<String>();
        for (Object bean : beans) {
            // O container contém vários tipos de bean; só controllers publicam rotas HTTP.
            var annotation = bean.getClass().getAnnotation(RestController.class);
            if (annotation == null) continue;
            for (Method method : bean.getClass().getDeclaredMethods()) {
                var route = method.getAnnotation(Route.class);
                if (route == null) continue;
                if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()))
                    throw new IllegalStateException("Método de rota deve ser público e de instância: " + method);
                // Prefixo do controller + caminho do método: /cursos + /{id} = /cursos/{id}.
                String template = normalize(annotation.value() + route.path());
                String verb = route.method().toUpperCase(Locale.ROOT);
                var names = new ArrayList<String>();
                var regex = new StringBuilder("^");
                int literals = 0;
                if (!template.equals("/")) {
                    for (String segment : template.substring(1).split("/")) {
                        regex.append('/');
                        if (segment.matches("\\{[a-zA-Z][a-zA-Z0-9_]*}")) {
                            String name = segment.substring(1, segment.length() - 1);
                            if (names.contains(name)) throw new IllegalStateException("Variável repetida em " + template);
                            names.add(name);
                            // Cada variável captura um segmento. /cursos/42 gera o par id → "42".
                            // A conversão para long acontece depois, no binding do dispatcher.
                            regex.append("([^/]+)");
                        } else {
                            // Um ponto ou outro símbolo no caminho literal não deve virar um operador de regex.
                            regex.append(Pattern.quote(segment));
                            literals++;
                        }
                    }
                } else regex.append('/');
                regex.append('$');
                // Usar a regex como assinatura detecta inclusive /{id} e /{codigo} como duplicatas
                // para o mesmo método HTTP: o nome da variável não muda o formato da rota.
                if (!signatures.add(verb + " " + regex)) throw new IllegalStateException("Rota duplicada: " + verb + " " + template);
                validateParameters(method, names);
                endpoints.add(new Endpoint(verb, template, Pattern.compile(regex.toString()), List.copyOf(names), bean, method, literals));
            }
        }
        // Rotas estáticas ganham de templates: /cursos/resumo antes de /cursos/{id}.
        endpoints.sort(Comparator.comparingInt(Endpoint::literals).reversed().thenComparing(Endpoint::template));
    }

    // Falhar na inicialização é melhor que descobrir na primeira chamada que um
    // argumento não tem origem definida, tem duas origens ou usa um tipo não suportado.
    private static void validateParameters(Method method, List<String> names) {
        int bodies = 0;
        for (var parameter : method.getParameters()) {
            int bindings = (parameter.isAnnotationPresent(PathVariable.class) ? 1 : 0)
                + (parameter.isAnnotationPresent(RequestParam.class) ? 1 : 0)
                + (parameter.isAnnotationPresent(RequestBody.class) ? 1 : 0);
            if (bindings != 1) throw new IllegalStateException("Parâmetro precisa de exatamente uma anotação HTTP: " + method);
            var path = parameter.getAnnotation(PathVariable.class);
            if (path != null && !names.contains(path.value())) throw new IllegalStateException("Variável ausente na rota: " + path.value());
            if (parameter.isAnnotationPresent(RequestBody.class)) bodies++;
            else if (!Set.of(String.class, int.class, Integer.class, long.class, Long.class).contains(parameter.getType()))
                throw new IllegalStateException("Tipo de parâmetro HTTP não suportado: " + parameter);
        }
        if (bodies > 1) throw new IllegalStateException("Apenas um @RequestBody por método: " + method);
    }

    // Executado por requisição. A tabela é compartilhada, mas matcher e variáveis
    // são locais à chamada, evitando misturar valores de clientes diferentes.
    public Match match(String verb, String path) {
        String normalized = normalize(path);
        for (Endpoint endpoint : endpoints) {
            var matcher = endpoint.pattern().matcher(normalized);
            if (endpoint.verb().equals(verb) && matcher.matches()) {
                var variables = new LinkedHashMap<String, String>();
                for (int i = 0; i < endpoint.names().size(); i++) variables.put(endpoint.names().get(i), matcher.group(i + 1));
                return new Match(endpoint.controller(), endpoint.method(), Map.copyOf(variables));
            }
        }
        // Distinguir caminho inexistente (404) de caminho existente com verbo incorreto (405).
        if (!allowedMethods(path).isEmpty()) throw new HttpException(405, "Método HTTP não permitido");
        throw new HttpException(404, "Rota não encontrada");
    }

    public Set<String> allowedMethods(String path) {
        var allowed = new TreeSet<String>();
        endpoints.stream().filter(e -> e.pattern().matcher(normalize(path)).matches()).forEach(e -> allowed.add(e.verb()));
        return allowed;
    }

    public List<String> descriptions() { return endpoints.stream().map(e -> e.verb() + " " + e.template()).toList(); }
    private static String normalize(String path) {
        if (path == null || path.isBlank()) return "/";
        if (!path.startsWith("/")) throw new IllegalArgumentException("Rota deve começar com /: " + path);
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
