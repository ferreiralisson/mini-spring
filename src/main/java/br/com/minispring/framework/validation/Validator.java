package br.com.minispring.framework.validation;

import br.com.minispring.framework.web.HttpException;
import java.util.ArrayList;

/** Validação intencionalmente pequena: somente componentes de records, sem recursão. */
public final class Validator {
    private Validator() {}
    public static void validate(Object value) {
        if (value == null) throw new HttpException(400, "Corpo JSON obrigatório");
        if (!value.getClass().isRecord()) return;
        var errors = new ArrayList<String>();
        for (var component : value.getClass().getRecordComponents()) {
            try {
                // Em um record, o componente nome tem o accessor nome(). Invocá-lo por reflection
                // permite validar DTOs diferentes sem escrever um if para cada classe de aplicação.
                Object field = component.getAccessor().invoke(value);
                if (component.isAnnotationPresent(NotBlank.class)
                    && (!(field instanceof String text) || text.isBlank())) {
                    errors.add(component.getName() + " não pode estar em branco");
                }
                if (component.isAnnotationPresent(Positive.class)
                    && (!(field instanceof Number number) || number.doubleValue() <= 0)) {
                    errors.add(component.getName() + " deve ser positivo");
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Não foi possível validar " + component.getName(), e);
            }
        }
        // Acumular erros permite ao aluno/cliente corrigir vários campos na mesma tentativa.
        if (!errors.isEmpty()) throw new HttpException(400, "Falha de validação", errors);
    }
}
