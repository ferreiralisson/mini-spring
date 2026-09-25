# Conceitos e materiais de apoio — Mini Spring com Jetty

Ao concluir o projeto, você deverá conseguir explicar como o framework cria objetos, conecta suas dependências e transforma uma requisição HTTP em uma chamada Java e uma resposta JSON.

Os materiais abaixo complementam a implementação deste projeto. Os links foram consultados em 17/09/2026. Comece pelas leituras essenciais; use as referências mais extensas para aprofundar um assunto por vez.

## Conceitos apresentados e onde encontrá-los

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

## Leituras essenciais — nesta ordem

### 1. Entender o que trafega pela rede

**Material em português, introdutório.** Leia [Mensagens HTTP — MDN](https://developer.mozilla.org/pt-BR/docs/Web/HTTP/Guides/Messages), [Métodos HTTP — MDN](https://developer.mozilla.org/pt-BR/docs/Web/HTTP/Reference/Methods) e [Códigos de status — MDN](https://developer.mozilla.org/pt-BR/docs/Web/HTTP/Reference/Status).

**Foco:** reconhecer método, caminho, headers, corpo e status. Revise GET, POST, PUT e DELETE, além dos status 200, 201, 204, 400, 404, 405, 415, 422 e 500.

**Prática:** use `curl -i` em um POST de curso e identifique cada parte da resposta. Explique por que o DELETE bem-sucedido retorna 204 sem corpo.

### 2. Entender o formato JSON

**Material em português, introdutório.** Leia [Trabalhando com JSON — MDN](https://developer.mozilla.org/pt-BR/docs/Learn_web_development/Core/Scripting/JSON).

**Foco:** objetos, listas, strings, números e regras de sintaxe. Os exemplos usam JavaScript; no nosso projeto, a conversão é feita em Java pelo Jackson.

**Prática:** escreva um JSON válido para `CursoInput`, depois provoque um erro de sintaxe e observe a resposta 400.

### 3. Distinguir IoC, DI e interfaces

**Material em inglês, intermediário.** Leia [Interfaces — Dev.java](https://dev.java/learn/interfaces/) e [Dependency Injection — Spring Framework](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html), especialmente a seção sobre injeção por construtor.

**Foco:** depender de uma abstração e receber colaboradores de fora. A documentação do Spring serve para comparar o conceito com nosso container; não é preciso adicionar Spring ao projeto.

**Prática:** construa manualmente repository → service → controller e explique onde existe DI mesmo sem `ApplicationContext`.

### 4. Entender como as anotações ganham efeito

**Material em inglês, intermediário.** Leia [The Reflection API — Dev.java](https://dev.java/learn/reflection/) e [Reading Annotations — Dev.java](https://dev.java/learn/reflection/annotations/).

**Foco:** representar classes com `Class`, ler anotações em runtime e localizar construtores/métodos. Relacione isso com `getConstructors`, `getAnnotations` e `invoke` no projeto.

**Prática:** identifique quem lê `@Repository` e quem lê `@Route`. Explique por que as duas anotações não executam código sozinhas.

### 5. Comparar nosso dispatcher com o Spring MVC

**Material em inglês, intermediário.** Leia [DispatcherServlet — Spring Framework](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html).

**Foco:** o padrão Front Controller e a coordenação do processamento de requisições. Compare responsabilidades, não equivalência de funcionalidades: nossa implementação é intencionalmente menor.

**Prática:** desenhe o caminho de um POST desde o Jetty até o repository e de volta ao JSON.

### 6. Consolidar o aprendizado com testes

**Material em inglês, intermediário.** Consulte [JUnit 5.12.2 User Guide](https://docs.junit.org/5.12.2/user-guide/), nas seções “Writing Tests”, “Assertions” e “Asserting Expected Exceptions”. A versão corresponde à usada neste projeto.

**Foco:** `@Test`, `@BeforeEach`, `@AfterEach`, `assertEquals`, `assertSame` e `assertThrows`.

**Prática:** acrescente um teste que consulta um curso inexistente e verifica status 404 e corpo JSON de erro.

## Referências para aprofundamento

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

## Distinções que você precisa conseguir explicar

- **IoC, DI e inversão de dependência:** IoC transfere o controle da criação ao framework; DI fornece colaboradores ao objeto; inversão de dependência orienta a dependência de abstrações. São conceitos relacionados, mas diferentes.
- **DI e binding:** o service chega pelo construtor na inicialização; o DTO chega pelo corpo de cada requisição.
- **Singleton e thread safety:** compartilhar uma instância não torna seu estado seguro para acesso concorrente.
- **Validação e regra de negócio:** o DTO verifica formato e restrições da entrada; o service aplica a política do caso de uso. Nossa validação automática ocorre no pipeline HTTP.
- **Atomicidade e transação:** atualizar uma entrada do mapa de forma atômica não torna um conjunto de operações uma transação.
- **Jetty, Servlet e framework:** Jetty recebe e interpreta HTTP; Servlet fornece a interface com request/response; nosso framework escolhe e executa a ação da aplicação.
- **Interceptor e AOP:** o interceptor deste projeto envolve requisições explicitamente; não implementamos proxies para interceptar qualquer método de um bean.

## O que fica para estudos futuros

AOP com proxies, transações, banco de dados, ORM, autenticação/autorização, outros escopos, autoconfiguração condicional e callbacks de lifecycle dos beans não foram implementados. O Validator é próprio e não é uma implementação de Jakarta Bean Validation. Essas diferenças delimitam os recursos deste projeto em relação a um framework completo.
