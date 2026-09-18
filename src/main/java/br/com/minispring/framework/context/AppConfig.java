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
