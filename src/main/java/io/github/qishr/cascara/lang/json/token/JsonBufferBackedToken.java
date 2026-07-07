package io.github.qishr.cascara.lang.json.token;

import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public final class JsonBufferBackedToken extends JsonToken {
    private final LexemeProvider provider;
    private final int endOffset;

    public JsonBufferBackedToken(
        LexemeProvider provider,
        int startOffset,
        int endOffset,
        int startLine,
        int startColumn,
        JsonTokenType type,
        String content,
        QuoteStyle quoteStyle
    ) {
        super(startLine, startColumn, startOffset, type, null, content, quoteStyle);
        this.provider = provider;
        this.endOffset = endOffset;
    }

    @Override
    public String getLexeme() {
        return provider.slice(startOffset, endOffset);
    }

    public QuoteStyle getQuoteStyle() {
        return quoteStyle;
    }

    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public String toString() {
        return type + "('" + getLexeme() + "')@" + startLine + ":" + startColumn;
    }
}
