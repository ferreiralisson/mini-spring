package br.com.minispring.failurefixture;

import br.com.minispring.framework.annotation.*;

/** Componente exclusivo dos testes; nunca é incluído no JAR da aplicação. */
@RestController
public final class FailureController {
    @Route(method = "GET", path = "/failure")
    public Object fail() { throw new IllegalStateException("detalhe-interno-do-teste"); }
}
