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
