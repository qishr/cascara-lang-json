package io.github.qishr.cascara.lang.json.token;

import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public final class JsonBufferBackedToken extends JsonToken {
    private final LexemeProvider provider;
    private final int endOffset;

    public JsonBufferBackedToken(LexemeProvider provider, int startLine, int startColumn, int startOffset, int endOffset, JsonTokenType type) {
        super(startLine, startColumn, startOffset, type);
        this.provider = provider;
        this.endOffset = endOffset;
    }

    public JsonBufferBackedToken(
        LexemeProvider provider,
        int startLine,
        int startColumn,
        int startOffset,
        int endOffset,
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

    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public String toString() {
        return type + "('" + getLexeme() + "')@" + startLine + ":" + startColumn;
    }
}
