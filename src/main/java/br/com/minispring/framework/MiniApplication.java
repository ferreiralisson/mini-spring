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
