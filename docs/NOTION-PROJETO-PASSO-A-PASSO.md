# Mini Spring com Jetty — construção passo a passo para a sala de aula

Este documento contém **o código completo do projeto, com os comentários didáticos**, organizado na ordem em que os arquivos devem ser criados. Ele é uma versão para importar como Markdown no Notion e acompanhar enquanto você desenvolve em sala.

## Como usar este material

1. Comece em uma pasta vazia chamada `mini-spring`, com JDK 21 e Maven 3.9+ instalados. A primeira compilação exige acesso à internet.
2. Siga as etapas na ordem. Em cada item, crie o arquivo no caminho indicado e copie ou digite o bloco completo. Os caminhos são relativos à raiz do novo projeto.
3. Os blocos incluem `package` e `import`: são arquivos completos, não trechos para acrescentar no fim de outra classe.
4. Leia “O que explicar” antes de desenvolver o arquivo e use os comentários como apoio durante a escrita.
5. Execute o ponto de verificação ao terminar cada etapa. Só avance quando ele funcionar. Até a etapa 8, não haverá um servidor para acessar; isso é esperado.
6. A partir da etapa 9, deixe a aplicação em um terminal e envie requisições em outro. Encerre com Ctrl+C quando precisar reconstruir o JAR.

**No Notion:** importe este arquivo como Markdown. A página usa títulos, listas e blocos de código convencionais, sem depender de imagens ou de arquivos externos para apresentar o código. Depois da importação, você pode acrescentar um índice da página para navegar pelos títulos. Nenhuma publicação automática no seu workspace foi feita.

**Referência da aula:** Java 21, Jetty 12 embutido, Jackson e JUnit. As anotações do projeto são nossas; não há dependência do Spring.

## Mapa da construção

| Etapa | O que construir | O que já é possível verificar |
|---|---|---|
| 1 | Maven e configuração | Dependências disponíveis |
| 2 | Anotações | Metadados compilam |
| 3 | Configuração, scanning e IoC | Injeção, singleton e falhas de dependências |
| 4 | Respostas, erros e validação | Contratos básicos do pipeline |
| 5 | Modelo e repository | Armazenamento da aplicação |
| 6 | Service e controllers | API declarada, ainda sem servidor |
| 7 | Router | Seleção de métodos por rota |
| 8 | Interceptadores e dispatcher | Pipeline completo compilando |
| 9 | Bootstrap com Jetty e main | API em execução |
| 10 | Requisições e integração | CRUD e erros testados |

## Dois fluxos para desenhar no quadro

```text
INICIALIZAÇÃO (uma vez)
Application.main
  → MiniApplication
  → AppConfig + ComponentScanner
  → ApplicationContext: cria e injeta os beans
  → Router: registra os métodos dos controllers
  → Jetty: abre a porta e passa a receber HTTP

REQUISIÇÃO (a cada chamada)
Cliente → Jetty → DispatcherServlet
  → Interceptor.before
  → Router.match
  → Binding + Jackson + Validator
  → Controller → Service → Repository
  → HttpResult ou objeto Java
  → Serialização JSON e escrita da resposta
  → Interceptor.after
```

A inicialização injeta serviços nos construtores. Cada requisição fornece novos dados aos métodos. Essa distinção é o fio condutor da aula.

## Etapa 01 — Preparar o projeto

**Objetivo:** Ter uma estrutura Maven com Java 21 e as bibliotecas externas.

**Condução da aula:** Mostre a diferença entre src/main/java, src/main/resources e src/test/java. O arquivo pom.xml já contém o empacotamento final; a classe principal será criada na etapa 9.

### Arquivo 01 — pom.xml

**Criar em:** `pom.xml`

