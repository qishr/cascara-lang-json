// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


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
