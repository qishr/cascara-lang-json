package io.github.qishr.cascara.lang.json.token;

import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public final class JsonBufferBackedToken extends JsonToken {
    private final LexemeProvider provider;
    private final int start;
    private final int end;
    private final int startLine;
    private final int startColumn;
    private final JsonTokenType type;
    private final String content;
    private final QuoteStyle quoteStyle;

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
        this.provider = provider;
        this.start = startOffset;
        this.end = endOffset;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.type = type;
        this.content = content;
        this.quoteStyle = quoteStyle;
    }

    @Override
    public JsonTokenType getType() {
        return type;
    }

    @Override
    public String getLexeme() {
        return provider.slice(start, end);
    }

    @Override
    public String getContent() {
        return content;
        // String lex = getLexeme();

        // switch (type) {
        //     case STRING:
        //         return JsonStringUnescaper.unescape(
        //             stripQuotes(lex, quoteStyle),
        //             quoteStyle
        //         );

        //     case COMMENT:
        //         return stripCommentDelimiters(lex);

        //     default:
        //         return lex; // identifiers, numbers, booleans, null
        // }
    }

    public QuoteStyle getQuoteStyle() {
        return quoteStyle;
    }

    @Override
    public int getOffset() {
        return start;
    }

    public int getEndOffset() {
        return end;
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
        return type + "('" + getLexeme() + "')@" + startLine + ":" + startColumn;
    }

    //
    //
    //

    private String stripQuotes(String lex, QuoteStyle style) {
        if (lex.length() < 2) return lex;
        return lex.substring(1, lex.length() - 1);
    }

    private String stripCommentDelimiters(String lex) {
        if (lex.startsWith("//")) {
            return lex.substring(2);
        }
        if (lex.startsWith("/*") && lex.endsWith("*/")) {
            return lex.substring(2, lex.length() - 2);
        }
        return lex;
    }

}
