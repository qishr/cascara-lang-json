package io.github.qishr.cascara.lang.json.exception;

import java.net.URI;

import io.github.qishr.cascara.common.lang.exception.ParserException;

public class JsonException extends ParserException {

    public JsonException(String message, Throwable cause) {
        super(message, cause, UNKNOWN_COORD, UNKNOWN_COORD, null);
    }

    public JsonException(String message, int line, int column, URI uri) {
        super(message, line, column, uri);
    }

    public JsonException(String message, Throwable cause, int line, int column, URI uri) {
        super(message, cause, line, column, uri);
    }
}
