package io.github.qishr.cascara.lang.json.processor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.util.SourceBuffer;
import io.github.qishr.cascara.common.lang.util.SourceInputStreamBuffer;
import io.github.qishr.cascara.common.lang.util.SourceStringBuffer;
import io.github.qishr.cascara.lang.json.JsonOptions;
import io.github.qishr.cascara.lang.json.token.JsonBufferBackedToken;
import io.github.qishr.cascara.lang.json.token.JsonComment;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;

public class JsonTokenizer extends AbstractJsonProcessor<JsonTokenizer> implements Tokenizer<JsonToken>{

    // TODO: WHy are these not being used after the refactor?
    // Either they never did any good, or a regression has been made.
    private static final String LBRACE = "{";
    private static final String RBRACE = "}";
    private static final String LBRACKET = "[";
    private static final String RBRACKET = "]";
    private static final String COMMA = ",";
    private static final String COLON = ":";

    private static final boolean[] WS = new boolean[128];
    static {
        WS[' ']  = true;
        WS['\t'] = true;
        WS['\n'] = true;
        WS['\r'] = true;
    }

    // Lookup tables for identifier classification
    private static final boolean[] IDENT_START = new boolean[128];
    private static final boolean[] IDENT_PART  = new boolean[128];
    static {
        // Letters
        for (char c = 'a'; c <= 'z'; c++) IDENT_START[c] = IDENT_PART[c] = true;
        for (char c = 'A'; c <= 'Z'; c++) IDENT_START[c] = IDENT_PART[c] = true;

        // JSON5 identifier extras
        IDENT_START['_'] = IDENT_PART['_'] = true;
        IDENT_START['$'] = IDENT_PART['$'] = true;

        // Digits allowed only as IDENT_PART
        for (char c = '0'; c <= '9'; c++) IDENT_PART[c] = true;
    }

    // Lookup tables for digits and hex digits
    private static final boolean[] DIGIT = new boolean[128];
    private static final boolean[] HEX   = new boolean[128];
    static {
        for (char c = '0'; c <= '9'; c++) DIGIT[c] = true;
        for (char c = '0'; c <= '9'; c++) HEX[c] = true;
        for (char c = 'a'; c <= 'f'; c++) HEX[c] = true;
        for (char c = 'A'; c <= 'F'; c++) HEX[c] = true;
    }

    // Note: Keep this in alphabetical order,
    // or it will become time consuming to maintain
    private boolean ALLOW_COMMENTS;
    private boolean ALLOW_HEXADECIMAL_NUMBERS;
    private boolean ALLOW_INFINITY_AND_NAN;
    private boolean ALLOW_SINGLE_QUOTED_STRINGS; // TODO:L Why is this not used after the refactor?
    private boolean ALLOW_UNICODE;
    private boolean CAPTURE_COMMENTS;

    // private final Deque<JsonToken> pendingTokens = new ArrayDeque<>();

    private SourceBuffer buffer;
    private List<JsonToken> tokens;
    private boolean isLegacyMode = false;
    private boolean streamEnded = false; // TODO: If this is no longer needed, remove it.
    private final List<JsonComment> pendingComments = new ArrayList<>();

    /// Default constructor for SPI
    public JsonTokenizer() {
        // SPI will call this
        // Default is strict JSON
        applyOptions(new JsonOptions());
    }

    @Override
    public JsonTokenizer setOptions(LanguageOptions<?> options) {
        super.setOptions(options);
        if (!(options instanceof JsonOptions jsonOptions)) {
            throw new IllegalArgumentException("JsonTokenizer requires JsonOptions");
        }
        applyOptions(jsonOptions);
        return this;
    }

    // TODO: This makes zero performance difference if the methods
    // using these values have to set a local final variable anyway.
    private void applyOptions(JsonOptions options) {
        // Note: Keep this in alphabetical order,
        // or it will become time consuming to maintain
        this.ALLOW_COMMENTS              = options.allowComments();
        this.ALLOW_HEXADECIMAL_NUMBERS   = options.allowHexadecimalNumbers();
        this.ALLOW_INFINITY_AND_NAN      = options.allowInfinityAndNaN();
        this.ALLOW_SINGLE_QUOTED_STRINGS = options.allowSingleQuotedStrings();
        this.ALLOW_UNICODE               = options.allowUnicode();
        this.CAPTURE_COMMENTS            = options.captureComments();
    }

