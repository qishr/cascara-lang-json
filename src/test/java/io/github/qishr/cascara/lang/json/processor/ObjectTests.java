package io.github.qishr.cascara.lang.json.processor;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;

public class ObjectTests {
    private JsonAstParser parser;

    @BeforeEach
    void init() {
        parser = new JsonAstParser().setReporter(new StandardReporter().setLevel(Level.TRACE));
    }

    @Test void test_i_structure_UTF8_BOM_empty_object() {
        assertThrows(Exception.class, () -> parser.parse("\uFEFF{}"));
    }
}
