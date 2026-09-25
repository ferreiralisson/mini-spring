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