    @Override protected JsonTokenizer self() { return this; }

    public int getLine() {
        return buffer.line();
    }

    public int getColumn() {
        return buffer.column();
    }


    @Override
    public void open(String text) {
        this.buffer = new SourceStringBuffer(text);
        this.isLegacyMode = false;
        resetCommonState();
    }

    @Override
    public void open(InputStream is) {
        this.buffer = new SourceInputStreamBuffer(is);
        this.isLegacyMode = false;
        resetCommonState();
    }

    @Override
    public List<JsonToken> tokenize(String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        this.tokens = new ArrayList<>();
        this.streamEnded = false;

        open(source);
        this.isLegacyMode = true;

        JsonToken token;
        while ((token = nextToken()).getType() != JsonTokenType.EOF) {
            this.tokens.add(token);
        }

        tokens.add(new JsonToken(
            buffer.line(),
            buffer.column(),
            buffer.offset(),
            JsonTokenType.EOF,
            "",
            "",
            QuoteStyle.PLAIN
        ));

        return this.tokens;
    }

    @Override
    public Set<JsonTokenType> getTokenTypes() {
        return EnumSet.allOf(JsonTokenType.class);
    }

    public JsonToken nextToken() {

        // reporter.debug("nextToken");

        if (!pendingComments.isEmpty()) {
            // reporter.debug("calling toCommentToken");
            return toCommentToken(pendingComments.remove(0));
        }

        advanceWhitespaceAndComments();

        if (!pendingComments.isEmpty()) {
            // reporter.debug("calling toCommentToken (post)");
            return toCommentToken(pendingComments.remove(0));
        }

        if (buffer.isAtEnd()) {
            JsonToken tok = new JsonToken(
                buffer.windowStartLine(),
                buffer.windowStartColumn(),
                buffer.windowStartOffset(),
                JsonTokenType.EOF,
                null,
                null,
                QuoteStyle.PLAIN
            );

            if (!pendingComments.isEmpty()) {
                tok.attachComments(pendingComments);
                pendingComments.clear();
            }

            return tok;
        }

        char c = buffer.peek();
        JsonToken tok;

        // Unicode path
        if (c > 127) {
            tok = handleUnicodeChar(c);
            if (tok == null) {
                // whitespace or skipped unicode trivia
                return nextToken();
            }
        } else {
            // ASCII path
            switch (c) {
                case '{': tok = structural(JsonTokenType.LEFT_BRACE); break;
                case '}': tok = structural(JsonTokenType.RIGHT_BRACE); break;
                case '[': tok = structural(JsonTokenType.LEFT_BRACKET); break;
                case ']': tok = structural(JsonTokenType.RIGHT_BRACKET); break;
                case ':': tok = structural(JsonTokenType.COLON); break;
                case ',': tok = structural(JsonTokenType.COMMA); break;

                case '"':
                case '\'':
                    tok = scanStringToken(c);
                    break;

                default:
                    tok = scanNumberOrIdentifierOrError(c);
                    break;
            }
        }

        // Attach any captured comments
        if (!pendingComments.isEmpty()) {
            tok.attachComments(pendingComments);
            pendingComments.clear();
        }

        if (!(reporter instanceof NoOpReporter)) {
            reporter.trace("TOKEN: " + tok.getType() +
                           " [" + tok.getStartLine() + ":" + tok.getStartColumn() + "] " +
                           (tok.getLexeme() != null ? tok.getLexeme() : ""));
        }

        return tok;
    }

    private JsonToken toCommentToken(JsonComment c) {
        return new JsonToken(
            c.getLine(),
            c.getColumn(),
            /* offset */ 0,              // or a better offset if you track it
            JsonTokenType.COMMENT,
            c.getLexeme(),
            c.getValue(),
            QuoteStyle.PLAIN
        );
    }

