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