**O que explicar:** Explique que Jetty recebe HTTP, Jackson converte JSON, SLF4J registra logs e JUnit executa testes. O Shade empacota a aplicação e as dependências em um JAR executável.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>br.com.minispring</groupId>
    <artifactId>mini-spring</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <jetty.version>12.0.39</jetty.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.eclipse.jetty.ee10</groupId>
            <artifactId>jetty-ee10-servlet</artifactId>
            <version>${jetty.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.21.5</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.17</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.12.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
                <configuration>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.4</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                        <exclude>module-info.class</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                            <transformers>
                                <transformer
                                        implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                                <transformer
                                        implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>br.com.minispring.exemplo.Application</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### Arquivo 02 — .gitignore

**Criar em:** `.gitignore`

**O que explicar:** Evite versionar arquivos gerados pela compilação e configurações locais da IDE.

```text
/target/
.idea/
*.iml
.DS_Store
```

### Arquivo 03 — application.properties

**Criar em:** `src/main/resources/application.properties`

**O que explicar:** Separe valores de configuração do código. A porta e o nome da aplicação poderão mudar sem editar uma classe.

```properties
server.port=8080
app.name=Escola Mini Spring
```

### Ponto de verificação da etapa 01

Execute na raiz do projeto:

```sh
mvn -q compile
```

**Resultado esperado:** O Maven baixa as dependências e termina sem erro. Ainda não existe uma aplicação para iniciar.

**Pergunta para a turma:** Quem vai escutar a porta HTTP? Resposta: o Jetty, que só será iniciado quando construirmos o bootstrap.

## Etapa 02 — Criar as anotações

**Objetivo:** Definir o vocabulário que o framework vai interpretar.

**Condução da aula:** Apresente primeiro Component e seus três estereótipos. Depois as anotações de configuração, HTTP e validação. Não tente explicar o algoritmo do roteador ainda: nesta etapa estamos somente declarando metadados.

### Arquivo 04 — Component.java

**Criar em:** `src/main/java/br/com/minispring/framework/annotation/Component.java`

**O que explicar:** Comece pelo marcador básico. Explique RUNTIME e a possibilidade de anotar outras anotações com ANNOTATION_TYPE.

```java
package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Marca classes elegíveis para o container. ANNOTATION_TYPE permite usá-la
// também sobre @Service, @Repository e @RestController (meta-anotações).
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface Component {  }
```

### Arquivo 05 — Service.java

**Criar em:** `src/main/java/br/com/minispring/framework/annotation/Service.java`

**O que explicar:** É uma especialização semântica de Component para regras de negócio.

```java
package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Identifica a camada de regras de negócio. @Component permite sua descoberta;
// neste mini framework, não adiciona comportamento além do registro como bean.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Component
public @interface Service {  }
```

### Arquivo 06 — Repository.java

**Criar em:** `src/main/java/br/com/minispring/framework/annotation/Repository.java`

**O que explicar:** É uma especialização para armazenamento. A anotação não cria banco de dados nem SQL.

```java
package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Identifica a camada de armazenamento. Não gera consultas nem transações;
// a implementação continua sendo responsabilidade da classe anotada.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Component
public @interface Repository {  }
```

### Arquivo 07 — RestController.java

**Criar em:** `src/main/java/br/com/minispring/framework/annotation/RestController.java`

**O que explicar:** Também é um componente, mas será reconhecido pelo Router como publicador de rotas. value é o prefixo.

```java
package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Registra um bean e permite ao Router procurar seus métodos @Route.
// value define o prefixo comum das rotas deste controller.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Component
public @interface RestController { String value() default ""; }
```

### Arquivo 08 — Value.java

**Criar em:** `src/main/java/br/com/minispring/framework/annotation/Value.java`

**O que explicar:** Marca um argumento do construtor que vem da configuração, não de outro bean.

```java
package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Associa um parâmetro de construtor a uma chave de configuração, como app.name.
// A resolução acontece na criação do bean; não há atualização automática em runtime.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Value { String value(); }
```

### Arquivo 09 — Route.java

**Criar em:** `src/main/java/br/com/minispring/framework/annotation/Route.java`

**O que explicar:** Associa verbo e caminho HTTP a um método Java.

```java
package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Descreve método HTTP e caminho. A anotação não atende requisições sozinha:
// o Router lê estes valores no bootstrap e o dispatcher invoca o método depois.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Route { String method(); String path() default ""; }
```

### Arquivo 10 — PathVariable.java

**Criar em:** `src/main/java/br/com/minispring/framework/annotation/PathVariable.java`

**O que explicar:** Nomeia um valor capturado na URL, como o id de /cursos/{id}.

```java
package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Associa um argumento do método a um segmento nomeado, como {id}.
// O valor explícito evita depender do nome de parâmetro preservado no bytecode.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface PathVariable { String value(); }
```

### Arquivo 11 — RequestParam.java

**Criar em:** `src/main/java/br/com/minispring/framework/annotation/RequestParam.java`

**O que explicar:** Nomeia um valor da query string e seu default quando ausente.

```java
package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Lê um argumento da query string, como ?nome=java. Se ausente, usa defaultValue.
// Para um parâmetro numérico opcional, informe um default que possa ser convertido.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface RequestParam { String value(); String defaultValue() default ""; }
```

### Arquivo 12 — RequestBody.java

**Criar em:** `src/main/java/br/com/minispring/framework/annotation/RequestBody.java`

**O que explicar:** Indica que o argumento vem do corpo JSON.

```java
package br.com.minispring.framework.annotation;

import java.lang.annotation.*;

// Indica que o argumento vem da desserialização do corpo JSON.
// Cada método aceita no máximo um corpo; o DTO não é um bean singleton.
// RUNTIME mantém a anotação disponível para leitura por reflection durante a execução.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface RequestBody {  }
```

### Arquivo 13 — NotBlank.java

**Criar em:** `src/main/java/br/com/minispring/framework/validation/NotBlank.java`

**O que explicar:** Será lida nos componentes de um record para validar texto obrigatório.

```java
package br.com.minispring.framework.validation;

import java.lang.annotation.*;

// Exige texto não nulo e com pelo menos um caractere que não seja espaço em branco.
// RECORD_COMPONENT permite ao Validator encontrar a regra em getRecordComponents().
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface NotBlank {}
```

### Arquivo 14 — Positive.java

**Criar em:** `src/main/java/br/com/minispring/framework/validation/Positive.java`

**O que explicar:** Será lida para validar um número maior que zero.

```java
package br.com.minispring.framework.validation;

import java.lang.annotation.*;

// Exige um valor numérico maior que zero; zero também é inválido.
// RECORD_COMPONENT permite ao Validator encontrar a regra em getRecordComponents().
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Positive {}
```

### Ponto de verificação da etapa 02

Execute na raiz do projeto:

```sh
mvn -q compile
```

**Resultado esperado:** Todas as anotações compilam. Nenhuma cria objetos ou processa HTTP sozinha.

**Pergunta para a turma:** Se apenas escrevermos @Service, quem instancia a classe? Resposta: ninguém ainda; falta o container que lê esse metadado.

## Etapa 03 — Construir o container IoC

**Objetivo:** Descobrir componentes e resolver suas dependências por construtor.

**Condução da aula:** Destaque a diferença entre definição (Class) e instância (Object). Explique isAssignableFrom, o cache de singletons e a pilha de criação que detecta ciclos. Mostre que receber uma interface por construtor já é DI, mesmo sem container.

### Arquivo 15 — AppConfig.java

**Criar em:** `src/main/java/br/com/minispring/framework/context/AppConfig.java`

**O que explicar:** Primeiro implemente a leitura de configuração; ApplicationContext vai depender dela.

```java
package br.com.minispring.framework.context;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Precedência: -Dpropriedade > VARIAVEL_AMBIENTE > application.properties. */
public final class AppConfig {
    private final Properties properties;
    private final Map<String, String> environment;

    public AppConfig(Properties properties, Map<String, String> environment) {
        // Copiar a entrada evita que alterações externas mudem a configuração já carregada.
        this.properties = new Properties();
        this.properties.putAll(properties);
        this.environment = Map.copyOf(environment);
    }

    public static AppConfig load() {
        var properties = new Properties();
        // Ler como recurso do classpath funciona também dentro do JAR.
        // try-with-resources fecha o stream automaticamente, inclusive quando há exceção.
        try (var input = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler application.properties", e);
        }
        return new AppConfig(properties, System.getenv());
    }

    public String get(String key) {
        // Exemplo: -Dserver.port=9090 ganha de SERVER_PORT, que ganha do arquivo.
        // A primeira fonte com valor encerra a busca; ausência em todas é erro de configuração.
        String value = System.getProperty(key);
        if (value == null) value = environment.get(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (value == null) value = properties.getProperty(key);
        if (value == null) throw new IllegalStateException("Configuração ausente: " + key);
        return value;
    }
}
```

### Arquivo 16 — ComponentScanner.java

**Criar em:** `src/main/java/br/com/minispring/framework/context/ComponentScanner.java`

**O que explicar:** Transforme um pacote em nomes de classes e filtre componentes concretos. Explique por que IDE e JAR exigem caminhos diferentes.

```java
package br.com.minispring.framework.context;

import br.com.minispring.framework.annotation.Component;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Descobre classes sem instanciá-las. Funciona no diretório de classes e no JAR executável. */
public final class ComponentScanner {
    private ComponentScanner() {}

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
                        jar.stream().map(e -> e.getName())
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
```

### Arquivo 17 — ApplicationContext.java

**Criar em:** `src/main/java/br/com/minispring/framework/context/ApplicationContext.java`

**O que explicar:** Percorra getBean com o desenho Controller → Service → Repository. A recursão resolve os argumentos antes de chamar o construtor.

```java
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
```

### Arquivo 18 — ApplicationContextTest.java

**Criar em:** `src/test/java/br/com/minispring/ApplicationContextTest.java`

**O que explicar:** Use estes testes imediatamente: eles constroem classes pequenas sem servidor HTTP para demonstrar singleton, interface, erros e configuração.

```java
package br.com.minispring;

import br.com.minispring.framework.context.*;
import br.com.minispring.framework.annotation.Value;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationContextTest {
    private final AppConfig config = new AppConfig(new Properties(), Map.of());
    public interface Storage {}
    public static class Memory implements Storage { public Memory() {} }
    public static class OtherMemory implements Storage { public OtherMemory() {} }
    public static class Service {
        final Storage storage;
        public Service(Storage storage) { this.storage = storage; }
    }
    public static class A { public A(B b) {} }
    public static class B { public B(A a) {} }
    public static class Configured {
        final String name;
        public Configured(@Value("test.name") String name) { this.name = name; }
    }
    public static class InvalidConstructors {
        public InvalidConstructors() {}
        public InvalidConstructors(Storage storage) {}
    }

    @Test void injectsImplementationAndReusesSingleton() {
        var context = new ApplicationContext(Set.of(Service.class, Memory.class), config);
        assertSame(context.getBean(Storage.class), context.getBean(Service.class).storage);
        assertSame(context.getBean(Service.class), context.getBean(Service.class));
    }
    @Test void failsOnMissingDependency() {
        assertTrue(assertThrows(IllegalStateException.class,
            () -> new ApplicationContext(Set.of(Service.class), config)).getMessage().contains("não registrada"));
    }
    @Test void failsOnAmbiguousDependency() {
        assertTrue(assertThrows(IllegalStateException.class,
            () -> new ApplicationContext(Set.of(Service.class, Memory.class, OtherMemory.class), config)).getMessage().contains("ambígua"));
    }
    @Test void explainsCircularDependency() {
        assertTrue(assertThrows(IllegalStateException.class,
            () -> new ApplicationContext(Set.of(A.class, B.class), config)).getMessage().contains("circular"));
    }
    @Test void requiresOnePublicConstructor() {
        assertThrows(IllegalStateException.class, () -> new ApplicationContext(Set.of(InvalidConstructors.class), config));
    }
    @Test void configurationPrecedenceAndInjection() {
        var properties = new Properties();
        properties.setProperty("test.name", "arquivo");
        var config = new AppConfig(properties, Map.of("TEST_NAME", "ambiente"));
        assertEquals("ambiente", config.get("test.name"));
        String previous = System.getProperty("test.name");
        try {
            System.setProperty("test.name", "sistema");
            var context = new ApplicationContext(Set.of(Configured.class), config);
            assertEquals("sistema", context.getBean(Configured.class).name);
        } finally {
            if (previous == null) System.clearProperty("test.name"); else System.setProperty("test.name", previous);
        }
        assertEquals("arquivo", new AppConfig(properties, Map.of()).get("test.name"));
    }
}
```

### Ponto de verificação da etapa 03

Execute na raiz do projeto:

```sh
mvn -q -Dtest=ApplicationContextTest test
```

**Resultado esperado:** 6 testes passam: singleton/injeção, dependência ausente, ambiguidade, ciclo, construtores e configuração.

**Pergunta para a turma:** Dois controllers que pedem o mesmo service recebem quantos objetos? Resposta: um por contexto, pois o cache reaproveita a instância.

## Etapa 04 — Definir respostas, erros e validação

**Objetivo:** Estabelecer o contrato que a aplicação e o dispatcher vão compartilhar.

**Condução da aula:** Essas classes vêm antes da API porque o service e o controller as utilizarão. Diferencie JSON malformado, DTO inválido e regra de negócio. Nosso Validator é pequeno e só percorre o primeiro nível de records.

### Arquivo 19 — HttpException.java

**Criar em:** `src/main/java/br/com/minispring/framework/web/HttpException.java`

**O que explicar:** Armazena status e detalhes de uma falha conhecida; lançar uma exceção ainda não escreve uma resposta HTTP.

```java
package br.com.minispring.framework.web;

import java.util.List;

// Representa uma falha que o dispatcher sabe traduzir para HTTP.
// Lançar esta exceção não escreve na conexão: a escrita continua centralizada no Servlet.
public final class HttpException extends RuntimeException {
    private final int status;
    private final List<String> details;
    public HttpException(int status, String message) { this(status, message, List.of()); }
    public HttpException(int status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = List.copyOf(details);
    }
    public int status() { return status; }
    public List<String> details() { return details; }
}
```

### Arquivo 20 — HttpResult.java

**Criar em:** `src/main/java/br/com/minispring/framework/web/HttpResult.java`

**O que explicar:** Representa status, corpo e headers. Explique 200, 201 com Location e 204 sem corpo.

```java
package br.com.minispring.framework.web;

import java.util.Map;

/** Controller declara a resposta sem depender da API Servlet. */
public record HttpResult(int status, Object body, Map<String, String> headers) {
    public HttpResult { headers = Map.copyOf(headers); }
    public static HttpResult ok(Object body) { return new HttpResult(200, body, Map.of()); }
    // 201 informa criação; Location indica onde consultar o recurso recém-criado.
    public static HttpResult created(String location, Object body) {
        return new HttpResult(201, body, Map.of("Location", location));
    }
    // 204 significa sucesso sem corpo: o dispatcher não deve serializar nem mesmo o texto null.
    public static HttpResult noContent() { return new HttpResult(204, null, Map.of()); }
}
```

### Arquivo 21 — Validator.java

**Criar em:** `src/main/java/br/com/minispring/framework/validation/Validator.java`

**O que explicar:** Leia as anotações dos componentes de records e acumule erros. O dispatcher chamará este código antes do controller.

```java
package br.com.minispring.framework.validation;

import br.com.minispring.framework.web.HttpException;
import java.util.ArrayList;

/** Validação intencionalmente pequena: somente componentes de records, sem recursão. */
public final class Validator {
    private Validator() {}
    public static void validate(Object value) {
        if (value == null) throw new HttpException(400, "Corpo JSON obrigatório");
        if (!value.getClass().isRecord()) return;
        var errors = new ArrayList<String>();
        for (var component : value.getClass().getRecordComponents()) {
            try {
                // Em um record, o componente nome tem o accessor nome(). Invocá-lo por reflection
                // permite validar DTOs diferentes sem escrever um if para cada classe de aplicação.
                Object field = component.getAccessor().invoke(value);
                if (component.isAnnotationPresent(NotBlank.class)
                    && (!(field instanceof String text) || text.isBlank())) {
                    errors.add(component.getName() + " não pode estar em branco");
                }
                if (component.isAnnotationPresent(Positive.class)
                    && (!(field instanceof Number number) || number.doubleValue() <= 0)) {
                    errors.add(component.getName() + " deve ser positivo");
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Não foi possível validar " + component.getName(), e);
            }
        }
        // Acumular erros permite ao aluno/cliente corrigir vários campos na mesma tentativa.
        if (!errors.isEmpty()) throw new HttpException(400, "Falha de validação", errors);
    }
}
```

### Ponto de verificação da etapa 04

Execute na raiz do projeto:

```sh
mvn -q compile
```

**Resultado esperado:** Os contratos de resposta e o validador compilam. O servidor ainda não foi criado.

**Pergunta para a turma:** Qual classe efetivamente verifica @NotBlank? Resposta: Validator; a anotação apenas descreve a restrição.

## Etapa 05 — Construir o modelo e o armazenamento

**Objetivo:** Implementar o domínio sem depender de conexão HTTP.

**Condução da aula:** Compare DTO de entrada com modelo de saída. No repository, foque primeiro no CRUD e depois retome AtomicLong e computeIfPresent para mostrar por que singleton exige atenção à concorrência.

### Arquivo 22 — Curso.java

**Criar em:** `src/main/java/br/com/minispring/exemplo/model/Curso.java`

**O que explicar:** Mostre o record devolvido pela API, incluindo o id gerado pelo servidor.

```java
package br.com.minispring.exemplo.model;

// Um record gera construtor, accessors, equals/hashCode e toString. Como seus campos
// aqui são primitivas e String, este modelo é imutável; atualizar significa criar outro Curso.
public record Curso(long id, String nome, int cargaHoraria) {}
```

### Arquivo 23 — CursoInput.java

**Criar em:** `src/main/java/br/com/minispring/exemplo/model/CursoInput.java`

**O que explicar:** O contrato de entrada tem apenas nome e carga horária, com validação; o cliente não escolhe o id.

```java
package br.com.minispring.exemplo.model;

import br.com.minispring.framework.validation.NotBlank;
import br.com.minispring.framework.validation.Positive;

/** DTO de entrada separado do modelo: o cliente não escolhe o ID. */
// O DTO define o contrato de entrada e não possui id. As anotações são metadados:
// é o Validator, chamado pelo dispatcher, que efetivamente verifica essas restrições.
public record CursoInput(@NotBlank String nome, @Positive int cargaHoraria) {}
```

### Arquivo 24 — CursoRepository.java

**Criar em:** `src/main/java/br/com/minispring/exemplo/repository/CursoRepository.java`

**O que explicar:** Defina a interface antes da implementação. Optional explicita um curso ausente.

```java
package br.com.minispring.exemplo.repository;

import br.com.minispring.exemplo.model.Curso;

import java.util.List;
import java.util.Optional;

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

    boolean delete(long id);
}
```

### Arquivo 25 — InMemoryCursoRepository.java

**Criar em:** `src/main/java/br/com/minispring/exemplo/repository/InMemoryCursoRepository.java`

**O que explicar:** Implemente o armazenamento compartilhado. Explique IDs atômicos, operações concorrentes e a ausência de persistência em disco.

```java
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
```

### Ponto de verificação da etapa 05

Execute na raiz do projeto:

```sh
mvn -q compile
```

**Resultado esperado:** Modelo, DTO e repository compilam. Os dados serão perdidos ao encerrar o processo.

**Pergunta para a turma:** ConcurrentHashMap torna duas gravações em registros diferentes uma transação? Resposta: não; a segurança de operações individuais não garante atomicidade de um fluxo inteiro.

## Etapa 06 — Escrever o service e os controllers

**Objetivo:** Usar o framework para declarar regras de negócio e a interface HTTP.

**Condução da aula:** Desenhe o grafo CursoController → CursoService → CursoRepository → InMemoryCursoRepository, deixando claro que a interface é um contrato, não uma instância adicional. Nenhum endpoint pode ser chamado ainda: faltam roteamento, dispatcher e bootstrap.

### Arquivo 26 — CursoService.java

**Criar em:** `src/main/java/br/com/minispring/exemplo/service/CursoService.java`

**O que explicar:** Injete CursoRepository por interface e aplique a regra de no máximo 2000 horas. Mostre o acoplamento didático com HttpException.

```java
package br.com.minispring.exemplo.service;

import br.com.minispring.framework.annotation.Service;
import br.com.minispring.framework.web.HttpException;
import br.com.minispring.exemplo.model.*;
import br.com.minispring.exemplo.repository.CursoRepository;

import java.util.List;
import java.util.Locale;

@Service
public final class CursoService {
    private final CursoRepository repository;

    // Dependemos da interface, permitindo trocar o armazenamento ou usar um fake nos testes.
    // A implementação concreta é escolhida pelo container durante a inicialização.
    public CursoService(CursoRepository repository) {
        this.repository = repository;
    }

    public List<Curso> list(String nome) {
        // Normalizar os dois lados permite busca sem distinguir maiúsculas de minúsculas.
        // Locale.ROOT evita que a regra varie conforme o idioma configurado na máquina.
        String filter = nome.toLowerCase(Locale.ROOT);
        return repository.findAll().stream().filter(c -> c.nome().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    public Curso get(long id) {
        return repository.findById(id).orElseThrow(this::notFound);
    }

    public Curso create(CursoInput input) {
        validateBusinessRule(input);
        return repository.create(input.nome().trim(), input.cargaHoraria());
    }

    public Curso update(long id, CursoInput input) {
        validateBusinessRule(input);
        return repository.update(id, input.nome().trim(), input.cargaHoraria()).orElseThrow(this::notFound);
    }

    public void delete(long id) {
        if (!repository.delete(id)) throw notFound();
    }

    // Esta regra pertence ao domínio; @NotBlank e @Positive validam a forma da entrada.
    // A validação do DTO só ocorre automaticamente no pipeline HTTP, não em chamadas diretas.
    private void validateBusinessRule(CursoInput input) {
        if (input.cargaHoraria() > 2000) throw new HttpException(422, "Um curso pode ter no máximo 2000 horas");
    }

    // Simplificação didática: o service conhece HTTP. Uma evolução é lançar exceções
    // de domínio e traduzi-las para status apenas na camada web.
    private HttpException notFound() {
        return new HttpException(404, "Curso não encontrado");
    }
}
```

### Arquivo 27 — CursoController.java

**Criar em:** `src/main/java/br/com/minispring/exemplo/controller/CursoController.java`

**O que explicar:** Leia cada rota como um contrato. Compare a dependência service recebida no construtor com os dados recebidos em cada método.

```java
package br.com.minispring.exemplo.controller;

import br.com.minispring.framework.annotation.*;
import br.com.minispring.framework.web.HttpResult;
import br.com.minispring.exemplo.model.*;
import br.com.minispring.exemplo.service.CursoService;
import java.util.List;

@RestController("/cursos")
public final class CursoController {
    private final CursoService service;
    // DI por construtor: o container fornece o service. O controller não escolhe
    // como construí-lo, e a dependência fica explícita para quem lê ou testa a classe.
    public CursoController(CursoService service) { this.service = service; }

    // GET /cursos?nome=java. Sem query param, o default vazio lista todos os cursos.
    // Retornamos objetos Java; o dispatcher usa Jackson para produzir JSON.
    @Route(method = "GET")
    public List<Curso> list(@RequestParam("nome") String nome) { return service.list(nome); }

    // O "id" da anotação corresponde ao {id} da rota. O dispatcher converte texto em long.
    @Route(method = "GET", path = "/{id}")
    public Curso get(@PathVariable("id") long id) { return service.get(id); }

    // Binding por requisição: input vem do JSON, não do container IoC.
    // A validação das anotações do DTO já ocorreu quando este método começa.
    @Route(method = "POST")
    public HttpResult create(@RequestBody CursoInput input) {
        Curso curso = service.create(input);
        return HttpResult.created("/cursos/" + curso.id(), curso);
    }

    // Uma mesma chamada combina dados da URL (identidade) e do corpo (novos valores).
    @Route(method = "PUT", path = "/{id}")
    public Curso update(@PathVariable("id") long id, @RequestBody CursoInput input) {
        return service.update(id, input);
    }

    // Não há representação para devolver após excluir; usamos explicitamente 204 sem corpo.
    @Route(method = "DELETE", path = "/{id}")
    public HttpResult delete(@PathVariable("id") long id) {
        service.delete(id);
        return HttpResult.noContent();
    }
}
```

### Arquivo 28 — InfoController.java

**Criar em:** `src/main/java/br/com/minispring/exemplo/controller/InfoController.java`

**O que explicar:** Demonstre configuração injetada por @Value e retornos simples que serão serializados em JSON.

```java
package br.com.minispring.exemplo.controller;

import br.com.minispring.framework.annotation.*;
import java.util.Map;

@RestController
public final class InfoController {
    private final String appName;
    // @Value injeta configuração na construção do bean, não um parâmetro HTTP.
    // O nome é resolvido uma vez ao iniciar esta aplicação.
    public InfoController(@Value("app.name") String appName) { this.appName = appName; }

    @Route(method = "GET", path = "/")
    public Map<String, String> info() { return Map.of("application", appName, "framework", "Mini Spring", "server", "Jetty"); }

    // Este health check comprova que a aplicação responde; não testa banco ou outros serviços.
    @Route(method = "GET", path = "/health")
    public Map<String, String> health() { return Map.of("status", "UP"); }
}
```

### Ponto de verificação da etapa 06

Execute na raiz do projeto:

```sh
mvn -q compile
```

**Resultado esperado:** A API está declarada e compila, mas ainda não há porta HTTP aberta.

**Pergunta para a turma:** CursoInput é injetado pelo container? Resposta: não; ele será construído a partir do JSON de cada requisição.

## Etapa 07 — Implementar o roteador

**Objetivo:** Relacionar um verbo e um caminho a um método do controller.

**Condução da aula:** Mostre /cursos/{id} virando uma expressão regular e depois id → "42". A regex encontra texto; o dispatcher fará a conversão para long. A tabela é montada uma vez e não consulta anotações novamente para registrar rotas a cada chamada.

### Arquivo 29 — Router.java

**Criar em:** `src/main/java/br/com/minispring/framework/web/Router.java`

**O que explicar:** Divida a explicação em duas fases: registro das rotas no construtor e busca por requisição em match.

```java
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
```

### Arquivo 30 — RouterTest.java

**Criar em:** `src/test/java/br/com/minispring/RouterTest.java`

**O que explicar:** Teste prioridade de rota literal, extração de id, 404 versus 405, duplicatas e parâmetros sem anotação.

```java
package br.com.minispring;

import br.com.minispring.framework.annotation.*;
import br.com.minispring.framework.web.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RouterTest {
    @RestController("/items") public static class Routes {
        @Route(method="GET", path="/{id}") public String item(@PathVariable("id") long id) { return "item"; }
        @Route(method="GET", path="/resumo") public String summary() { return "summary"; }
    }
    @RestController("/items") public static class Duplicate {
        @Route(method="GET", path="/{other}") public String item(@PathVariable("other") long id) { return "duplicate"; }
    }
    @RestController public static class Invalid {
        @Route(method="GET", path="/bad") public String bad(String missingAnnotation) { return ""; }
    }
    @Test void literalRouteHasPriority() {
        var router = new Router(List.of(new Routes()));
        assertEquals("summary", router.match("GET", "/items/resumo").method().getName());
        assertEquals("42", router.match("GET", "/items/42/").variables().get("id"));
    }
    @Test void distinguishesNotFoundAndMethodNotAllowed() {
        var router = new Router(List.of(new Routes()));
        assertEquals(404, assertThrows(HttpException.class, () -> router.match("GET", "/unknown")).status());
        assertEquals(405, assertThrows(HttpException.class, () -> router.match("POST", "/items/1")).status());
    }
    @Test void rejectsDuplicateTemplatesEvenWithDifferentVariableNames() {
        assertThrows(IllegalStateException.class, () -> new Router(List.of(new Routes(), new Duplicate())));
    }
    @Test void rejectsUnboundParametersAtStartup() {
        assertThrows(IllegalStateException.class, () -> new Router(List.of(new Invalid())));
    }
}
```

### Ponto de verificação da etapa 07

Execute na raiz do projeto:

```sh
mvn -q -Dtest=RouterTest test
```

**Resultado esperado:** 4 testes passam, sem iniciar Jetty.

**Pergunta para a turma:** Por que /cursos/{id} e /cursos/{codigo} com GET são duplicatas? Resposta: aceitam o mesmo formato de URL; trocar o nome da variável não diferencia a rota.

## Etapa 08 — Construir o pipeline HTTP

**Objetivo:** Transformar uma requisição em uma chamada Java e uma resposta JSON.

**Condução da aula:** Percorra seis movimentos: interceptar, resolver rota, fazer binding/validação, invocar o controller, traduzir retorno/erro e escrever JSON. Explique InvocationTargetException e por que o Servlet não deve guardar o DTO atual em um campo.

### Arquivo 31 — RequestInterceptor.java

**Criar em:** `src/main/java/br/com/minispring/framework/web/RequestInterceptor.java`

**O que explicar:** Defina os pontos de extensão before e after antes de usá-los no dispatcher.

```java
package br.com.minispring.framework.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Ponto de extensão para preocupações transversais. Uma instância atende várias threads. */
public interface RequestInterceptor {
    default void before(HttpServletRequest request, HttpServletResponse response) {}
    default void after(HttpServletRequest request, HttpServletResponse response) {}
}
```

### Arquivo 32 — LoggingInterceptor.java

**Criar em:** `src/main/java/br/com/minispring/framework/web/LoggingInterceptor.java`

**O que explicar:** Guarde cronômetro e requestId no request para não compartilhar estado entre clientes.

```java
package br.com.minispring.framework.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.slf4j.LoggerFactory;

public final class LoggingInterceptor implements RequestInterceptor {
    @Override public void before(HttpServletRequest request, HttpServletResponse response) {
        // nanoTime mede duração sem depender de ajustes no relógio de calendário.
        // Guardar no request isola o estado; campos no interceptor seriam compartilhados.
        request.setAttribute("startNanos", System.nanoTime());
        request.setAttribute("requestId", UUID.randomUUID().toString());
        // O mesmo identificador aparece no header, no corpo de erro e no log do servidor.
        response.setHeader("X-Request-Id", request.getAttribute("requestId").toString());
    }
    @Override public void after(HttpServletRequest request, HttpServletResponse response) {
        // Mede o processamento no Servlet, não o tempo até o cliente receber todos os bytes.
        long elapsed = (System.nanoTime() - (long) request.getAttribute("startNanos")) / 1_000_000;
        LoggerFactory.getLogger(LoggingInterceptor.class).info("{} {} -> {} ({} ms) requestId={}",
            request.getMethod(), request.getRequestURI(), response.getStatus(), elapsed, request.getAttribute("requestId"));
    }
}
```

### Arquivo 33 — DispatcherServlet.java

**Criar em:** `src/main/java/br/com/minispring/framework/web/DispatcherServlet.java`

**O que explicar:** Apresente service primeiro, depois bind, convert, error e write. O método service mostra o fluxo completo; os demais detalham cada operação.

```java
package br.com.minispring.framework.web;

import br.com.minispring.framework.annotation.*;
import br.com.minispring.framework.validation.Validator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.slf4j.LoggerFactory;

/** Front Controller: toda requisição passa pelo mesmo pipeline. */
public final class DispatcherServlet extends HttpServlet {
    private static final int MAX_BODY_BYTES = 1_048_576;
    private final Router router;
    private final List<RequestInterceptor> interceptors;
    // O mapper é configurado uma vez e reutilizado. As opções evitam aceitar
    // silenciosamente valores incompatíveis, como 1.5 ou "40" em um campo inteiro.
    private final ObjectMapper json = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);

    public DispatcherServlet(Router router, List<RequestInterceptor> interceptors) {
        this.router = router;
        this.interceptors = List.copyOf(interceptors);
    }

    // Ponto de entrada chamado pelo Jetty, possivelmente em várias threads ao mesmo tempo.
    // Request, response e argumentos pertencem à chamada, não aos campos do Servlet.
    @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        int entered = 0;
        try {
            // 1. Executar comportamentos transversais antes do controller (aqui: ID e cronômetro).
            for (var interceptor : interceptors) {
                interceptor.before(request, response);
                entered++;
            }
            // 2. Descobrir qual método atende o verbo e o caminho desta requisição.
            var match = router.match(request.getMethod(), request.getPathInfo());
            // 3. Transformar dados HTTP em argumentos Java e validar o DTO de entrada.
            Object[] arguments = bind(match, request);
            Object returned;
            try {
                // 4. Equivalente a controller.create(input), mas sem conhecer a classe em compilação.
                // O bean já foi criado pelo IoC; só os argumentos HTTP são novos nesta chamada.
                returned = match.method().invoke(match.controller(), arguments);
            } catch (InvocationTargetException e) {
                // A exceção real vem embrulhada pela reflection. Recuperá-la preserva o status
                // intencional de um erro da aplicação, como curso inexistente (404).
                if (e.getCause() instanceof HttpException http) throw http;
                throw new IllegalStateException("Falha no controller", e.getCause());
            }
            // 5. Um objeto comum vira resposta 200. HttpResult permite escolher status e headers.
            HttpResult result = returned instanceof HttpResult http ? http : HttpResult.ok(returned);
            write(response, result);
        } catch (HttpException e) {
            if (e.status() == 405) response.setHeader("Allow", String.join(", ", router.allowedMethods(request.getPathInfo())));
            write(response, new HttpResult(e.status(), error(request, e.status(), e.getMessage(), e.details()), Map.of()));
        } catch (Exception e) {
            // Erros inesperados ficam detalhados no log; o cliente recebe uma mensagem genérica
            // e um requestId para relacionar a resposta à ocorrência no servidor.
            LoggerFactory.getLogger(DispatcherServlet.class).error("Falha na requisição {}", request.getAttribute("requestId"), e);
            write(response, new HttpResult(500, error(request, 500, "Erro interno do servidor", List.of()), Map.of()));
        } finally {
            // Desempilha os interceptadores na ordem inversa, mesmo em caso de erro.
            for (int i = entered - 1; i >= 0; i--) {
                try { interceptors.get(i).after(request, response); }
                catch (Exception e) { LoggerFactory.getLogger(DispatcherServlet.class).error("Falha no interceptor", e); }
            }
        }
    }

    private Object[] bind(Router.Match match, HttpServletRequest request) throws IOException {
        var parameters = match.method().getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            var parameter = parameters[i];
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                String contentType = request.getContentType();
                if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
                    throw new HttpException(415, "Use Content-Type: application/json");
                // O byte extra permite detectar excesso sem carregar um corpo arbitrariamente grande.
                byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
                if (bytes.length > MAX_BODY_BYTES) throw new HttpException(413, "Corpo excede 1 MiB");
                if (bytes.length == 0) throw new HttpException(400, "Corpo JSON obrigatório");
                try {
                    // Jackson faz a desserialização: bytes JSON → objeto do tipo declarado no parâmetro.
                    // O tipo genérico é preservado, o que também permite representar tipos como List<DTO>.
                    arguments[i] = json.readValue(bytes, json.constructType(parameter.getParameterizedType()));
                } catch (JsonProcessingException e) {
                    throw new HttpException(400, "JSON inválido ou incompatível com os campos esperados");
                }
                // Validar antes de chamar o controller evita processar entradas estruturalmente inválidas.
                // Nosso Validator só inspeciona records no primeiro nível; não percorre listas ou objetos aninhados.
                Validator.validate(arguments[i]);
            } else {
                // Exemplos: /cursos/42 fornece id pelo caminho; ?nome=java fornece nome pela query.
                // Ambos começam como texto; a assinatura Java determina a conversão necessária.
                var path = parameter.getAnnotation(PathVariable.class);
                var query = parameter.getAnnotation(RequestParam.class);
                String name = path != null ? path.value() : query.value();
                String raw = path != null ? match.variables().get(name) : request.getParameter(name);
                if (raw == null && query != null) raw = query.defaultValue();
                arguments[i] = convert(raw, parameter.getType(), name);
            }
        }
        return arguments;
    }

    private Object convert(String raw, Class<?> type, String name) {
        try {
            if (type == String.class) return raw;
            if (type == long.class || type == Long.class) return Long.valueOf(raw);
            if (type == int.class || type == Integer.class) return Integer.valueOf(raw);
        } catch (NumberFormatException e) { throw new HttpException(400, "Parâmetro inválido: " + name); }
        throw new IllegalStateException("Tipo não suportado: " + type);
    }

    private Map<String, Object> error(HttpServletRequest request, int status, String message, List<String> details) {
        // Um formato de erro uniforme permite ao cliente tratar falhas de rotas diferentes.
        return Map.of("timestamp", Instant.now().toString(), "status", status, "message", message,
            "path", request.getRequestURI(), "details", details,
            "requestId", Objects.toString(request.getAttribute("requestId"), ""));
    }

    private void write(HttpServletResponse response, HttpResult result) throws IOException {
        // Serializa antes de alterar a resposta: falhas ainda podem virar um erro 500 limpo.
        byte[] body = result.status() == 204 ? new byte[0] : json.writeValueAsBytes(result.body());
        response.setStatus(result.status());
        result.headers().forEach(response::setHeader);
        if (result.status() != 204) {
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // Content-Length conta bytes enviados, não caracteres: acentos podem ocupar vários bytes.
            // O output stream entrega os bytes ao Jetty, que cuida do transporte HTTP.
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
        }
    }
}
```

### Ponto de verificação da etapa 08

Execute na raiz do projeto:

```sh
mvn -q compile
```

**Resultado esperado:** Todo o pipeline compila. Ainda falta registrá-lo em um servidor Jetty para receber conexões.

**Pergunta para a turma:** Quem converte JSON e quem entende HTTP? Resposta: Jackson converte os dados; Jetty interpreta HTTP; nosso dispatcher coordena a chamada ao código de aplicação.

## Etapa 09 — Ligar tudo com Jetty

**Objetivo:** Montar o bootstrap e iniciar a aplicação fora da IDE.

**Condução da aula:** Agora os arquivos finais que se referenciam já existem. Mostre o mapeamento /* e explique o papel do ServletContextHandler. O Jetty escuta em 127.0.0.1; os testes usarão porta zero para obter uma porta livre.

### Arquivo 34 — MiniApplication.java

**Criar em:** `src/main/java/br/com/minispring/framework/MiniApplication.java`

**O que explicar:** Siga a ordem do bootstrap: configuração/scanning → IoC → rotas → Servlet → conector Jetty. Só abra a porta depois de validar o contexto.

```java
package br.com.minispring.framework;

import br.com.minispring.framework.context.*;
import br.com.minispring.framework.web.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import java.util.List;

/** Bootstrap explícito: configuração -> scanning -> IoC -> rotas -> Jetty. */
public final class MiniApplication implements AutoCloseable {
    private final Server server;
    private final ServerConnector connector;
    private final ApplicationContext context;

    private MiniApplication(Server server, ServerConnector connector, ApplicationContext context) {
        this.server = server;
        this.connector = connector;
        this.context = context;
    }

    public static MiniApplication run(Class<?> applicationClass) throws Exception {
        // O pacote da classe principal delimita o scanning. Por isso Application fica
        // acima dos pacotes controller, service e repository da aplicação de exemplo.
        var config = AppConfig.load();
        return start(applicationClass.getPackageName(), config, Integer.parseInt(config.get("server.port")));
    }

    public static MiniApplication start(String basePackage, AppConfig config, int port) throws Exception {
        // 1. Descobrir classes e construir o grafo de objetos antes de aceitar requisições.
        // Aqui o framework assume o controle da criação: inversão de controle (IoC).
        var context = new ApplicationContext(ComponentScanner.scan(basePackage), config);
        // 2. Ler as anotações dos controllers e preparar a tabela de rotas uma única vez.
        var router = new Router(context.beans());
        // 3. O Jetty cuida das conexões e do protocolo HTTP. O conector define onde escutar.
        var server = new Server();
        var connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(port); // Porta zero permite testes independentes, sem conflito.
        connector.setIdleTimeout(30_000);
        server.addConnector(connector);
        // 4. A API Servlet é a ponte entre Jetty e nosso framework. O mapeamento /*
        // entrega as requisições deste contexto ao mesmo Front Controller.
        var handler = new ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(new ServletHolder(new DispatcherServlet(router, List.of(new LoggingInterceptor()))), "/*");
        server.setHandler(handler);
        server.setStopTimeout(5_000);
        // Registra o encerramento do Jetty junto ao desligamento da JVM, inclusive Ctrl+C.
        server.setStopAtShutdown(true);
        // 5. Só agora a porta é aberta. Se a inicialização falhar, tentamos liberar recursos
        // sem substituir a exceção original por uma eventual falha de limpeza.
        try { server.start(); }
        catch (Exception e) {
            try { server.stop(); } catch (Exception cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
        System.out.println("\nMini Spring em http://127.0.0.1:" + connector.getLocalPort());
        router.descriptions().forEach(route -> System.out.println("  " + route));
        return new MiniApplication(server, connector, context);
    }

    public int port() { return connector.getLocalPort(); }
    public ApplicationContext context() { return context; }
    // A thread principal espera o término do servidor; as requisições são atendidas pelo Jetty.
    public void await() throws InterruptedException { server.join(); }
    @Override public void close() throws Exception { server.stop(); }
}
```

### Arquivo 35 — Application.java

**Criar em:** `src/main/java/br/com/minispring/exemplo/Application.java`

**O que explicar:** O main entrega o controle ao framework, aguarda o servidor e utiliza AutoCloseable para encerramento.

```java
package br.com.minispring.exemplo;

import br.com.minispring.framework.MiniApplication;

public final class Application {
    public static void main(String[] args) throws Exception {
        // A aplicação entrega o controle ao framework. Não precisamos criar controllers
        // e services manualmente. AutoCloseable permite encerrar o Jetty ao sair deste bloco.
        try (var app = MiniApplication.run(Application.class)) {
            app.await();
        }
    }
}
```

### Ponto de verificação da etapa 09

Execute na raiz do projeto:

```sh
mvn -q package
java -jar target/mini-spring-1.0.0.jar
```

**Resultado esperado:** O terminal mostra a URL local e as rotas registradas. O comando java permanece em execução; envie requisições por outro terminal. Encerre com Ctrl+C antes de executar os próximos comandos de build.

**Pergunta para a turma:** Por que iniciar o Jetty por último? Resposta: para detectar erros de dependências e rotas antes de aceitar requisições.

## Etapa 10 — Demonstrar e testar a API completa

**Objetivo:** Validar o CRUD e os erros usando requisições reais ao Jetty.

**Condução da aula:** Agora execute todos os testes. O cenário de erro inesperado escreve propositalmente uma exceção no log: isso não significa falha do teste. Considere o resumo do Maven. Os testes de integração escolhem uma porta livre e não precisam da aplicação manual em execução.

### Arquivo 36 — requests.http

**Criar em:** `requests.http`

**O que explicar:** Arquivo opcional para clientes HTTP de IDE. Também há comandos curl no final deste documento.

```http
@baseUrl = http://127.0.0.1:8080

### Informações
GET {{baseUrl}}/

### Saúde
GET {{baseUrl}}/health

### Criar curso
POST {{baseUrl}}/cursos
Content-Type: application/json

{"nome":"Java básico","cargaHoraria":40}

### Listar
GET {{baseUrl}}/cursos

### Filtrar
GET {{baseUrl}}/cursos?nome=java

### Consultar — ajuste o ID conforme o POST
GET {{baseUrl}}/cursos/1

### Atualizar
PUT {{baseUrl}}/cursos/1
Content-Type: application/json

{"nome":"Java avançado","cargaHoraria":80}

### Validação estrutural: 400
POST {{baseUrl}}/cursos
Content-Type: application/json

{"nome":" ","cargaHoraria":0}

### Regra de negócio: 422
POST {{baseUrl}}/cursos
Content-Type: application/json

{"nome":"Java","cargaHoraria":2001}

### Formato inválido: 400
POST {{baseUrl}}/cursos
Content-Type: application/json

{"nome":

### Tipo de mídia inválido: 415
POST {{baseUrl}}/cursos
Content-Type: text/plain

texto

### Método não permitido: 405
PATCH {{baseUrl}}/cursos/1

### Excluir
DELETE {{baseUrl}}/cursos/1
```

### Arquivo 37 — ApiIntegrationTest.java

**Criar em:** `src/test/java/br/com/minispring/ApiIntegrationTest.java`

**O que explicar:** Cada teste inicia Jetty em uma porta livre e encerra os recursos ao terminar. Os cenários cobrem CRUD, entradas inválidas, rotas e concorrência.

```java
package br.com.minispring;

import br.com.minispring.framework.MiniApplication;
import br.com.minispring.framework.context.AppConfig;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class ApiIntegrationTest {
    private MiniApplication app;
    private HttpClient client;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void start() throws Exception {
        var properties = new Properties();
        properties.setProperty("app.name", "Escola de teste");
        app = MiniApplication.start("br.com.minispring.exemplo", new AppConfig(properties, Map.of()), 0);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
    @AfterEach void stop() throws Exception {
        if (client != null) client.close();
        if (app != null) app.close();
    }
    private HttpRequest request(String method, String path, String body, String contentType) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).timeout(Duration.ofSeconds(10));
        if (contentType != null) builder.header("Content-Type", contentType);
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return client.send(request(method, path, body, body == null ? null : "application/json"), HttpResponse.BodyHandlers.ofString());
    }
    @Test void fullCrudThroughRealJetty() throws Exception {
        assertEquals("Escola de teste", json.readTree(send("GET", "/", null).body()).get("application").asText());
        assertEquals(200, send("GET", "/health", null).statusCode());
        var created = send("POST", "/cursos", "{\"nome\":\" Java básico \",\"cargaHoraria\":40}");
        assertEquals(201, created.statusCode());
        assertTrue(created.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        assertFalse(created.headers().firstValue("X-Request-Id").orElseThrow().isBlank());
        String location = created.headers().firstValue("Location").orElseThrow();
        assertEquals("Java básico", json.readTree(send("GET", location, null).body()).get("nome").asText());
        assertEquals(1, json.readTree(send("GET", "/cursos?nome=JAVA", null).body()).size());
        assertEquals(0, json.readTree(send("GET", "/cursos?nome=python", null).body()).size());
        var updated = send("PUT", location, "{\"nome\":\"Java avançado\",\"cargaHoraria\":80}");
        assertEquals(200, updated.statusCode());
        assertEquals(80, json.readTree(updated.body()).get("cargaHoraria").asInt());
        var removed = send("DELETE", location, null);
        assertEquals(204, removed.statusCode());
        assertEquals("", removed.body());
        assertEquals(404, send("GET", location, null).statusCode());
        assertEquals(404, send("DELETE", location, null).statusCode());
        assertEquals(404, send("PUT", location, "{\"nome\":\"Java\",\"cargaHoraria\":20}").statusCode());
    }
    @Test void rejectsInvalidInputsWithStructuredErrors() throws Exception {
        var invalid = send("POST", "/cursos", "{\"nome\":\" \",\"cargaHoraria\":0}");
        assertEquals(400, invalid.statusCode());
        var error = json.readTree(invalid.body());
        assertEquals(2, error.get("details").size());
        assertEquals(invalid.headers().firstValue("X-Request-Id").orElseThrow(), error.get("requestId").asText());
        for (String body : List.of("{broken", "null", "", "{}", "{\"nome\":\"Java\",\"cargaHoraria\":1.5}",
                "{\"nome\":\"Java\",\"cargaHoraria\":\"40\"}", "{} {}", "{\"nome\":\"Java\",\"cargaHoraria\":40,\"id\":7}")) {
            assertEquals(400, send("POST", "/cursos", body).statusCode(), body);
        }
        assertEquals(400, send("GET", "/cursos/abc", null).statusCode());
        assertEquals(422, send("POST", "/cursos", "{\"nome\":\"Java\",\"cargaHoraria\":2001}").statusCode());
        assertEquals(415, client.send(request("POST", "/cursos", "{}", "text/plain"), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(413, send("POST", "/cursos", "x".repeat(1_048_577)).statusCode());
    }
    @Test void distinguishesRouteErrorsAndSendsAllowHeader() throws Exception {
        assertEquals(404, send("GET", "/inexistente", null).statusCode());
        var response = send("PATCH", "/cursos/1", "{}");
        assertEquals(405, response.statusCode());
        assertEquals("DELETE, GET, PUT", response.headers().firstValue("Allow").orElseThrow());
    }
    @Test void concurrentRequestsProduceUniqueIds() throws Exception {
        var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < 30; i++) futures.add(client.sendAsync(
            request("POST", "/cursos", "{\"nome\":\"Curso\",\"cargaHoraria\":10}", "application/json"), HttpResponse.BodyHandlers.ofString()));
        var ids = new HashSet<Long>();
        for (var future : futures) {
            var response = future.join();
            assertEquals(201, response.statusCode());
            ids.add(json.readTree(response.body()).get("id").asLong());
        }
        assertEquals(30, ids.size());
        assertEquals(30, json.readTree(send("GET", "/cursos", null).body()).size());
    }
}
```

### Arquivo 38 — FailureController.java

**Criar em:** `src/test/java/br/com/minispring/failurefixture/FailureController.java`

**O que explicar:** Crie este controller somente em src/test/java. Ele gera uma falha intencional e não entra no JAR da aplicação.

```java
package br.com.minispring.failurefixture;

import br.com.minispring.framework.annotation.*;

/** Componente exclusivo dos testes; nunca é incluído no JAR da aplicação. */
@RestController
public final class FailureController {
    @Route(method = "GET", path = "/failure")
    public Object fail() { throw new IllegalStateException("detalhe-interno-do-teste"); }
}
```

### Arquivo 39 — UnexpectedErrorIntegrationTest.java

**Criar em:** `src/test/java/br/com/minispring/UnexpectedErrorIntegrationTest.java`

**O que explicar:** Verifique que uma falha inesperada gera 500 com requestId, sem expor a mensagem interna ao cliente.

```java
package br.com.minispring;

import br.com.minispring.framework.MiniApplication;
import br.com.minispring.framework.context.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UnexpectedErrorIntegrationTest {
    @Test void unexpectedFailureReturnsJsonWithoutLeakingImplementationDetails() throws Exception {
        try (var app = MiniApplication.start("br.com.minispring.failurefixture", new AppConfig(new Properties(), Map.of()), 0);
             var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/failure"))
                .timeout(Duration.ofSeconds(5)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(500, response.statusCode());
            var body = new ObjectMapper().readTree(response.body());
            assertEquals("Erro interno do servidor", body.get("message").asText());
            assertFalse(response.body().contains("detalhe-interno-do-teste"));
            assertEquals(response.headers().firstValue("X-Request-Id").orElseThrow(), body.get("requestId").asText());
        }
    }
}
```

### Ponto de verificação da etapa 10

Execute na raiz do projeto:

```sh
mvn clean verify
```

**Resultado esperado:** 15 testes passam. O JAR final é gerado em target/mini-spring-1.0.0.jar.

**Pergunta para a turma:** Por que testar o Servlet com um servidor real além dos testes unitários? Resposta: para verificar também a integração entre roteamento, binding, JSON, headers e transporte HTTP.

## Demonstração final — um CRUD completo no terminal

Depois de `mvn clean verify`, inicie a aplicação em um terminal:

```sh
java -jar target/mini-spring-1.0.0.jar
```

Em outro terminal, execute os comandos abaixo. Em uma execução nova e sem cadastros anteriores, o primeiro curso terá id 1. Caso contrário, use o id e o header `Location` devolvidos pelo POST.

### 1. Verificar que o servidor está respondendo

```sh
curl -i http://127.0.0.1:8080/health
```

Esperado: status 200 e `{"status":"UP"}`. Mostre o header `X-Request-Id` e procure o mesmo valor no log.

### 2. Criar um curso

```sh
curl -i -X POST http://127.0.0.1:8080/cursos \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Java básico","cargaHoraria":40}'
```

Esperado: status 201, `Location: /cursos/1` e um JSON com id, nome e cargaHoraria. O aluno deve identificar onde o JSON virou CursoInput e onde o id foi gerado.

### 3. Listar, filtrar e consultar

```sh
curl -i http://127.0.0.1:8080/cursos
curl -i 'http://127.0.0.1:8080/cursos?nome=java'
curl -i http://127.0.0.1:8080/cursos/1
```

Esperado: status 200. Compare a lista devolvida nas duas primeiras chamadas com o objeto da terceira. Identifique query param e path variable.

### 4. Atualizar

```sh
curl -i -X PUT http://127.0.0.1:8080/cursos/1 \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Java avançado","cargaHoraria":80}'
```

Esperado: status 200 e o curso atualizado, mantendo o mesmo id.

### 5. Provocar falhas compreensíveis

```sh
# DTO inválido: 400 com detalhes dos campos
curl -i -X POST http://127.0.0.1:8080/cursos \
  -H 'Content-Type: application/json' \
  -d '{"nome":" ","cargaHoraria":0}'

# Regra de negócio: 422
curl -i -X POST http://127.0.0.1:8080/cursos \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Java","cargaHoraria":2001}'

# Parâmetro numérico inválido: 400
curl -i http://127.0.0.1:8080/cursos/abc

# Caminho sem mapeamento: 404
curl -i http://127.0.0.1:8080/inexistente

# Verbo sem mapeamento neste caminho: 405 com Allow
curl -i -X PATCH http://127.0.0.1:8080/cursos/1
```

Peça que a turma identifique quem detectou cada erro: Validator, service, binding ou Router. Todos chegam ao tratamento central do dispatcher.

### 6. Excluir e comprovar a ausência

```sh
curl -i -X DELETE http://127.0.0.1:8080/cursos/1
curl -i http://127.0.0.1:8080/cursos/1
```

Esperado: primeiro 204 sem corpo; depois 404, pois o curso não existe mais.

### 7. Demonstrar configuração e ciclo de vida

Encerre a aplicação com Ctrl+C e reinicie em outra porta:

```sh
java -Dserver.port=9090 -jar target/mini-spring-1.0.0.jar
```

Em outro terminal:

```sh
curl -i http://127.0.0.1:9090/cursos
```

Esperado: lista vazia. A configuração mudou a porta e o reinício apagou o armazenamento em memória.

## Pontos de depuração para explicar um POST

1. `MiniApplication.start`: observe o contexto e o registro das rotas antes de o servidor iniciar.
2. `ApplicationContext.getBean`: veja o cache e os argumentos do construtor; isso acontece durante a inicialização.
3. `DispatcherServlet.service`: observe método, caminho e headers da requisição.
4. `Router.match`: acompanhe qual método Java foi selecionado.
5. `DispatcherServlet.bind`: veja os bytes se transformarem em CursoInput.
6. `Validator.validate`: acompanhe a leitura das anotações do DTO.
7. `CursoController.create` e `CursoService.create`: acompanhe a regra de negócio.
8. `InMemoryCursoRepository.create`: veja a geração do id e a inserção no mapa.
9. `DispatcherServlet.write`: veja o objeto Java virar bytes JSON.
10. `LoggingInterceptor.after`: relacione status e requestId ao que chegou no cliente.

## Se algo não funcionar durante a aula

| Sintoma | O que conferir |
|---|---|
| `package ... does not exist` | Etapa anterior, caminho do arquivo e declaração package. Recarregue as dependências Maven na IDE. |
| Compilação não aceita Java 21 | Compare `java -version` e `mvn -version`; os dois precisam usar o JDK correto. |
| Nenhum componente encontrado | Application deve ficar em `br.com.minispring.exemplo`, acima dos pacotes da aplicação. |
| Dependência não registrada | A implementação tem @Repository/@Service/@Component e está no pacote escaneado? |
| Dependência ambígua | Existem duas implementações registradas para a mesma interface? Nosso container exige apenas uma. |
| Porta em uso | Encerre a execução anterior ou escolha outra porta com `-Dserver.port=9090`. |
| Alterei uma classe, mas a resposta não mudou | Pare a execução, gere novamente o JAR com `mvn package` e inicie o novo JAR. Não há hot reload. |
| POST retorna 415 | Inclua `Content-Type: application/json`. |
| POST retorna 400 | Confira nomes dos campos, tipos, sintaxe JSON e as regras de validação. |
| Curso 1 não existe | Leia o id devolvido pelo POST. Os dados são apagados ao reiniciar. |
| Log tem exceção durante os testes | O teste de erro inesperado provoca uma exceção de propósito. Confira se o resumo do Maven informa testes aprovados. |

## Exercício de conclusão

Implemente um cadastro de alunos usando novos DTO, modelo, repository, service e controller. Não altere o container, o roteador ou o dispatcher.

- Receba nome e idade, valide a entrada e gere o id no repository.
- Implemente POST, GET da lista e GET por id.
- Retorne 201 com Location ao criar e 404 ao consultar um aluno inexistente.
- Teste por HTTP e explique a diferença entre DI por construtor e binding do JSON.

**Critério central:** o aluno consegue acrescentar uma funcionalidade usando o framework e explicar o trabalho que cada camada executou.


## Encerramento — conceitos e materiais para a turma

Ao concluir o projeto, você deverá conseguir explicar como o framework cria objetos, conecta suas dependências e transforma uma requisição HTTP em uma chamada Java e uma resposta JSON.

Os materiais abaixo complementam o código desenvolvido em sala. Os links foram consultados em 17/09/2026. Comece pelas leituras essenciais; use as referências mais extensas para aprofundar um assunto por vez.

### Conceitos apresentados e onde encontrá-los

| Conceito | O que aprendemos | Onde aparece no projeto |
|---|---|---|
| Interfaces e inversão de dependência | O service depende do contrato de armazenamento, permitindo substituir sua implementação. | `CursoRepository` e construtor de `CursoService` |
| Separação de responsabilidades | Controller descreve HTTP; service aplica regras de negócio; repository cuida dos dados. | Pacotes `controller`, `service` e `repository` |
| Inversão de controle (IoC) | A criação e a conexão dos objetos passam a ser coordenadas pelo framework. | `ApplicationContext` |
| Injeção de dependência (DI) | Um objeto recebe pelo construtor os colaboradores de que precisa. DI também pode ser feita manualmente. | Construtores de `CursoController` e `CursoService` |
| Container e beans | O container registra tipos e mantém as instâncias gerenciadas, chamadas beans. | `definitions` e `singletons` em `ApplicationContext` |
| Escopo singleton | Uma instância é reutilizada dentro do mesmo contexto; isso não garante segurança de acesso concorrente. | Cache em `ApplicationContext.getBean` |
| Grafo de dependências e fail fast | Dependências ausentes, ambíguas ou circulares são detectadas antes de iniciar o servidor. | `getBean`, `creating` e testes do contexto |
| Anotações e meta-anotações | Metadados descrevem intenções, mas precisam de código que os interprete. | `@Component`, `@Service`, `@Route`, `@NotBlank` |
| Reflection | Inspecionamos tipos e invocamos construtores, métodos e accessors em tempo de execução. | `newInstance`, `Method.invoke` e `Validator` |
| Classpath e descoberta de componentes | Procuramos classes elegíveis em diretórios ou dentro de JARs. | `ComponentScanner` |
| Configuração externa | Valores podem vir de propriedades da JVM, ambiente e arquivo de configuração, com precedência definida. | `AppConfig` e `@Value` |
| Bootstrap e ciclo de vida | A inicialização tem uma ordem; o servidor precisa ser iniciado e encerrado corretamente. | `MiniApplication`, `Application` e `AutoCloseable` |
| Servidor embutido e Servlet | Jetty recebe HTTP e entrega request/response à API Servlet usada pelo framework. | `ServerConnector`, `ServletContextHandler` e `HttpServlet` |
| Protocolo HTTP | Método, caminho, headers, corpo e status têm responsabilidades distintas. | API `/cursos` e `requests.http` |
| Front Controller | Um ponto central coordena o processamento de diferentes endpoints. | `DispatcherServlet.service` |
| Roteamento e expressões regulares | Verbo e caminho selecionam um método; templates extraem variáveis da URL. | `Router` e `/cursos/{id}` |
| Binding e conversão de tipos | Dados da URL, query string e corpo viram argumentos Java a cada requisição. | `bind`, `convert`, `@PathVariable`, `@RequestParam`, `@RequestBody` |
| Serialização e desserialização | Jackson transforma objetos em JSON e JSON em objetos. | `ObjectMapper` no dispatcher |
| DTOs e records | O contrato de entrada é separado do modelo devolvido; records simplificam os portadores de dados. | `CursoInput` e `Curso` |
| Validação e regras de negócio | Restrições estruturais são verificadas antes do controller; regras do caso de uso ficam no service. | `Validator` e limite de 2000 horas em `CursoService` |
| Tratamento centralizado de erros | Falhas conhecidas recebem status e corpo previsíveis; falhas inesperadas são registradas e viram 500. | `HttpException`, `HttpResult` e tratamento no dispatcher |
| Interceptadores e observabilidade | Comportamentos comuns podem envolver o processamento sem se repetir em todos os controllers. | `RequestInterceptor`, `LoggingInterceptor`, `X-Request-Id` |
| Concorrência e atomicidade | Requisições compartilham beans; estruturas concorrentes e operações atômicas protegem operações específicas. | `ConcurrentHashMap`, `AtomicLong` e `computeIfPresent` |
| Testes unitários e de integração | Testamos peças isoladas e o fluxo completo com Jetty real em porta livre. | `ApplicationContextTest`, `RouterTest` e testes HTTP |
| Build e empacotamento | Maven resolve dependências, compila, executa testes e gera um JAR executável com as bibliotecas necessárias. | `pom.xml`, Surefire e Shade |

### Leituras essenciais — nesta ordem

#### 1. Entender o que trafega pela rede

**Material em português, introdutório.** Leia [Mensagens HTTP — MDN](https://developer.mozilla.org/pt-BR/docs/Web/HTTP/Guides/Messages), [Métodos HTTP — MDN](https://developer.mozilla.org/pt-BR/docs/Web/HTTP/Reference/Methods) e [Códigos de status — MDN](https://developer.mozilla.org/pt-BR/docs/Web/HTTP/Reference/Status).

**Foco:** reconhecer método, caminho, headers, corpo e status. Revise GET, POST, PUT e DELETE, além dos status 200, 201, 204, 400, 404, 405, 415, 422 e 500.

**Prática:** use `curl -i` em um POST de curso e identifique cada parte da resposta. Explique por que o DELETE bem-sucedido retorna 204 sem corpo.

#### 2. Entender o formato JSON

**Material em português, introdutório.** Leia [Trabalhando com JSON — MDN](https://developer.mozilla.org/pt-BR/docs/Learn_web_development/Core/Scripting/JSON).

**Foco:** objetos, listas, strings, números e regras de sintaxe. Os exemplos usam JavaScript; no nosso projeto, a conversão é feita em Java pelo Jackson.

**Prática:** escreva um JSON válido para `CursoInput`, depois provoque um erro de sintaxe e observe a resposta 400.

#### 3. Distinguir IoC, DI e interfaces

**Material em inglês, intermediário.** Leia [Interfaces — Dev.java](https://dev.java/learn/interfaces/) e [Dependency Injection — Spring Framework](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html), especialmente a seção sobre injeção por construtor.

**Foco:** depender de uma abstração e receber colaboradores de fora. A documentação do Spring serve para comparar o conceito com nosso container; não é preciso adicionar Spring ao projeto.

**Prática:** construa manualmente repository → service → controller e explique onde existe DI mesmo sem `ApplicationContext`.

#### 4. Entender como as anotações ganham efeito

**Material em inglês, intermediário.** Leia [The Reflection API — Dev.java](https://dev.java/learn/reflection/) e [Reading Annotations — Dev.java](https://dev.java/learn/reflection/annotations/).

**Foco:** representar classes com `Class`, ler anotações em runtime e localizar construtores/métodos. Relacione isso com `getConstructors`, `getAnnotations` e `invoke` no projeto.

**Prática:** identifique quem lê `@Repository` e quem lê `@Route`. Explique por que as duas anotações não executam código sozinhas.

#### 5. Comparar nosso dispatcher com o Spring MVC

**Material em inglês, intermediário.** Leia [DispatcherServlet — Spring Framework](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html).

**Foco:** o padrão Front Controller e a coordenação do processamento de requisições. Compare responsabilidades, não equivalência de funcionalidades: nossa implementação é intencionalmente menor.

**Prática:** desenhe o caminho de um POST desde o Jetty até o repository e de volta ao JSON.

#### 6. Consolidar o aprendizado com testes

**Material em inglês, intermediário.** Consulte [JUnit 5.12.2 User Guide](https://docs.junit.org/5.12.2/user-guide/), nas seções “Writing Tests”, “Assertions” e “Asserting Expected Exceptions”. A versão corresponde à usada neste projeto.

**Foco:** `@Test`, `@BeforeEach`, `@AfterEach`, `assertEquals`, `assertSame` e `assertThrows`.

**Prática:** acrescente um teste que consulta um curso inexistente e verifica status 404 e corpo JSON de erro.

### Referências para aprofundamento

Todas as referências desta seção estão em inglês. Não é necessário lê-las integralmente: use a coluna “Foco de estudo” para localizar o assunto relevante.

| Assunto | Material | Foco de estudo e relação com o projeto |
|---|---|---|
| Escopos e singleton | [Bean Scopes — Spring Framework](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html) | Compare o singleton por container com os demais escopos. Nosso framework só implementa singleton. |
| Records | [Using Records to Model Immutable Data — Dev.java](https://dev.java/learn/records/) | Construtor canônico, accessors e campos finais. Um record não torna automaticamente mutáveis internos, como listas, imutáveis. |
| Reflection em records | [Working with Records — Dev.java](https://dev.java/learn/reflection/records/) | `getRecordComponents` e accessors, usados no Validator. |
| Servidor Jetty | [HTTP Server Libraries — Jetty 12](https://jetty.org/docs/jetty/12/programming-guide/server/http.html) | Busque `ServerConnector` e `ServletContextHandler`. O guia também aborda handlers nativos; nosso projeto usa Servlet. |
| Contrato Servlet | [HttpServlet — Jakarta Servlet 6.0](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/http/httpservlet) | Método `service`, request/response e cuidado com acesso concorrente à mesma instância. |
| JSON em Java | [Jackson Databind — ramo 2.21](https://github.com/FasterXML/jackson-databind/tree/2.21) | Exemplos de `ObjectMapper`, `readValue` e escrita de JSON, na linha principal usada pelo projeto. |
| Coleções concorrentes | [ConcurrentHashMap — Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html) | Estude `computeIfPresent` e os limites das garantias das operações concorrentes. |
| Operações atômicas | [AtomicLong — Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html) | Compare `incrementAndGet` com um incremento comum ao gerar IDs compartilhados. |
| Logs | [SLF4J Manual](https://www.slf4j.org/manual.html) | Logger e mensagens parametrizadas. No projeto, correlacione o log com `X-Request-Id`. |
| Maven | [Maven Getting Started Guide](https://maven.apache.org/guides/getting-started/) | Estrutura de diretórios, POM, dependências, compilação, testes e empacotamento. |

### Distinções que você precisa conseguir explicar

- **IoC, DI e inversão de dependência:** IoC transfere o controle da criação ao framework; DI fornece colaboradores ao objeto; inversão de dependência orienta a dependência de abstrações. São conceitos relacionados, mas diferentes.
- **DI e binding:** o service chega pelo construtor na inicialização; o DTO chega pelo corpo de cada requisição.
- **Singleton e thread safety:** compartilhar uma instância não torna seu estado seguro para acesso concorrente.
- **Validação e regra de negócio:** o DTO verifica formato e restrições da entrada; o service aplica a política do caso de uso. Nossa validação automática ocorre no pipeline HTTP.
- **Atomicidade e transação:** atualizar uma entrada do mapa de forma atômica não torna um conjunto de operações uma transação.
- **Jetty, Servlet e framework:** Jetty recebe e interpreta HTTP; Servlet fornece a interface com request/response; nosso framework escolhe e executa a ação da aplicação.
- **Interceptor e AOP:** o interceptor deste projeto envolve requisições explicitamente; não implementamos proxies para interceptar qualquer método de um bean.

### O que fica para estudos futuros

AOP com proxies, transações, banco de dados, ORM, autenticação/autorização, outros escopos, autoconfiguração condicional e callbacks de lifecycle dos beans não foram implementados. O Validator é próprio e não é uma implementação de Jakarta Bean Validation. Use essas diferenças para delimitar o que o mini framework ensina e o que um framework completo acrescenta.

### Revisão para entregar à turma

1. Explique quem cria `CursoController`, `CursoService` e `InMemoryCursoRepository`.
2. Mostre como o container descobre uma implementação de `CursoRepository`.
3. Descreva o resultado de registrar duas implementações dessa interface.
4. Explique por que uma anotação precisa de retenção em runtime para ser lida pelo nosso framework.
5. Desenhe o fluxo completo de `POST /cursos`, incluindo JSON, validação, status e headers.
6. Diferencie rota inexistente (404) de verbo não permitido (405).
7. Mostre por que guardar o DTO atual em um campo do controller causaria risco de concorrência.
8. Explique por que o teste de singleton usa `assertSame`, e não apenas `assertEquals`.
9. Identifique uma simplificação do projeto e proponha uma evolução com um teste que demonstre seu funcionamento.
10. Implemente o cadastro de alunos sem alterar as classes centrais do framework.
