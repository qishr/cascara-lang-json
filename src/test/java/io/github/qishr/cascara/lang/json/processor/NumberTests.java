package io.github.qishr.cascara.lang.json.processor;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;

public class NumberTests {
    private JsonAstParser parser;

    @BeforeEach
    void init() {
        parser = new JsonAstParser().setReporter(new StandardReporter().setLevel(Level.TRACE));
    }

    @Test void test_n_number_plus_1() {
        assertThrows(Exception.class, () -> parser.parse("[+1]"));
    }

    @Test void test_n_number_minus_01() {
        assertThrows(Exception.class, () -> parser.parse("[-01]"));
    }

    @Test void test_n_number_minus_2_dot() {
        assertThrows(Exception.class, () -> parser.parse("[-2.]"));
    }

    @Test void test_n_number_dot2e_minus3() {
        assertThrows(Exception.class, () -> parser.parse("[.2e-3]"));
    }

    @Test void test_n_number_0_3e_plus() {
        assertThrows(Exception.class, () -> parser.parse("[0.3e+]"));
    }

    @Test void test_n_number_0_3e() {
        assertThrows(Exception.class, () -> parser.parse("[0.3e]"));
    }

    @Test void test_n_number_0E_plus() {
        assertThrows(Exception.class, () -> parser.parse("[0E+]"));
    }

    @Test void test_n_number_0E() {
        assertThrows(Exception.class, () -> parser.parse("[0E]"));
    }

    @Test void test_n_number_0e_plus() {
        assertThrows(Exception.class, () -> parser.parse("[0e+]"));
    }

    @Test void test_n_number_0e() {
        assertThrows(Exception.class, () -> parser.parse("[0e]"));
    }

    @Test void test_n_number_1_0e_plus() {
        assertThrows(Exception.class, () -> parser.parse("[1.0e+]"));
    }

    @Test void test_n_number_1_0e_minus() {
        assertThrows(Exception.class, () -> parser.parse("[1.0e-]"));
    }

    @Test void test_n_number_1_0e() {
        assertThrows(Exception.class, () -> parser.parse("[1.0e]"));
    }

    @Test void test_n_number_9_dot_e_plus() {
        assertThrows(Exception.class, () -> parser.parse("[9.e+]"));
    }

    @Test void test_n_number_neg_int_starting_with_zero() {
        assertThrows(Exception.class, () -> parser.parse("[-012]"));
    }

    @Test void test_n_number_neg_real_without_int_part() {
        assertThrows(Exception.class, () -> parser.parse("[-.123]"));
    }

    @Test void test_n_number_real_without_fractional_part() {
        assertThrows(Exception.class, () -> parser.parse("[1.]"));
    }

    @Test void test_n_number_starting_with_dot() {
        assertThrows(Exception.class, () -> parser.parse("[.123]"));
    }

    @Test void test_n_number_with_leading_zero() {
        assertThrows(Exception.class, () -> parser.parse("[012]"));
    }

    @Test void test_n_string_unescaped_ctrl_char() {
        assertThrows(Exception.class, () -> parser.parse("[\"a\u0000a\"]"));
    }

    @Test void test_n_string_unescaped_newline() {
        assertThrows(Exception.class, () -> parser.parse("[\"new\nline\"]"));
    }

    @Test void test_n_string_unescaped_tab() {
        assertThrows(Exception.class, () -> parser.parse("[\"a\tb\"]"));
    }

    @Test void test_n_number_0_dot_e1() {
        assertThrows(Exception.class, () -> parser.parse("[0.e1]"));
    }

    @Test void test_n_number_2_dot_e_plus3() {
        assertThrows(Exception.class, () -> parser.parse("[2.e+3]"));
    }

    @Test void test_n_number_2_dot_e_minus3() {
        assertThrows(Exception.class, () -> parser.parse("[2.e-3]"));
    }

    @Test void test_n_number_2_dot_e3() {
        assertThrows(Exception.class, () -> parser.parse("[2.e3]"));
    }
}
