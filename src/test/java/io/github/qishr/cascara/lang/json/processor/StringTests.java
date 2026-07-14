package io.github.qishr.cascara.lang.json.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;

public class StringTests {
    private JsonAstParser parser;

    @BeforeEach
    void init() {
        parser = new JsonAstParser().setReporter(new StandardReporter().setLevel(Level.TRACE));
    }

    @Test
    void test_stringContaining_quotes() {
        String text = "{\"name\": \"one \\\"two\\\" three\"}";
        JsonAstParser parser = new JsonAstParser();
        JsonMapNode json = (JsonMapNode)parser.parse(text);
        String name = json.getString("name");
        assertEquals("one \"two\" three", name);
    }

    @Test
    void test_array_commaAfterClose() {
        String text = "[\"\"],";
        assertThrows(Exception.class, () -> parser.parse(text), "Should have failed");
    }

    @Test
    void test_string_backslash() {
        String text = "[\"\\00\"]";
        assertThrows(Exception.class, () -> parser.parse(text), "Should have failed");
    }

    @Test void test_i_string_incomplete_surrogate_and_escape_valid() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uD800\n\"]"));
    }

    @Test void test_i_string_incomplete_surrogates_escape_valid() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uD800\\uD800\n\"]"));
    }

    @Test void test_i_string_utf16BE_no_BOM() {
        byte[] data = {
            0x00, '[', 0x00, '"',
            0x00, 0x00,       // U+0000
            0x00, (byte)0xE9, // U+00E9
            0x00, '"', 0x00, ']'
        };
        assertThrows(Exception.class, () -> parser.parse(data));
    }


    @Test void test_i_string_utf16LE_no_BOM() {
        byte[] data = {
            '[', 0x00, '"', 0x00,
            (byte)0xE9, 0x00,
            '"', 0x00, ']', 0x00
        };
        assertThrows(Exception.class, () -> parser.parse(data));
    }

}