    private JsonToken structural(JsonTokenType type) {
        int startOffset = buffer.offset();
        int startLine   = buffer.line();
        int startColumn = buffer.column();

        buffer.advance();

        if (buffer instanceof LexemeProvider lp) {
            return new JsonBufferBackedToken(
                lp,
                startOffset,
                buffer.offset(),
                startLine,
                startColumn,
                type,
                null,
                QuoteStyle.PLAIN
            );
        }

        return new JsonToken(
            startLine,
            startColumn,
            startOffset,
            type,
            null,
            null,
            QuoteStyle.PLAIN
        );
    }

    private JsonToken scanStringToken(char quoteChar) {
        int startOffset = buffer.offset(); // actual position of the quote
        int startLine   = buffer.line();
        int startColumn = buffer.column();

        buffer.startTokenWindow();
        buffer.advance(); // consume opening quote

        boolean ok = scanString(quoteChar);

        String lexeme = buffer.getTokenWindowLexeme();
        String content = lexeme.substring(1, lexeme.length() - 1);

        QuoteStyle qs = (quoteChar == '"')
            ? QuoteStyle.DOUBLE
            : QuoteStyle.SINGLE;

        if (qs == QuoteStyle.SINGLE && !options.allowSingleQuotedStrings()) {
            return errorToken("Single-quoted strings are not allowed in strict JSON", startLine, startColumn);
        }

        if (!ok) {
            return errorToken("Unterminated string literal", startLine, startColumn);
        }

        return makeStringToken(startOffset, startLine, startColumn, lexeme, content, qs);
    }

    private JsonToken errorToken(String message, int line, int column) {
        return new JsonToken(
            line,
            column,
            buffer.windowStartOffset(),
            JsonTokenType.ERROR,
            message,
            null,
            QuoteStyle.PLAIN
        );
    }

    private JsonToken makeStringToken(
        int startOffset, int line, int column,
        String lexeme, String content, QuoteStyle qs
    ) {

        // reporter.debug("New string token with content: " + content);

        if (buffer instanceof LexemeProvider lp) {
            return new JsonBufferBackedToken(
                lp,
                startOffset,
                buffer.offset(),
                line,
                column,
                JsonTokenType.STRING,
                content,
                qs
            );
        }

        return new JsonToken(
            line,
            column,
            startOffset,
            JsonTokenType.STRING,
            lexeme,
            content,
            qs
        );
    }

    private JsonToken scanNumberOrIdentifierOrError(char startChar) {
        int startOffset = buffer.offset();
        int startLine   = buffer.line();
        int startColumn = buffer.column();

        buffer.startTokenWindow();

        // NUMBER?
        if (isDigit(startChar) || startChar == '-' || startChar == '+' || startChar == '.') {
            scanNumber(startChar);
            String lexeme  = buffer.getTokenWindowLexeme();
            return makeNumberToken(startOffset, startLine, startColumn, lexeme, lexeme);
        }

        // IDENTIFIER?
        if (startChar < 128 && IDENT_START[startChar]) {
            scanIdentifierFast();
            String lexeme  = buffer.getTokenWindowLexeme();
            return classifyLexeme(startOffset, startLine, startColumn, lexeme, lexeme);
        }

        // ERROR
        buffer.advance();
        return errorToken(
            "Unexpected character '" + startChar + "'",
            startLine,
            startColumn
        );
    }

    private JsonToken classifyLexeme(
        int startOffset, int line, int column,
        String lexeme, String content
    ) {
        switch (lexeme) {
            case "true":
            case "false":
                return new JsonToken(
                    line, column, startOffset,
                    JsonTokenType.BOOLEAN,
                    lexeme,
                    lexeme,
                    QuoteStyle.PLAIN
                );

            case "null":
                return new JsonToken(
                    line, column, startOffset,
                    JsonTokenType.NULL,
                    lexeme,
                    lexeme,
                    QuoteStyle.PLAIN
                );

            case "Infinity":
            case "NaN":
                return makeNumberToken(startOffset, line, column, lexeme, lexeme);

            default:
                return makeIdentifierToken(startOffset, line, column, lexeme, content);
        }
    }



