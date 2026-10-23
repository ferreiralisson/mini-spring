# Mini Spring

API REST de catálogo de cursos construída com **Spring Boot 4.1.1**, **Spring MVC** e **Java 21**. O Spring fornece inicialização, injeção de dependências, roteamento HTTP, validação e serialização JSON; a API usa Tomcat embutido e armazena os cursos em memória.

## Pré-requisitos

- JDK 21
- Maven 3.9 ou superior

Confira a configuração local:

```sh
java -version
mvn -version
```

## Compilar e executar

```sh
mvn clean verify
java -jar target/mini-spring-1.0.0.jar
```

A aplicação escuta em `http://127.0.0.1:8080`. Para verificar:

```sh
curl -i http://127.0.0.1:8080/health
```

A resposta esperada é `200` com `{"status":"UP"}`. Os dados ficam em memória e são perdidos ao reiniciar a aplicação.

Também é possível executar o método `main` de `br.com.minispring.Application` pela IDE. Os testes HTTP iniciam o servidor Spring Boot em uma porta aleatória; não é necessário iniciar a aplicação manualmente.

### Configuração

As propriedades estão em `src/main/resources/application.properties`. Podem ser substituídas por argumentos, propriedades de sistema ou variáveis de ambiente:

```sh
java -Dserver.port=9090 -Dapp.name="Minha escola" -jar target/mini-spring-1.0.0.jar
```

As variáveis equivalentes são `SERVER_PORT` e `APP_NAME`. Por padrão, o servidor escuta apenas em `127.0.0.1`.

## API

Documentação interativa (Swagger UI, via springdoc-openapi): <http://127.0.0.1:8080/swagger-ui.html>. A especificação OpenAPI está em `/v3/api-docs`.

```sh
# Informações e health check
curl -i http://127.0.0.1:8080/
curl -i http://127.0.0.1:8080/health

# Criar: 201, Location e JSON
curl -i -X POST http://127.0.0.1:8080/cursos \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Java básico","cargaHoraria":40}'

# Listar (paginado: page começa em 0, size de 1 a 100; total em X-Total-Count), filtrar e consultar
curl -i 'http://127.0.0.1:8080/cursos?page=0&size=20'
curl -i 'http://127.0.0.1:8080/cursos?nome=java'
curl -i http://127.0.0.1:8080/cursos/1

# Atualizar: 200
curl -i -X PUT http://127.0.0.1:8080/cursos/1 \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Java avançado","cargaHoraria":80}'

# Atualização parcial: 200
curl -i -X PATCH http://127.0.0.1:8080/cursos/1 \
  -H 'Content-Type: application/json' \
  -d '{"cargaHoraria":60}'

# Excluir: 204, sem corpo
curl -i -X DELETE http://127.0.0.1:8080/cursos/1
```

`Curso` é um item de catálogo, não uma turma ou matrícula. `cargaHoraria` representa a duração total em horas inteiras: são aceitos valores de 1 a 2000. O nome é obrigatório e aparado antes de ser armazenado. No PATCH, campos omitidos ou explicitamente enviados como `null` permanecem inalterados; `{}` é aceito como operação sem efeito.

Corpos maiores que 1 MiB retornam `413`. A API também distingue requisições inválidas (`400`), conteúdo não suportado (`415`), rotas inexistentes (`404`) e métodos não suportados (`405`). Erros seguem a RFC 9457 (`application/problem+json`) com `type`, `title`, `status`, `detail`, `instance` e as extensões `timestamp`, `details` e `requestId`; cada resposta inclui o header `X-Request-Id`. Respostas GET de `/cursos` e `/cursos/{id}` trazem `ETag`; um `If-None-Match` coincidente retorna `304`.

## Organização

```text
src/main/java/br/com/minispring/
├── Application.java
├── controller/   Endpoints REST com Spring MVC
├── model/        Curso e DTOs de entrada validados com Jakarta Validation
├── repository/   Contrato e armazenamento concorrente em memória
├── service/      Regras de negócio
└── web/          Formato de erros, tratamento global e request ID
```

Consulte o [glossário](GLOSSARY.md) para os termos do domínio e o [guia de arquitetura](docs/ARQUITETURA.md) para detalhes do fluxo HTTP e dos componentes Spring utilizados.
