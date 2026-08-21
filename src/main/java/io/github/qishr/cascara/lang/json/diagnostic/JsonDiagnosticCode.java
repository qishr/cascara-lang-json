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


package io.github.qishr.cascara.lang.json.diagnostic;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;

public enum JsonDiagnosticCode implements DiagnosticCode {

    // Tokenizer
    EXPECTED_CLOSE_BRACE("JSON-102", "Expected '}'"),
    EXPECTED_EOS("JSON-103", "Expected end of stream"),
    EXPECTED_SCALAR("JSON-104", "Expected scalar."),
    EXPECTED_COLON_FLOW_MAP("JSON-105", "Expected ':' after key in flow map"),
    EXPECTED_OPEN_BRACE("JSON-106", "Expected '{'."),
    EXPECTED_CLOSE_BRACKET("JSON-107", "Expected ']'"),
    EXPECTED_OPEN_BRACKET("JSON-108", "Expected '['"),
    EXPECTED_COLON_AFTER_MAP_KEY("JSON-109", "Expected ':' after key"),
    INVALID_NUMBER("JSON-110", "Invalid number"), // TODO: Put the number in the error message
    UNEXPECTED_UNQUOTED_STRING_VALUE("JSON-112","Unexpected unquoted string value: '{0}'"),
    MISSING_INTEGER_PART("","Missing integer part in JSON numbe"),
    MISSING_FRACTIONAL_PART("","Missing fractional part in JSON number"),
    MISSING_EXPONENT("","Exponent missing in JSON number"),
    MISSING_EXPONENT_DIGITS("","Exponent must have digits in JSON number"),
    NOT_ALLOWED_SINGLE_QUOTED_STRING("", "Single-quoted strings are not allowed in strict JSON"),
    NOT_ALLOWED_LEADING_PLUS("","Leading '+' not allowed in JSON number"),
    NOT_ALLOWED_LEADING_ZERO("","Leading zero not allowed in JSON number"),
    NOT_ALLOWED_TRAILING_DOT("","Trailing '.' not allowed in JSON number"),
    EXPECTED_MAP_KEY("JSON-110", "Expected key (string or identifier)"),
    UNEXPECTED_CHARACTER("JSON-111", "Unexpected character '{0}'"),
    UNTERMINATED_STRING("JSON-111", "Unterminated string literal"),

    // Parser
    DEPTH_LIMIT("JSON-201", "Depth limit exceeded"),
    UNEXPECTED_TOKEN("JSON-202", "Unexpected token: {0}"),
    DUPLICATE_KEY("JSON-203", "Duplicate key found: '{0}'"),

    // MAP_KEY_INDENTATION("JSON-302", "Inconsistent indentation for map key"),
    // EXPECTED_INDENTATION_BLOCK_SCALAR("JSON-303", "Inconsistent indentation for block scalar"),
    // EXPECTED_DEDENT_BLOCK_COMMENT("JSON-304", "Expected dedent after block content"),


    // Serializer
    FAILED_TO_MAP_TYPE("JSON-501", "Failed to map {0} to JSON AST: {1}"),
    FAILED_TO_MAP_AST("JSON-502", "Failed to map JSON AST to %s: %s"),
    CLASS_NOT_SERIALIZABLE("JSON-503", "Class {0} is not serializable"),
    EXPECTED_MAP_STRUCTURE("JSON-504", "Expected a map structure for class {0}"),
    NO_SUCH_METHOD("JSON-505", "No such method: {0}"),
    FAILED_DESERIALIZE("JSON-506", "Failed to deserialize: {0}: {1}"),
    // EXPECTED_YAML_NODE("JSON-507", "Expected YamlNode for serializable type: {0}."),
    INCOMPATIBLE_TYPES("JSON-508", "Incompatible types: Cannot map {0} to Java type {1}"),
    FAILED_DESERIALIZE_SCALAR("JSON-509", "Failed to deserialize scalar to {0}: {1}"),
    UNSUPPORTED_TYPE("JSON-510", "Unsupported field type: {0}"),
    EXPECTED_SEQUENCE("JSON-511", "Expected a sequence for field: {0}");

    private final String code;
    private final String message;

    JsonDiagnosticCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}