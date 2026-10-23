package br.com.minispring.exemplo.model;

import br.com.minispring.exemplo.web.ApiException;
import tools.jackson.databind.JsonNode;

import java.util.Set;

// Null representa campo omitido; null explícito no JSON é rejeitado no factory.
public record CursoPatchInput(String nome, Integer cargaHoraria) {
    private static final Set<String> FIELDS = Set.of("nome", "cargaHoraria");

    public static CursoPatchInput from(JsonNode patch) {
        if (patch == null || !patch.isObject()) throw invalid("O patch deve ser um objeto JSON");
        patch.propertyNames().forEach(field -> {
            if (!FIELDS.contains(field)) throw invalid("Campo desconhecido: " + field);
        });

        String nome = null;
        if (patch.has("nome")) {
            JsonNode value = patch.get("nome");
            if (value.isNull() || !value.isTextual() || value.textValue().isBlank())
                throw invalid("nome deve ser uma string não vazia");
            nome = value.textValue();
        }

        Integer cargaHoraria = null;
        if (patch.has("cargaHoraria")) {
            JsonNode value = patch.get("cargaHoraria");
            if (value.isNull() || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0)
                throw invalid("cargaHoraria deve ser um inteiro positivo");
            cargaHoraria = value.intValue();
        }
        return new CursoPatchInput(nome, cargaHoraria);
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest(message);
    }
}
