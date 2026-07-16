package io.github.qishr.cascara.lang.json.processor;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.json.util.JsonOptions;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;

public class UnicodeStringTests {
    private JsonAstParser parser;

    @BeforeEach
    void init() {
        parser = new JsonAstParser().setReporter(new StandardReporter().setLevel(Level.TRACE));
        parser.setOptions(new JsonOptions()
            .setAllowUnicode(true)
            .setValidateUnicode(true)
        );
    }

    @Test void test_i_object_key_lone_2nd_surrogate() {
        assertThrows(Exception.class, () -> parser.parse("{\"\\uDFAA\":0}"));
    }

    @Test void test_i_string_1st_surrogate_but_2nd_missing() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uDADA\"]"));
    }

    @Test void test_i_string_1st_valid_surrogate_2nd_invalid() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uD888\\u1234\"]"));
    }

    @Test void test_i_string_incomplete_surrogate_pair() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uDd1ea\"]"));
    }

    @Test void test_i_string_invalid_lonely_surrogate() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\ud800\"]"));
    }

    @Test void test_i_string_invalid_surrogate() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\ud800abc\"]"));
    }

    @Test void test_i_string_inverted_surrogates_U_1D11E() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uDd1e\\uD834\"]"));
    }

    @Test void test_i_string_lone_second_surrogate() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uDFAA\"]"));
    }




    @Test void test_i_string_lone_utf8_continuation_byte() {
        byte[] data = { '[', '"', (byte)0x81, '"', ']' };
        assertThrows(Exception.class, () -> parser.parse(data));
    }

    @Test void test_i_string_overlong_sequence_2_bytes() {
        byte[] data = { '[', '"', (byte)0xC0, (byte)0xAF, '"', ']' };
        assertThrows(Exception.class, () -> parser.parse(data));
    }


    @Test void test_i_string_overlong_sequence_6_bytes() {
        byte[] data = {
            '[', '"',
            (byte)0xFC, (byte)0x83, (byte)0xBF, (byte)0xBF, (byte)0xBF, (byte)0xBF,
            '"', ']'
        };
        assertThrows(Exception.class, () -> parser.parse(data));
    }


    @Test void test_i_string_overlong_sequence_6_bytes_null() {
        byte[] data = {
            '[', '"',
            (byte)0xFC, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80,
            '"', ']'
        };
        assertThrows(Exception.class, () -> parser.parse(data));
    }


    @Test void test_i_string_truncated_utf8() {
        byte[] data = { '[', '"', (byte)0xE0, (byte)0xFF, '"', ']' };
        assertThrows(Exception.class, () -> parser.parse(data));
    }


    @Test void test_i_string_not_in_unicode_range() {
        byte[] data = { '[', '"', (byte)0xF4, (byte)0xBF, (byte)0xBF, (byte)0xBF, '"', ']' };
        assertThrows(Exception.class, () -> parser.parse(data));
    }


    @Test void test_i_string_UTF8_surrogate_U_D800() {
        byte[] data = { '[', '"', (byte)0xED, (byte)0xA0, (byte)0x80, '"', ']' };
        assertThrows(Exception.class, () -> parser.parse(data));
    }


    @Test void test_i_string_UTF8_invalid_sequence() {
        byte[] data = {
            '[', '"',
            (byte)0xE6, (byte)0x97, (byte)0xA5, (byte)0xD1, (byte)0x88, (byte)0xFA,
            '"', ']'
        };
        assertThrows(Exception.class, () -> parser.parse(data));
    }


    @Test void test_i_string_iso_latin_1() {
        byte[] data = { '[', '"', (byte)0xE9, '"', ']' };
        assertThrows(Exception.class, () -> parser.parse(data));
    }


    @Test void test_i_string_invalid_utf8() {
        byte[] data = { '[', '"', (byte)0xFF, '"', ']' };
        assertThrows(Exception.class, () -> parser.parse(data));
    }



    @Test void test_i_string_UTF16LE_with_BOM() {
        byte[] data = { '"', (byte)0xFE, (byte)0xFF, '"' };
        assertThrows(Exception.class, () -> parser.parse(data));
    }


}
