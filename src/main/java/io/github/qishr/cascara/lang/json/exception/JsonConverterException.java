package io.github.qishr.cascara.lang.json.exception;

import io.github.qishr.cascara.common.diagnostic.LocalizableRuntimeException;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;

public class JsonConverterException extends LocalizableRuntimeException {

    public JsonConverterException(Throwable cause, DiagnosticCode code, Object... details) {
        super(cause, code, details);
    }

    public JsonConverterException(DiagnosticCode code, Object... details) {
        this(null, code, details);
    }
}
