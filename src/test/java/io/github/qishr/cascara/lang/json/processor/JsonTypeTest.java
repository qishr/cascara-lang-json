package io.github.qishr.cascara.lang.json.processor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;

public class JsonTypeTest {
    @Test
    void booleanTest() {
        String json = """
            {
                "status": true
            }
            """;

        JsonNode doc = new JsonParser().parse(json);
        if (doc instanceof JsonMapNode map) {
            if (map.get("status") instanceof JsonScalarNode status) {
                Object o = status.getPrimitive();
                assertInstanceOf(Boolean.class, o);
            }
        }
    }
}
