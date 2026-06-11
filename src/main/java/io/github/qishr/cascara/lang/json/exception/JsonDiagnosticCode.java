package io.github.qishr.cascara.lang.json.exception;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;

public enum JsonDiagnosticCode implements DiagnosticCode {

    EXPECTED_CLOSE_BRACE("JSON-201", "Expected '}'"),
    EXPECTED_EOS("JSON-203", "Expected end of stream."),
    EXPECTED_SCALAR("JSON-204", "Expected scalar."),
    EXPECTED_COLON_FLOW_MAP("JSON-205", "Expected ':' after key in flow map."),
    EXPECTED_OPEN_BRACE("YAML-206", "Expected '{'."),
    EXPECTED_CLOSE_BRACKET("JSON-207", "Expected ']'."),
    EXPECTED_OPEN_BRACKET("JSON-208", "Expected '['."),
    EXPECTED_COLON_MAP_KEY("JSON-209", "Expected ':' after key."),
    EXPECTED_MAP_KEY("JSON-210", "Expected key (string or identifier)"),

    MAP_KEY_INDENTATION("JSON-302", "Inconsistent indentation for map key."),
    EXPECTED_INDENTATION_BLOCK_SCALAR("JSON-303", "Inconsistent indentation for block scalar."),
    EXPECTED_DEDENT_BLOCK_COMMENT("JSON-304", "Expected dedent after block content."),

    DUPLICATE_KEY("JSON-403", "Duplicate key found: '{0}'."),

    FAILED_TO_MAP_TYPE("JSON-501", "Failed to map {0} to JSON AST: {1}."),
    FAILED_TO_MAP_AST("JSON-502", "Failed to map JSON AST to %s: %s."),
    CLASS_NOT_SERIALIZABLE("JSON-503", "Class {0} is not serializable."),
    EXPECTED_MAP_STRUCTURE("JSON-504", "Expected a map structure for class {0}."),
    NO_SUCH_METHOD("JSON-505", "No such method: {0}."),
    FAILED_DESERIALIZE("JSON-506", "Failed to deserialize: {0}: {1}."),
    EXPECTED_YAML_NODE("JSON-507", "Expected YamlNode for serializable type: {0}."),
    INCOMPATIBLE_TYPES("JSON-508", "Incompatible types: Cannot map {0} to Java type {1}."),
    FAILED_DESERIALIZE_SCALAR("JSON-509", "Failed to deserialize scalar to {0}: {1}."),
    UNSUPPORTED_TYPE("JSON-510", "Unsupported field type: {0}."),
    EXPECTED_SEQUENCE("JSON-511", "Expected a sequence for field: {0}.");

    private final String code;
    private final String message;

    JsonDiagnosticCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}