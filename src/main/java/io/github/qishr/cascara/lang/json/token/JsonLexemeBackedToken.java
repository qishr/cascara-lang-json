package io.github.qishr.cascara.lang.json.token;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class JsonLexemeBackedToken implements JsonToken {
    protected final int startLine;
    protected final int startColumn;
    protected final int startOffset;
    protected final JsonTokenType type;
    private final JsonLiteral literal;
    private final String lexeme;
    private final String content;
    private final QuoteStyle quoteStyle;
    private List<JsonComment> comments;

    // Structural
    public JsonLexemeBackedToken(int line, int column, int startOffset, JsonTokenType type) {
        this.startLine = line;
        this.startColumn = column;
        this.startOffset = startOffset;
        this.type = type;

        this.lexeme = null;
        this.content = null;
        this.literal = null;
        this.quoteStyle = QuoteStyle.PLAIN;
    }

    // Literals
    public JsonLexemeBackedToken(int line, int column, int startOffset, JsonTokenType type, JsonLiteral literal) {
        this.startLine = line;
        this.startColumn = column;
        this.startOffset = startOffset;
        this.type = type;
        this.literal = literal;

        this.lexeme = null;
        this.content = null;
        this.quoteStyle = QuoteStyle.PLAIN;
    }

    // Numbers & Identifiers
    public JsonLexemeBackedToken(int line, int column, int startOffset, JsonTokenType type, String lexeme) {
        this.startLine = line;
        this.startColumn = column;
        this.startOffset = startOffset;
        this.type = type;
        this.lexeme = lexeme;

        this.content = null;
        this.literal = null;
        this.quoteStyle = QuoteStyle.PLAIN;
    }

    // Strings
    public JsonLexemeBackedToken(int line, int column, int startOffset, JsonTokenType type, String lexeme, String content, QuoteStyle quoteStyle) {
        this.startLine = line;
        this.startColumn = column;
        this.startOffset = startOffset;
        this.type = type;
        this.lexeme = lexeme;
        this.content = content;
        this.quoteStyle = quoteStyle;

        this.literal = null;
    }

    @Override
    public JsonTokenType getType() {
        return type;
    }

    public JsonLiteral getLiteral() {
        return literal;
    }

    @Override
    public String getLexeme() {
        return lexeme;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public QuoteStyle getQuoteStyle() {
        return quoteStyle;
    }

    @Override
    public int getOffset() {
        return startOffset;
    }

    @Override
    public int getStartLine() {
        return startLine;
    }

    @Override
    public int getStartColumn() {
        return startColumn;
    }

    public List<JsonComment> getComments() {
        return comments;
    }

    public void attachComments(List<JsonComment> list) {
        if (list == null || list.isEmpty()) return;
        this.comments = new ArrayList<>(list);
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
