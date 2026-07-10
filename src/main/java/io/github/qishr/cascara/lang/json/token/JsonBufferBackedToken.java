package io.github.qishr.cascara.lang.json.token;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public final class JsonBufferBackedToken implements JsonToken {
    private final LexemeProvider provider;

    protected final int startLine;
    protected final int startColumn;
    protected final int startOffset;
    protected final JsonTokenType type;

    private final QuoteStyle quoteStyle;
    private List<JsonComment> comments;

    private final int endOffset;
    private final int startContent;
    private final int endContent;

    private String cachedLexeme;
    private boolean isLexemeCached;
    private String cachedString;
    private boolean isStringCached;

    // Identifiers & Numbers
    public JsonBufferBackedToken(
        LexemeProvider provider,
        int startLine, int startColumn,
        int startOffset, int endOffset,
        JsonTokenType type
    ) {
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.startOffset = startOffset;
        this.type = type;
        this.quoteStyle = QuoteStyle.PLAIN;

        this.provider = provider;
        this.endOffset = endOffset;
        this.startContent = startOffset;
        this.endContent = endOffset;
    }

    // Strings
    public JsonBufferBackedToken(
        LexemeProvider provider,
        int startLine, int startColumn,
        int startOffset, int endOffset,
        QuoteStyle quoteStyle
    ) {
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.startOffset = startOffset;
        this.type = JsonTokenType.STRING;
        this.quoteStyle = quoteStyle;

        this.provider = provider;
        this.endOffset = endOffset;
        this.startContent = startOffset + 1;
        this.endContent = endOffset - 1;
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
        if (!isStringCached) {
            cachedString = provider.slice(startContent, endContent);
            isStringCached = true;
        }
        return cachedString;
    }

    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public JsonTokenType getType() {
        return type;
    }

    public JsonLiteral getLiteral() {
        return null;
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
        return type + "('" + getLexeme() + "')@" + startLine + ":" + startColumn;
    }
}