    private JsonToken makeNumberToken(
        int startOffset, int line, int column,
        String lexeme, String content
    ) {
        if (buffer instanceof LexemeProvider lp) {
            return new JsonBufferBackedToken(
                lp,
                startOffset,
                buffer.offset(),
                line,
                column,
                JsonTokenType.NUMBER,
                content,
                QuoteStyle.PLAIN
            );
        }

        return new JsonToken(
            line,
            column,
            startOffset,
            JsonTokenType.NUMBER,
            lexeme,
            content,
            QuoteStyle.PLAIN
        );
    }

    private JsonToken makeIdentifierToken(
        int startOffset, int line, int column,
        String lexeme, String content
    ) {
        if (buffer instanceof LexemeProvider lp) {
            return new JsonBufferBackedToken(
                lp,
                startOffset,
                buffer.offset(),
                line,
                column,
                JsonTokenType.IDENTIFIER,
                content,
                QuoteStyle.PLAIN
            );
        }

        return new JsonToken(
            line,
            column,
            startOffset,
            JsonTokenType.IDENTIFIER,
            lexeme,
            content,
            QuoteStyle.PLAIN
        );
    }

    private boolean scanString(char quoteChar) {
        boolean invalidUnicode = false;

        while (!buffer.isAtEnd()) {
            char next = buffer.advance();

            // Normal termination
            if (next == quoteChar) {
                // Return true = “string terminated normally”
                return !invalidUnicode;
            }

            // Raw unicode check
            if (!ALLOW_UNICODE && next > 127) {
                invalidUnicode = true;
            }

            // Escape sequence
            if (next == '\\' && !buffer.isAtEnd()) {
                char escaped = buffer.advance();
                if (escaped == 'u' || escaped == 'x') {
                    int count = (escaped == 'u') ? 4 : 2;
                    for (int i = 0; i < count && !buffer.isAtEnd(); i++) {
                        buffer.advance();
                    }
                }
            }
        }

        // EOF before closing quote → invalid string
        return false;
    }

    private void scanNumber(char startChar) {
        // Always consume the starting character first
        buffer.advance();

        // 1. JSON5 signed Infinity/NaN
        if (ALLOW_INFINITY_AND_NAN && (startChar == '-' || startChar == '+')) {
            char c = buffer.peek();
            if (c < 128 && IDENT_START[c]) {
                do {
                    buffer.advance();
                    c = buffer.peek();
                } while (c < 128 && IDENT_PART[c]);
                return;
            }
        }

        // 2. Hexadecimal 0x...
        if (ALLOW_HEXADECIMAL_NUMBERS && startChar == '0') {
            char c = buffer.peek();
            if (c == 'x' || c == 'X') {
                buffer.advance(); // consume x/X
                do {
                    c = buffer.peek();
                    if (c < 128 && HEX[c]) {
                        buffer.advance();
                        continue;
                    }
                    break;
                } while (true);
                return;
            }
        }

        // 3. Integer part
        char c;
        while (true) {
            c = buffer.peek();
            if (c < 128 && DIGIT[c]) {
                buffer.advance();
                continue;
            }
            break;
        }

        // 4. Fractional part
        if (buffer.peek() == '.') {
            buffer.advance();
            while (true) {
                c = buffer.peek();
                if (c < 128 && DIGIT[c]) {
                    buffer.advance();
                    continue;
                }
                break;
            }
        }

        // 5. Exponent part
        c = buffer.peek();
        if (c == 'e' || c == 'E') {
            buffer.advance();
            c = buffer.peek();
            if (c == '+' || c == '-') {
                buffer.advance();
            }
            while (true) {
                c = buffer.peek();
                if (c < 128 && DIGIT[c]) {
                    buffer.advance();
                    continue;
                }
                break;
            }
        }
    }

    private void scanIdentifierFast() {
        // We already consumed the first character in scanToken()
        while (true) {
            char c = buffer.peek();
            if (c < 128 && IDENT_PART[c]) {
                buffer.advance();
                continue;
            }
            break;
        }
    }

