package io.github.qishr.cascara.lang.json.token;

import java.util.List;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class JsonErrorToken implements JsonToken {
    private final int line;
    private final int column;
    private final int offset;
    private final DiagnosticCode code;
    private final Object[] details;

    public JsonErrorToken(int line, int column, int offset, DiagnosticCode code, Object... details) {
        this.line = line;
        this.column = column;
        this.offset = offset;
        this.code = code;
        this.details = details;
    }

    public DiagnosticCode getCode() {
        return code;
    }

    public Object[] getDetails() {
        return details;
    }

    @Override
    public String getLexeme() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getLexeme'");
    }

    @Override
    public String getContent() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getContent'");
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public int getStartLine() {
        return line;
    }

    @Override
    public int getStartColumn() {
        return column;
    }

    @Override
    public JsonTokenType getType() {
        return JsonTokenType.ERROR;
    }

    @Override
    public QuoteStyle getQuoteStyle() {
        return QuoteStyle.PLAIN;
    }

    @Override
    public JsonLiteral getLiteral() {
        return null;
    }

    @Override
    public List<JsonComment> getComments() {
        return null;
    }

    @Override
    public void attachComments(List<JsonComment> list) {
        throw new UnsupportedOperationException("Unimplemented method 'attachComments'");
    }

}
