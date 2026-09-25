# Roteiro de oito encontros

Sugestão: encontros de 60–90 minutos. Os alunos precisam conhecer classes, interfaces, construtores, coleções e exceções. Records, reflection e HTTP podem ser apresentados durante o projeto.

A implementação final serve como referência. Para uma construção ao vivo, siga a sequência abaixo e mostre apenas as classes da etapa; não é necessário explicar todo o código na primeira aula.

## Aula 1 — Quem cria os objetos?

**Objetivo:** distinguir dependência, injeção e inversão de controle.

1. Mostre `CursoRepository`, `InMemoryCursoRepository` e `CursoService`.
2. Construa manualmente repository → service → controller em um pequeno teste.
3. Compare receber a interface pelo construtor com executar `new InMemoryCursoRepository()` dentro do service.
4. Substitua a implementação por um fake que devolve um curso fixo.

**Pergunta:** é possível usar DI sem anotações e sem framework? O exemplo manual demonstra que sim.

**Entrega:** teste do service com fake, sem servidor e sem container.

## Aula 2 — Anotações, classpath e reflection

**Objetivo:** entender que metadados precisam de um intérprete.

Leia `Component`, `Service`, `ComponentScanner`. Explore `getAnnotations`, `getConstructors` e `newInstance`. Explique `RetentionPolicy.RUNTIME`, pacotes, classpath e a diferença entre diretório de classes e JAR.

**Experimento:** retire `@Repository` de `InMemoryCursoRepository` e observe a falha de resolução. Reponha a anotação. Coloque um breakpoint em `ComponentScanner.scan` e liste os tipos descobertos.

**Entrega:** adicionar um componente simples e provar que ele é descoberto sem alterar o scanner.

## Aula 3 — Construindo o container IoC

**Objetivo:** implementar resolução recursiva e escopo singleton.

Leia `ApplicationContext.getBean`. Desenhe o grafo controller → service → repository e acompanhe a criação do fim para o início. Explique a diferença entre definição e instância.

**Experimentos:** registre uma segunda implementação de repository; crie temporariamente um ciclo A → B → A; declare dois construtores públicos. Observe os erros antes do servidor iniciar. Use `ApplicationContextTest` como especificação desses casos.

**Entrega:** teste com `assertSame` mostrando que duas dependências recebem a mesma instância. Extensão opcional: projetar `@Qualifier` para resolver ambiguidade sem escolher silenciosamente a primeira implementação.

## Aula 4 — Da conexão ao método Java

**Objetivo:** separar servidor HTTP, Servlet e roteador.

Leia `MiniApplication`, depois `Router` e `DispatcherServlet.service`. Execute o JAR e envie `GET /health`. Coloque breakpoints em `service`, `match` e `Method.invoke`.

Observe método, caminho, headers e status no `curl -i`. Mostre como `/cursos/{id}` captura um valor, e por que o controller não precisa conhecer sockets.

**Experimentos:** rota inexistente gera 404; PATCH em `/cursos/1` gera 405 com `Allow`; rota duplicada falha na inicialização.

**Entrega:** criar `GET /sobre` retornando um record com nome e versão, sem modificar o dispatcher.

## Aula 5 — JSON, DTO e binding

**Objetivo:** acompanhar texto da rede → objeto Java → texto JSON.

Leia `DispatcherServlet.bind`, `CursoInput` e `CursoController.create`. Depure um POST e observe o DTO antes da chamada ao controller. O ID é gerado no repository, não informado pelo cliente.

**Experimentos:** JSON incompleto, `cargaHoraria` como texto, campo desconhecido, ID não numérico e query param `nome`. Compare 200, 201 com `Location` e 204 sem corpo.

**Entrega:** novo campo no DTO e no modelo, com teste de ida e volta HTTP. Explique por que concatenar strings manualmente para montar JSON falha com aspas e quebras de linha.

## Aula 6 — Validação e erros previsíveis

**Objetivo:** separar forma da entrada, regra de negócio e falha inesperada.

Leia `Validator`, `HttpException` e o tratamento de `InvocationTargetException`. Envie nome vazio e carga horária zero: 400 com detalhes. Envie 2001 horas: 422. Consulte curso inexistente: 404.

**Experimento:** lance temporariamente `IllegalStateException` em um controller e observe 500 genérico no cliente e causa no log; reverta a alteração.

**Entrega:** implementar `@MaxLength` para records e um teste HTTP. Extensão arquitetural: substituir `HttpException` no service por exceções de domínio e mapeá-las na camada web.

## Aula 7 — Concorrência e preocupações transversais

**Objetivo:** entender o risco de guardar estado de requisição em singletons.

Leia `LoggingInterceptor` e `InMemoryCursoRepository`. Acompanhe `X-Request-Id`. Explique por que os atributos do request são diferentes de campos do interceptor.

Execute `concurrentRequestsProduceUniqueIds`. Compare a geração com `AtomicLong` e uma implementação ingênua com `long++`, sem assumir que uma corrida aparece em toda execução. Discuta por que uma coleção concorrente não equivale a uma transação.

**Entrega:** interceptor que adiciona um header `X-Aula`, registrado no bootstrap. Acrescente um teste que verifica o header, inclusive em respostas de erro.

## Aula 8 — Configuração, empacotamento e autonomia

**Objetivo:** colocar a aplicação inteira em execução e demonstrar o que foi entendido.

Leia `AppConfig` e `Application`. Execute `mvn clean verify`, rode o JAR e altere a porta com `-Dserver.port=9090`. Observe que o scanner precisa funcionar dentro do JAR. Encerre e reinicie para demonstrar a perda de dados em memória.

**Projeto final:** implementar cadastro de alunos em novos controller, service, interface de repository e implementação, mantendo o código central do framework intacto. Inclua GET, POST, consulta por ID, validação e testes HTTP.

**Critérios de conclusão:**

- Explica quem cria cada objeto e em que momento.
- Diferencia DI de binding de argumentos HTTP.
- Percorre o fluxo de um POST e identifica quem transforma JSON.
- Justifica os status usados e consegue provocar erros previsíveis.
- Explica por que beans singleton exigem atenção à concorrência.
- Executa os testes e o JAR fora da IDE.
- Adiciona um novo domínio usando os pontos de extensão existentes.

## Trilha avançada

1. **Lifecycle:** `@PostConstruct` e encerramento de beans que implementem `AutoCloseable`.
2. **Escopos:** prototype e request, discutindo dependências de escopos diferentes.
3. **Persistência:** implementação JDBC de `CursoRepository`, migrações e testes com banco.
4. **Transações:** duas gravações que devem confirmar ou reverter juntas.
5. **AOP:** proxy JDK para medir métodos de uma interface; discutir chamadas internas que não passam pelo proxy.
6. **Segurança:** autenticação e autorização como conceitos distintos, com interceptor e testes.
7. **Comparação com Spring:** reimplementar apenas a API de cursos com Spring e mapear cada responsabilidade ao que foi construído aqui.