    private String scanSingleLineComment() {
        buffer.advance(); // '/'
        buffer.advance(); // '/'
        // System.out.println("SLC consumed until offset: " + buffer.offset());

        while (true) {
            char c = buffer.peek();
            if (c == '\n' || c == '\r' || c == '\0') {
                break;
            }
            buffer.advance();
        }

        if (buffer.peek() == '\n' || buffer.peek() == '\r') {
            buffer.advance();
        }

        // Full lexeme, including slashes
        // String lexeme = buffer.getTokenWindowLexeme();
        // Value: strip the leading "//"
        // return lexeme.length() >= 2 ? lexeme.substring(2) : "";

        String lexeme = buffer.getTokenWindowLexeme();
        String value = lexeme.length() >= 2 ? lexeme.substring(2) : "";

        // remove trailing newline from value
        if (value.endsWith("\n")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("\r")) value = value.substring(0, value.length() - 1);

        return value;

    }

    private String scanMultiLineComment() {
        // Consume the initial "/*"
        buffer.advance(); // '/'
        buffer.advance(); // '*'
        // System.out.println("MLC consumed until offset: " + buffer.offset());

        int start = buffer.offset();

        while (!buffer.isAtEnd()) {
            char c = buffer.peek();
            char n = buffer.peekNext();

            // End of comment: "*/"
            if (c == '*' && n == '/') {
                buffer.advance(); // '*'
                buffer.advance(); // '/'
                break;
            }

            buffer.advance();
        }

        // Extract inner text: everything between "/*" and "*/"
        String lexeme = buffer.getTokenWindowLexeme();
        if (lexeme.length() >= 4) {
            // strip leading "/*" and trailing "*/"
            return lexeme.substring(2, lexeme.length() - 2);
        }

        return "";
    }

    private void advanceWhitespaceAndComments() {
        final boolean allowComments = ALLOW_COMMENTS;
        final boolean capture = CAPTURE_COMMENTS;
        final boolean allowUnicode = ALLOW_UNICODE;

        // if (CAPTURE_COMMENTS) {
        //     buffer.startTokenWindow();
        // }

        while (true) {
            char c = buffer.peek();
            if (c == '\0') return;

            // Fast ASCII whitespace
            if (c < 128 && WS[c]) {
                buffer.advance();
                continue;
            }

            // Unicode whitespace (only if allowed)
            if (c > 127) {
                if (!allowUnicode) return;
                if (Character.isWhitespace(c)) {
                    buffer.advance();
                    continue;
                }
            }

            // Comments
            if (allowComments && c == '/') {
                char n = buffer.peekNext();

                // Single-line comment
                if (n == '/') {
                    int line = buffer.windowStartLine();
                    int col  = buffer.windowStartColumn();

                    if (capture) buffer.startTokenWindow();
                    String value = scanSingleLineComment();
                    if (capture) {
                        String lexeme = buffer.getTokenWindowLexeme();

                        // TODO: I expect this to go wrong.
                        // And it's using 2 substring calls in the hot path!!!
                        // Well done, Copilot!
                        if (lexeme.endsWith("\n")) lexeme = lexeme.substring(0, lexeme.length() - 1);
                        if (lexeme.endsWith("\r")) lexeme = lexeme.substring(0, lexeme.length() - 1);

                        pendingComments.add(new JsonComment(lexeme, value, line, col));
                    }
                    continue;
                }

                // Multi-line comment
                if (n == '*') {
                    int line = buffer.windowStartLine();
                    int col  = buffer.windowStartColumn();

                    if (capture) buffer.startTokenWindow();
                    String value = scanMultiLineComment();
                    if (capture) {
                        String lexeme = buffer.getTokenWindowLexeme();

                        // See above comment ^^
                        if (lexeme.endsWith("\n")) lexeme = lexeme.substring(0, lexeme.length() - 1);
                        if (lexeme.endsWith("\r")) lexeme = lexeme.substring(0, lexeme.length() - 1);

                        pendingComments.add(new JsonComment(lexeme, value, line, col));
                    }
                    continue;
                }
            }

            // Non-trivia
            return;
        }
    }






