package io.github.qishr.cascara.lang.json.processor;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;

public class ArrayTests {
    private JsonAstParser parser;

    @BeforeEach
    void init() {
        parser = new JsonAstParser().setReporter(new StandardReporter().setLevel(Level.TRACE));
    }

    @Test
    void test_array_justMinus() {
        String text = "[-]";
        assertThrows(Exception.class, () -> parser.parse(text), "Should have failed");
    }

    @Test void test_i_structure_500_nested_arrays() {
        assertThrows(Exception.class, () -> parser.parse("[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[["));
    }
}
