# Cursos API com Spring Boot

API REST didática para um catálogo de cursos, implementada com **Java 21**, **Spring Boot 4.1.2**, **Spring MVC** e **Tomcat embutido**. O projeto usa o container, o roteamento, o binding, a serialização JSON e a validação fornecidos pelo Spring; os cursos são mantidos em memória e se perdem ao reiniciar a aplicação.

## Executar

Pré-requisitos: JDK 21 e Maven 3.9+. A primeira execução precisa baixar dependências.

```sh
mvn clean verify
mvn spring-boot:run
```

Ou execute o JAR empacotado:

```sh
java -jar target/mini-spring-1.0.0.jar
```

A API fica em `http://127.0.0.1:8080`. Outra porta:

```sh
SERVER_PORT=9090 mvn spring-boot:run
# Também é possível usar -Dserver.port=9090
```

O Spring Boot carrega `application.properties`, variáveis de ambiente e propriedades da JVM usando seu mecanismo padrão de configuração.

## API

A especificação completa OpenAPI 3.1 está em [openapi.yaml](openapi.yaml).

```sh
# Informações e saúde
curl -i http://127.0.0.1:8080/
curl -i http://127.0.0.1:8080/health

# Criar: 201, Location e representação JSON
curl -i -X POST http://127.0.0.1:8080/api/v1/cursos \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Java básico","cargaHoraria":40}'

# Listar a v1: página zero-based, tamanho padrão 20 (máximo 100)
curl -i 'http://127.0.0.1:8080/api/v1/cursos?page=0&size=20&sort=nome,asc&nome=java'

# Consultar e substituir todos os campos editáveis
curl -i http://127.0.0.1:8080/api/v1/cursos/1
curl -i -X PUT http://127.0.0.1:8080/api/v1/cursos/1 \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Java avançado","cargaHoraria":80}'

# Atualizar parcialmente: campos omitidos permanecem inalterados
curl -i -X PATCH http://127.0.0.1:8080/api/v1/cursos/1 \
  -H 'Content-Type: application/merge-patch+json' \
  -d '{"nome":"Java avançado"}'

# HEAD não envia corpo; OPTIONS informa Allow
curl -I http://127.0.0.1:8080/api/v1/cursos/1
curl -i -X OPTIONS http://127.0.0.1:8080/api/v1/cursos/1

# Excluir: 204 sem corpo
curl -i -X DELETE http://127.0.0.1:8080/api/v1/cursos/1
```

`/api/v1/cursos` é a rota canônica. Os caminhos `/cursos` e `/cursos/{id}` permanecem como aliases: a listagem legada mantém resposta em forma de array; a v1 devolve `items`, `page`, `size`, `totalElements` e `totalPages`.

PATCH usa JSON Merge Patch (RFC 7396). Campos ausentes são preservados; `null`, campos desconhecidos e valores inválidos são rejeitados. O Curso resultante é validado antes da atualização atômica. PUT continua sendo substituição completa.

Na listagem v1, `page` começa em 0; `size` tem padrão 20 e máximo 100; `sort` aceita `id`, `nome` ou `cargaHoraria` com direção `asc`/`desc`. O padrão é `id,asc`, com ID como desempate. O filtro `nome` é aplicado antes da paginação. Parâmetros inválidos retornam 400; páginas sem itens retornam 200 com metadados.

Também há exemplos executáveis em [requests.http](requests.http).

## Organização do código

```text
src/main/java/br/com/minispring/exemplo/
├── Application.java       Inicialização Spring Boot
├── controller/            Endpoints Spring MVC e versão da API
├── model/                 Curso, DTOs e página de resposta
├── repository/            Interface e implementação em memória
├── service/               Regras de aplicação e domínio
└── web/                   Erros da API e filtro de request-id/limite do corpo
```

O Spring Boot fornece IoC/DI, Spring MVC, binding HTTP, conversão JSON, validação Jakarta e servidor Tomcat. O código específico da aplicação mantém as regras do catálogo, o contrato de erro JSON, o `X-Request-Id` e o limite de 1 MiB para corpos de escrita.

## Erros HTTP

| Status | Situação |
|---|---|
| 400 | JSON, parâmetros ou validação inválidos |
| 404 | Rota ou curso inexistente |
| 405 | Método não permitido; inclui `Allow` |
| 413 | Corpo maior que 1 MiB |
| 415 | Tipo de mídia incompatível |
| 422 | Carga horária acima de 2000 horas |
| 500 | Falha inesperada; detalhes ficam no log |

As respostas de erro da aplicação têm `timestamp`, `status`, `message`, `path`, `details` e `requestId`. `X-Request-Id` é retornado em todas as respostas processadas pela aplicação. O endpoint `/health` indica que a aplicação responde; não verifica dependências externas.

## Testes

```sh
mvn test
```

A suíte sobe Spring Boot e Tomcat em porta aleatória e testa os contratos HTTP reais, incluindo CRUD, validação, PATCH, paginação, erros, métodos HEAD/OPTIONS e concorrência.
