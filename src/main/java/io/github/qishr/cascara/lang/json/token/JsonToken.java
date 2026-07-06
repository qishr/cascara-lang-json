package io.github.qishr.cascara.lang.json.token;

import io.github.qishr.cascara.common.lang.token.Token;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class JsonToken implements Token {
    private int startLine;
    private int startColumn;
    private int offset;
    private JsonTokenType type;
    private String lexeme;
    private String content;

    private QuoteStyle quoteStyle;

    public JsonToken(int line, int column, int startIndex, JsonTokenType type, String lexeme, String content) {
        this.startLine = line;
        this.startColumn = column;
        this.offset = startIndex;
        this.type = type;
        this.lexeme = lexeme;
        this.content = content;
    }

    public JsonToken(int line, int column, int startIndex, JsonTokenType type, String lexeme, String content, QuoteStyle quoteStyle) {
        this.startLine = line;
        this.startColumn = column;
        this.offset = startIndex;
        this.type = type;
        this.lexeme = lexeme;
        this.content = content;
        this.quoteStyle = quoteStyle;
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
    public String getContent() {
        return content;
    }

    public QuoteStyle getQuoteStyle() {
        return quoteStyle;
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
        String valuePart = (content != null) ? " (Value: " + content + ")" : "";

        return String.format("[%-20s | '%-15s'%s | L:%d C:%d]",
            type,
            displayLexeme,
            valuePart,
            startLine,
            startColumn);
    }


}
