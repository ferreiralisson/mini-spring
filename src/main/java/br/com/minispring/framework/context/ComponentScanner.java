package br.com.minispring.framework.context;

import br.com.minispring.framework.annotation.Component;

import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;

/**
 * Descobre classes sem instanciá-las. Funciona no diretório de classes e no JAR executável.
 */
public final class ComponentScanner {
    private ComponentScanner() {
    }

    public static Set<Class<?>> scan(String basePackage) {
        // Pacotes Java usam pontos; entradas do classpath usam barras: a.b vira a/b.
        String prefix = basePackage.replace('.', '/');
        var names = new TreeSet<String>();
        var loader = Thread.currentThread().getContextClassLoader();
        try {
            // O mesmo pacote pode existir em mais de uma localização do classpath.
            var resources = loader.getResources(prefix);
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                // Na IDE/Maven, as classes normalmente estão em diretórios; no executável, em JAR.
                // Os dois caminhos produzem nomes de classe, não instâncias de componentes.
                if (url.getProtocol().equals("file")) {
                    Path root = Path.of(url.toURI());
                    try (var files = Files.walk(root)) {
                        files.filter(p -> p.toString().endsWith(".class")).forEach(p ->
                                names.add(basePackage + "." + root.relativize(p).toString()
                                        .replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", "")));
                    }
                } else if (url.getProtocol().equals("jar")) {
                    var connection = (JarURLConnection) url.openConnection();
                    connection.setUseCaches(false);
                    try (var jar = connection.getJarFile()) {
                        jar.stream().map(ZipEntry::getName)
                                .filter(n -> n.startsWith(prefix + "/") && n.endsWith(".class"))
                                .map(n -> n.substring(0, n.length() - 6).replace('/', '.')).forEach(names::add);
                    }
                } else {
                    throw new IllegalStateException("Protocolo de classpath não suportado: " + url);
                }
            }
            var result = new LinkedHashSet<Class<?>>();
            for (String name : names) {
                // false evita executar inicializadores estáticos nesta etapa de descoberta.
                // Interfaces e classes abstratas podem descrever contratos, mas não viram beans aqui.
                Class<?> type = Class.forName(name, false, loader);
                if (isComponent(type) && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
                    result.add(type);
                }
            }
            if (result.isEmpty()) throw new IllegalStateException("Nenhum componente em " + basePackage);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descobrir componentes em " + basePackage, e);
        }
    }

    // Meta-anotação: @Service, @Repository e @RestController carregam @Component.
    // Neste projeto reconhecemos apenas um nível dessa composição.
    private static boolean isComponent(Class<?> type) {
        return type.isAnnotationPresent(Component.class) || Arrays.stream(type.getAnnotations())
                .anyMatch(a -> a.annotationType().isAnnotationPresent(Component.class));
    }
}
