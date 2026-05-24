package io.github.qishr.cascara.lang.json.token;

import io.github.qishr.cascara.common.lang.token.Token;

public class JsonToken implements Token {
    private JsonTokenType type;
    private String lexeme;
    private Object value;
    private int offset;
    private int startLine;
    private int startColumn;

    public JsonToken(JsonTokenType type, String lexeme, Object value, int startIndex, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.value = value;
        this.offset = startIndex;
        this.startLine = line;
        this.startColumn = column;
    }


    @Override
    public JsonTokenType getType() {
        return type;
    }

    @Override
    public String getLexeme() {
        return lexeme;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public int getStartLine() {
        return startLine;
    }

    @Override
    public int getStartColumn() {
        return startColumn;
    }

    @Override
    public String toString() {
        String displayLexeme = lexeme.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
        String valuePart = (value != null) ? " (Value: " + value + ")" : "";

        return String.format("[%-20s | '%-15s'%s | L:%d C:%d]",
            type,
            displayLexeme,
            valuePart,
            startLine,
            startColumn);
    }


}
