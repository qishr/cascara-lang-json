package io.github.qishr.cascara.lang.json.token;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class JsonNumberToken implements JsonToken {
    private final LexemeProvider provider;

    protected final int startLine;
    protected final int startColumn;
    protected final int startOffset;
    private final int endOffset;
    // protected final JsonTokenType type;

    private final double value;
    private final boolean isInteger;
    private final boolean isHex;

    private List<JsonComment> comments;

    private String cachedLexeme;
    private boolean isLexemeCached;

    // Identifiers & Numbers
    // public JsonNumberToken(
    //     LexemeProvider provider,
    //     int startLine, int startColumn,
    //     int startOffset, int endOffset,
    //     JsonTokenType type
    // ) {
    //     this.startLine = startLine;
    //     this.startColumn = startColumn;
    //     this.startOffset = startOffset;
    //     this.endOffset = endOffset;
    //     this.type = type;

    //     this.provider = provider;
    // }

    public JsonNumberToken(
        LexemeProvider provider,
        int startLine, int startColumn,
        int startOffset, int endOffset,
        ScannedNumber n
    ) {
        this.provider = provider;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.value = n.value;
        this.isInteger = n.isInteger;
        this.isHex = n.isHex;
        // this.provider = provider;
    }

    public Number getNumber() {
        if (isHex) return (long)value;
        if (isInteger) return (long)value;
        return value;
    }
    @Override
    public String getLexeme() {
        if (!isLexemeCached) {
            cachedLexeme = provider.slice(startOffset, endOffset);
            isLexemeCached = true;
        }
        return cachedLexeme;
    }

    @Override
    public String getContent() {
        return null;
    }

    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public JsonTokenType getType() {
        return JsonTokenType.NUMBER;
    }

    public JsonLiteral getLiteral() {
        return null;
    }

    @Override
    public QuoteStyle getQuoteStyle() {
        return QuoteStyle.PLAIN;
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
        return "NUMBER('" + value + "')@" + startLine + ":" + startColumn;
    }
}
