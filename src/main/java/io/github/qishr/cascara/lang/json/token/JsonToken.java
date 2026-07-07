package io.github.qishr.cascara.lang.json.token;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.token.Token;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class JsonToken implements Token {
    protected int startLine;
    protected int startColumn;
    protected int startOffset;
    protected JsonTokenType type;
    protected String lexeme;
    protected String content;
    protected QuoteStyle quoteStyle;
    private List<JsonComment> comments;


    // public JsonToken(int line, int column, int startOffset, JsonTokenType type) {
    //     this.startLine = line;
    //     this.startColumn = column;
    //     this.startOffset = startOffset;
    //     this.type = type;
    // }

    public JsonToken(int line, int column, int startOffset, JsonTokenType type, String lexeme, String content, QuoteStyle quoteStyle) {
        this.startLine = line;
        this.startColumn = column;
        this.startOffset = startOffset;
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