    //
    // Unicode
    //

    private JsonToken handleUnicodeChar(char c) {
        int startOffset = buffer.windowStartOffset();
        int line        = buffer.windowStartLine();
        int column      = buffer.windowStartColumn();

        // Unicode whitespace (JSON5)
        if (Character.isWhitespace(c)) {
            // skip it
            buffer.advance();
            return null; // caller will continue skipping trivia
        }

        // Unicode identifier start (JSON5)
        if (Character.isUnicodeIdentifierStart(c)) {
            scanUnicodeIdentifier();
            String lexeme = buffer.getTokenWindowLexeme();
            return makeIdentifierToken(startOffset, line, column, lexeme, lexeme);
        }

        // Unicode identifier part (rare case)
        if (Character.isUnicodeIdentifierPart(c)) {
            scanUnicodeIdentifier();
            String lexeme = buffer.getTokenWindowLexeme();
            return makeIdentifierToken(startOffset, line, column, lexeme, lexeme);
        }

        // Unicode digit (JSON5)
        if (Character.isDigit(c)) {
            scanUnicodeNumber(c);
            String lexeme = buffer.getTokenWindowLexeme();
            return makeNumberToken(startOffset, line, column, lexeme, lexeme);
        }

        // Otherwise → UNKNOWN
        String lexeme = Character.toString(c);
        buffer.advance();
        return errorToken("Unexpected character '" + c + "'", line, column);
    }

    private void scanUnicodeIdentifier() {
        while (!buffer.isAtEnd()) {
            char c = buffer.peek();
            if (c <= 127) {
                if (c < 128 && IDENT_PART[c]) {
                    buffer.advance();
                    continue;
                }
                break;
            }
            if (Character.isUnicodeIdentifierPart(c)) {
                buffer.advance();
                continue;
            }
            break;
        }
    }

    private void scanUnicodeNumber(char startChar) {
        // integer part
        while (!buffer.isAtEnd()) {
            char c = buffer.peek();
            if (Character.isDigit(c)) {
                buffer.advance();
                continue;
            }
            break;
        }

        // fractional part
        if (buffer.peek() == '.') {
            buffer.advance();
            while (!buffer.isAtEnd() && Character.isDigit(buffer.peek())) {
                buffer.advance();
            }
        }

        // exponent part
        char c = buffer.peek();
        if (c == 'e' || c == 'E') {
            buffer.advance();
            c = buffer.peek();
            if (c == '+' || c == '-') buffer.advance();
            while (!buffer.isAtEnd() && Character.isDigit(buffer.peek())) {
                buffer.advance();
            }
        }
    }

    //
    //
    //

    // Small interceptor ensuring that if someone runs the old tokenize() API,
    // tokens get copied to the collection output array correctly.
    private JsonToken queueToken(JsonToken token) {
        if (isLegacyMode && token != null) {
            tokens.add(token);
        }
        return token;
    }

    private void resetCommonState() {
        // Handle UTF-8 BOM if present at start of stream/string
        if (buffer.peek() == '\uFEFF') {
            buffer.advance();
        }
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    //
    // Diagnostics
    //

    private void trace(String method) {
        if (reporter instanceof NoOpReporter) return;
        char currentC = buffer.peek();
        reporter.trace("C=%03d '%s' %03d:%03d %s",
            buffer.offset(), currentChar(currentC), buffer.line(), buffer.column(), method);
    }

    private String currentChar(char c) {
        return switch (c) {
            case '\t' -> "⇥";
            case '\r' -> "↵";
            case '\n' -> "↩";
            default -> Character.toString(c);
        };
    }

    // //
    // //
    // //

    // private static final class JsonComment {
    //     final String lexeme;
    //     final String value;
    //     final int line;
    //     final int column;

    //     JsonComment(String lexeme, String value, int line, int column) {
    //         this.lexeme = lexeme;
    //         this.value = value;
    //         this.line = line;
    //         this.column = column;
    //     }
    // }
}