# Decisões de arquitetura

## 1. IoC é quem controla; DI é como a dependência chega

Sem o container, o ponto de entrada poderia construir tudo assim:

```java
var repository = new InMemoryCursoRepository();
var service = new CursoService(repository);
var controller = new CursoController(service);
```

Isso já é injeção de dependência: o service recebe o repository de fora. O container automatiza esse trabalho e assume o controle da criação (IoC). A anotação não é a injeção; é um metadado usado para decidir quais classes registrar.

`ComponentScanner` encontra classes do pacote da aplicação e seus subpacotes. Ele reconhece `@Component` e anotações diretamente anotadas com `@Component`. Suporta diretórios de classes e JARs comuns; não suporta JARs aninhados, JPMS, carregadores especiais nem composição recursiva de anotações.

`ApplicationContext` mantém definições de classes e instâncias separadamente. Ao pedir um tipo, procura uma única classe atribuível a ele. Se necessário, resolve recursivamente os argumentos do único construtor público e guarda o resultado. A pilha `creating` revela ciclos como `A → B → A`. A inicialização antecipada detecta erros antes de iniciar o Jetty.

Singleton significa uma instância por contexto. Não é o padrão de uma variável `static` global. Dois contextos podem ter duas instâncias da mesma classe.

## 2. Servidor embutido e Servlet

`MiniApplication` cria um `Server` Jetty e um conector HTTP local. O `ServletContextHandler` registra nosso Servlet em `/*`. Não há necessidade de instalar um servidor separado ou produzir um WAR.

O Jetty recebe conexões, interpreta HTTP e oferece `HttpServletRequest` / `HttpServletResponse`. Nosso `DispatcherServlet` resolve o restante: interceptação, rota, argumentos, validação, invocação e serialização. Jetty 12 também possui uma API de handlers própria; a opção Servlet torna explícita a ponte usada por frameworks MVC Servlet.

O listener usa `127.0.0.1`. Para acesso de outros computadores, altere conscientemente o host no bootstrap. Os testes usam porta `0`, permitindo ao sistema operacional escolher uma porta livre.

## 3. Binding não é DI

O construtor de `CursoController` recebe um service durante a inicialização: isso é DI.

O método `create` recebe um `CursoInput` a cada POST: isso é binding de dados HTTP. Esses argumentos vêm de lugares diferentes e possuem ciclos de vida diferentes.

As variáveis de caminho e query params suportam `String`, `int` e `long`, incluindo wrappers. Um query param ausente usa `defaultValue`, que por padrão é uma string vazia. Para parâmetros numéricos opcionais, declare explicitamente um default numérico. Nomes não dependem do nome do parâmetro Java: a anotação deve fornecê-los.

O JSON usa Jackson para tratar escapes, Unicode e tipos. O mapper rejeita campos desconhecidos, conteúdo extra após o documento, conversão de strings para números, valores nulos para primitivas e frações para inteiros. O framework limita a leitura do corpo a 1 MiB mais um byte para detectar excesso.

A validação deste projeto é própria; não é Jakarta Bean Validation. Ela verifica `@NotBlank` e `@Positive` nos componentes do primeiro nível de records. Não percorre grafos ou coleções aninhadas e não valida métodos chamados fora do dispatcher.

## 4. Roteamento e reflection

As rotas são registradas uma vez. O caminho `/cursos/{id}` vira uma expressão regular; os grupos capturados alimentam os argumentos. Segmentos literais são escapados para não serem interpretados como expressões regulares.

Rotas com mais segmentos literais têm prioridade; templates com a mesma forma e método são rejeitados mesmo que mudem o nome da variável. Empates entre outros templates são ordenados pelo texto do caminho. Isso é uma regra didática limitada, não um algoritmo completo de especificidade. Evite templates sobrepostos como `/{x}/detalhes` e `/cursos/{y}`.

Uma barra final é ignorada. Não há curingas, negociação de conteúdo, versionamento de rotas ou HEAD/OPTIONS automáticos. Métodos sem mapeamento recebem 405 quando o caminho existe, com `Allow`, ou 404 quando não existe.

`Method.invoke` encapsula exceções da aplicação em `InvocationTargetException`. Desencapsular a causa permite preservar um `HttpException` intencional; outros erros viram 500 e são registrados no servidor.

## 5. Camadas e acoplamento

O controller descreve a interface HTTP. O service normaliza o nome e aplica a regra de no máximo 2000 horas. O repository armazena cursos e gera IDs. `CursoInput` impede que o cliente determine o ID de criação.

Para manter o exemplo enxuto, o service usa `HttpException`, acoplando a aplicação ao contrato HTTP do mini framework. Uma evolução é lançar exceções de domínio (`CursoNaoEncontrado`, `CargaHorariaExcedida`) e registrar mapeadores de exceção na camada web. Essa mudança separa as regras de domínio dos detalhes do transporte HTTP.

Não há transação: `ConcurrentHashMap` torna operações individuais seguras; não torna uma sequência de operações em vários registros atômica. O `computeIfPresent` evita recriar um curso removido durante uma atualização concorrente. A listagem não promete um snapshot transacional.

## 6. Interceptadores e observabilidade

Interceptors executam `before` na ordem de registro e `after` na ordem inversa, para os que concluíram `before`. O logging guarda início e ID como atributos da requisição. Guardá-los em campos do interceptor misturaria requisições concorrentes, pois a instância é compartilhada.

Os interceptadores são registrados explicitamente no bootstrap. Este projeto não os descobre por scanning nem implementa proxies AOP. O `after` mede o processamento no Servlet, não o recebimento completo pelo cliente. O header `X-Request-Id` liga a resposta ao log e ao corpo de erro.

## 7. Ciclo de vida e extensões

O bootstrap só abre a porta após montar o contexto e validar as rotas. O servidor pode ser encerrado com `close()` nos testes, e o hook do Jetty atende ao encerramento da JVM. Beans não possuem callbacks próprios de inicialização/destruição.

Extensões possíveis, uma por vez: callbacks de lifecycle; `@Qualifier`; escopo prototype; mapeamento de exceções; filtros de autenticação; paginação; JDBC; transações; proxies por interface; testes de arquitetura. Adicione um cenário observável e um teste para cada extensão antes de expandir a abstração.
