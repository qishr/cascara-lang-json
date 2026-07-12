package io.github.qishr.cascara.lang.json.processor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.util.SimdCapableBuffer;
import io.github.qishr.cascara.common.lang.util.SourceBuffer;
import io.github.qishr.cascara.common.lang.util.SourceInputStreamBuffer;
import io.github.qishr.cascara.common.lang.util.SourceStringBuffer;
import io.github.qishr.cascara.lang.json.token.JsonBufferBackedToken;
import io.github.qishr.cascara.lang.json.token.JsonComment;
import io.github.qishr.cascara.lang.json.token.JsonLiteral;
import io.github.qishr.cascara.lang.json.token.JsonStructuralToken;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonLexemeBackedToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;
import io.github.qishr.cascara.lang.json.token.SourceByteBuffer;
import io.github.qishr.cascara.lang.json.util.JsonOptions;

public class JsonTokenizer extends AbstractJsonProcessor<JsonTokenizer> implements Tokenizer<JsonToken>{

    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String NULL = "null";
    private static final String INFINITY = "Infinity";
    private static final String NAN = "NaN";

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

    private static final byte[] STRUCTURAL = new byte[128];

    // private static final byte S_OTHER      = 0;
    private static final byte S_LBRACE     = 1;
    private static final byte S_RBRACE     = 2;
    private static final byte S_LBRACKET   = 3;
    private static final byte S_RBRACKET   = 4;
    private static final byte S_COLON      = 5;
    private static final byte S_COMMA      = 6;
    private static final byte S_STRING     = 7;

    static {
        STRUCTURAL['{'] = S_LBRACE;
        STRUCTURAL['}'] = S_RBRACE;
        STRUCTURAL['['] = S_LBRACKET;
        STRUCTURAL[']'] = S_RBRACKET;
        STRUCTURAL[':'] = S_COLON;
        STRUCTURAL[','] = S_COMMA;
        STRUCTURAL['"'] = S_STRING;
        STRUCTURAL['\''] = S_STRING; // JSON5
    }

    private boolean ALLOW_COMMENTS;
    private boolean ALLOW_HEXADECIMAL_NUMBERS;
    private boolean ALLOW_INFINITY_AND_NAN;
    private boolean ALLOW_UNICODE;
    private boolean CAPTURE_COMMENTS;
    private boolean USE_SIMD;

    // private final Deque<JsonToken> pendingTokens = new ArrayDeque<>();

    private SourceBuffer buffer;
    private List<JsonToken> tokens;
    private final List<JsonComment> pendingComments = new ArrayList<>();

    private TokenFactory factory;

    /// Default constructor for SPI
    public JsonTokenizer() {
        // SPI will call this
        // Default is strict JSON
        applyOptions(new JsonOptions());
    }


    @FunctionalInterface
    private interface TokenHandler {
        JsonToken handle(char c);
    }

    @FunctionalInterface
    private interface StringScanner {
        boolean scan(char quoteChar);
    }

    @FunctionalInterface
    private interface NumberScanner {
        JsonToken scan(char first);
    }

    @FunctionalInterface
    private interface IdentifierScanner {
        JsonToken scan(char first);
    }

    private Runnable triviaHandler;
    private TokenHandler tokenHandler;
    private StringScanner stringScanner;
    private NumberScanner numberScanner; // TODO: This isn't used yet
    private IdentifierScanner identifierScanner; // TODO: This isn't used yet


    private final StringScanner scalarStringScanner = this::scanString; // TODO: This isn't used yet

    // I moved this into a normal method, quite a bit below here.

    // private final StringScanner simdStringScanner = (quoteChar) -> {
    //     SourceByteBuffer bb = (SourceByteBuffer) buffer;
    //     byte quoteByte = (byte) quoteChar;
    //     int pos = bb.offset();
    //     int simdPos = bb.scanStringAsciiSimd(pos, quoteByte);
    //     bb.advanceBy(simdPos - pos);
    //     return scanString(quoteChar); // scalar fallback for escapes/Unicode
    // };


    @Override
    public JsonTokenizer setOptions(LanguageOptions<?> options) {
        super.setOptions(options);
        if (!(options instanceof JsonOptions jsonOptions)) {
            throw new IllegalArgumentException("JsonTokenizer requires JsonOptions");
        }
        applyOptions(jsonOptions);
        return this;
    }

    private void applyOptions(JsonOptions options) {
        this.ALLOW_COMMENTS              = options.allowComments();
        this.ALLOW_HEXADECIMAL_NUMBERS   = options.allowHexadecimalNumbers();
        this.ALLOW_INFINITY_AND_NAN      = options.allowInfinityAndNaN();
        this.ALLOW_UNICODE               = options.allowUnicode();
        this.CAPTURE_COMMENTS            = options.captureComments();
        this.USE_SIMD                    = options.useSimd();
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
        buffer = setupStringBuffer(text);
        factory = setupTokenFactory(buffer);
        skipBom();
    }

    @Override
    public void open(InputStream stream) {
        buffer = setupStreamBuffer(stream);
        factory = setupTokenFactory(buffer);
        skipBom();
    }

    @Override
    public List<JsonToken> tokenize(String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        this.tokens = new ArrayList<>();
        open(source);
        JsonToken token;
        while ((token = nextToken()).getType() != JsonTokenType.EOF) {
            this.tokens.add(token);
        }
        tokens.add(new JsonLexemeBackedToken(
            buffer.line(),
            buffer.column(),
            buffer.offset(),
            JsonTokenType.EOF
        ));
        return this.tokens;
    }

    @Override
    public Set<JsonTokenType> getTokenTypes() {
        return EnumSet.allOf(JsonTokenType.class);
    }

    // Old code...

    // public JsonToken nextToken() {
    //     if (!pendingComments.isEmpty()) {
    //         return toCommentToken(pendingComments.remove(0));
    //     }

    //     skipTrivia.run();

    //     if (!pendingComments.isEmpty()) {
    //         return toCommentToken(pendingComments.remove(0));
    //     }

    //     if (buffer.isAtEnd()) {
    //         JsonToken tok = makeEofToken();

    //         if (!pendingComments.isEmpty()) {
    //             tok.attachComments(pendingComments);
    //             pendingComments.clear();
    //         }

    //         return tok;
    //     }

    //     char c = buffer.peek();
    //     JsonToken tok;

    //     // Unicode path
    //     if (c > 127) {
    //         tok = handleUnicodeChar(c);
    //         if (tok == null) {
    //             // whitespace or skipped unicode trivia
    //             return nextToken();
    //         }
    //     } else {
    //         // ASCII path
    //         byte kind = STRUCTURAL[c];

    //         switch (kind) {
    //             case S_LBRACE:
    //                 tok = makeStructuralToken(JsonTokenType.LEFT_BRACE);
    //                 break;

    //             case S_RBRACE:
    //                 tok = makeStructuralToken(JsonTokenType.RIGHT_BRACE);
    //                 break;

    //             case S_LBRACKET:
    //                 tok = makeStructuralToken(JsonTokenType.LEFT_BRACKET);
    //                 break;

    //             case S_RBRACKET:
    //                 tok = makeStructuralToken(JsonTokenType.RIGHT_BRACKET);
    //                 break;

    //             case S_COLON:
    //                 tok = makeStructuralToken(JsonTokenType.COLON);
    //                 break;

    //             case S_COMMA:
    //                 tok = makeStructuralToken(JsonTokenType.COMMA);
    //                 break;

    //             case S_STRING:
    //                 tok = scanStringToken(c);
    //                 break;

    //             default:
    //                 tok = scanNumberOrIdentifierOrError(c);
    //                 break;
    //         }
    //     }

    //     // Attach any captured comments
    //     if (!pendingComments.isEmpty()) {
    //         tok.attachComments(pendingComments);
    //         pendingComments.clear();
    //     }

    //     if (!reporter.isSilent()) {
    //         reporter.trace("TOKEN: " + tok.getType() +
    //                        " [" + tok.getStartLine() + ":" + tok.getStartColumn() + "] " +
    //                        (tok.getLexeme() != null ? tok.getLexeme() : ""));
    //     }

    //     return tok;
    // }


    public JsonToken nextToken() {

        if (!pendingComments.isEmpty()) {
            return toCommentToken(pendingComments.remove(0));
        }

        triviaHandler.run();

        if (!pendingComments.isEmpty()) {
            return toCommentToken(pendingComments.remove(0));
        }

        if (buffer.isAtEnd()) {
            JsonToken tok = makeEofToken();
            if (!pendingComments.isEmpty()) {
                tok.attachComments(pendingComments);
                pendingComments.clear();
            }
            return tok;
        }

        char c = buffer.peek();
        JsonToken tok = tokenHandler.handle(c);

        if (!pendingComments.isEmpty()) {
            tok.attachComments(pendingComments);
            pendingComments.clear();
        }

        if (!reporter.isSilent()) {
            reporter.trace("TOKEN: " + tok.getType() +
                           " [" + tok.getStartLine() + ":" + tok.getStartColumn() + "] " +
                           (tok.getLexeme() != null ? tok.getLexeme() : ""));
        }

        return tok;
    }


    private JsonToken scanStringToken(char quoteChar) {
        int startOffset = buffer.offset(); // actual position of the quote
        int startLine   = buffer.line();
        int startColumn = buffer.column();

        buffer.startTokenWindow();
        buffer.advance(); // consume opening quote

        // boolean ok = scanString(quoteChar);
        boolean ok = stringScanner.scan(quoteChar);

        QuoteStyle qs = (quoteChar == '"')
            ? QuoteStyle.DOUBLE
            : QuoteStyle.SINGLE;

        if (qs == QuoteStyle.SINGLE && !options.allowSingleQuotedStrings()) {
            return makeErrorToken("Single-quoted strings are not allowed in strict JSON", startLine, startColumn);
        }

        if (!ok) {
            return makeErrorToken("Unterminated string literal", startLine, startColumn);
        }

        return factory.makeStringToken(startOffset, startLine, startColumn, qs);
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

        // EOF before closing quote - invalid string
        return false;
    }



    private JsonToken scanNumberOrIdentifierOrError(char startChar) {
        int startOffset = buffer.offset();
        int startLine   = buffer.line();
        int startColumn = buffer.column();

        buffer.startTokenWindow();

        // NUMBER?
        if (DIGIT[startChar] || startChar == '-' || startChar == '+' || startChar == '.') {
            scanNumber(startChar);
            String lexeme  = buffer.getTokenWindowLexeme();
            return factory.makeNumberToken(startOffset, startLine, startColumn, lexeme);
        }

        // IDENTIFIER?
        if (startChar < 128 && IDENT_START[startChar]) {
            scanIdentifierFast();
            String lexeme  = buffer.getTokenWindowLexeme();
            return classifyLexeme(startOffset, startLine, startColumn, lexeme);
        }

        // ERROR
        buffer.advance();
        return makeErrorToken(
            "Unexpected character '" + startChar + "'",
            startLine,
            startColumn
        );
    }

    // TODO: scanNumber needs to be split into:
    // - a SIMD version
    // - a non-SIMD version
    // And the SIMD version will require the scanDigitsSimd method in SimdCapableBuffer
    // to be implemented by both SourceStringBuffer and SourceByteBuffer. At the moment
    // they both have different signatures for that method.
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

        char c;
        int pos;

        if (USE_SIMD) {
            pos = buffer.scanDigitsSimd(buffer.offset());
            buffer.setOffset(pos);
        } else {
            while (true) {
                c = buffer.peek();
                if (c < 128 && DIGIT[c]) {
                    buffer.advance();
                    continue;
                }
                break;
            }
        }

        // 4. Fractional part
        if (buffer.peek() == '.') {
            buffer.advance();
            if (USE_SIMD) {
                pos = buffer.scanDigitsSimd(buffer.offset());
                buffer.setOffset(pos);
            } else {
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

        // 5. Exponent part
        c = buffer.peek();
        if (c == 'e' || c == 'E') {
            buffer.advance();
            c = buffer.peek();
            if (c == '+' || c == '-') {
                buffer.advance();
            }
            if (USE_SIMD) {
                pos = buffer.scanDigitsSimd(buffer.offset());
                buffer.setOffset(pos);
            } else {
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
        String lexeme = buffer.getTokenWindowLexeme();

        // Value: strip the leading "//"
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
                    if (capture) buffer.startTokenWindow();
                    int line = buffer.line();
                    int col  = buffer.column();
                    String value = scanSingleLineComment();
                    if (capture) {
                        String lexeme = buffer.getTokenWindowLexeme();

                        // TODO: This is using 2 substring calls in the hot path!!!
                        if (lexeme.endsWith("\n")) lexeme = lexeme.substring(0, lexeme.length() - 1);
                        if (lexeme.endsWith("\r")) lexeme = lexeme.substring(0, lexeme.length() - 1);

                        pendingComments.add(new JsonComment(lexeme, value, line, col));
                    }
                    continue;
                }

                // Multi-line comment
                if (n == '*') {
                    if (capture) buffer.startTokenWindow();
                    int line = buffer.line();
                    int col  = buffer.column();
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
            return factory.makeIdentifierToken(startOffset, line, column, lexeme);
        }

        // Unicode identifier part (rare case)
        if (Character.isUnicodeIdentifierPart(c)) {
            scanUnicodeIdentifier();
            String lexeme = buffer.getTokenWindowLexeme();
            return factory.makeIdentifierToken(startOffset, line, column, lexeme);
        }

        // Unicode digit (JSON5)
        if (Character.isDigit(c)) {
            scanUnicodeNumber(c);
            String lexeme = buffer.getTokenWindowLexeme();
            return factory.makeNumberToken(startOffset, line, column, lexeme);
        }

        // Otherwise - UNKNOWN
        buffer.advance();
        return makeErrorToken("Unexpected character '" + c + "'", line, column);
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

    private void skipBom() {
        if (buffer.peek() == '\uFEFF') {
            buffer.advance();
        }
    }

    /// Token factory selection
    private TokenFactory setupTokenFactory(SourceBuffer buffer) {
        TokenFactory factory;
        if (buffer instanceof LexemeProvider lp) {
            factory = new BufferBackedTokenFactory(buffer, lp);
        } else {
            factory = new LexemeBackedTokenFactory(buffer);
        }
        return factory;
    }

    private SourceBuffer setupStringBuffer(String text) {
        SimdCapableBuffer buffer;

        // SIMD & no JSON5 unquoted keys - use byte-based buffer
        if (options.useSimd() && !options.allowUnquotedKeys()) {
            byte[] raw = text.getBytes(StandardCharsets.UTF_8);
            SourceByteBuffer byteBuffer = new SourceByteBuffer(raw);

            if (!ALLOW_UNICODE) {
                // TODO: This doesn't work:
                // - stringScanner takes a char and returns a boolean
                // - scanStringAsciiSimd takes an int and a byte
                stringScanner = byteBuffer::scanStringAsciiSimd;

                // TODO: This doesn't work:
                // - numberScanner takes a char and returns a JsonToken
                // - scanDigitsSimd takes an int and returns an int
                numberScanner = byteBuffer::scanDigitsSimd;

                // TODO: This doesn't work:
                // - identifierScanner takes a char and returns a JsonToken
                // - scanIdentifierFast takes no parameters and returns void
                identifierScanner = this::scanIdentifierFast;
            }


            // Whitespace / trivia skipping
            if (ALLOW_COMMENTS) {
                triviaHandler = byteBuffer::skipWhitespaceAndFormattingSimd;
            } else {
                triviaHandler = byteBuffer::skipWhitespaceSimd;
            }

            buffer = byteBuffer;
        } else {
            // JSON5 identifiers or SIMD disabled - use char-based buffer
            SourceStringBuffer stringBuffer = new SourceStringBuffer(text);

            if (!ALLOW_UNICODE) {
                stringScanner = this::simdStringScanner;

                // TODO: This doesn't work:
                // - numberScanner takes a char and returns a JsonToken
                // - scanNumber returns void
                numberScanner = this::scanNumber;

                // TODO: This doesn't work:
                // - identifierScanner takes a char and returns a JsonToken
                // - scanIdentifierFast takes no parameters and returns void
                identifierScanner = this::scanIdentifierFast;
            }

            // Whitespace / trivia skipping
            if (!ALLOW_COMMENTS && options.useSimd()) {
                triviaHandler = stringBuffer::skipWhitespaceSimd;
            } else {
                triviaHandler = this::advanceWhitespaceAndComments;
            }

            buffer = stringBuffer;
        }

        // String and number scanning
        if (ALLOW_UNICODE) {
            // TODO: scanStringUnicode does not exist
            stringScanner = this::scanStringUnicode;

            // TODO: This doesn't work:
            // - numberScanner takes a char and returns a JsonToken
            // - scanUnicodeNumber takes a char and returns void
            numberScanner = this::scanUnicodeNumber;

            // TODO: This doesn't work:
            // - identifierScanner takes a char and returns a JsonToken
            // - scanUnicodeIdentifier takes no parameters and returns void
            identifierScanner = this::scanUnicodeIdentifier;
        }

        // TODO: Does this need to change for unicode and/or SIMD?
        tokenHandler = this::handleToken;

        return buffer;
    }

    private SourceBuffer setupStreamBuffer(InputStream stream) {
        SourceInputStreamBuffer buffer = new SourceInputStreamBuffer(stream);

        return buffer;
    }

    private JsonToken handleToken(char c) {

        if (c > 127) {
            JsonToken tok = handleUnicodeChar(c);
            if (tok == null) return nextToken();
            return tok;
        }

        switch (c) {
            case '{': return makeStructuralToken(JsonTokenType.LEFT_BRACE);
            case '}': return makeStructuralToken(JsonTokenType.RIGHT_BRACE);
            case '[': return makeStructuralToken(JsonTokenType.LEFT_BRACKET);
            case ']': return makeStructuralToken(JsonTokenType.RIGHT_BRACKET);
            case ':': return makeStructuralToken(JsonTokenType.COLON);
            case ',': return makeStructuralToken(JsonTokenType.COMMA);

            case '"':
            case '\'':
                return scanStringToken(c);

            default:
                return scanNumberOrIdentifierOrError(c);
        }
    }

    // private JsonToken handleAsciiToken(char c) {

    //     if (c > 127) {
    //         throw error("Non-ASCII byte encountered but unicode is disabled");
    //     }

    //     byte kind = STRUCTURAL[c];

    //     switch (kind) {
    //         case S_LBRACE:   return makeStructuralToken(JsonTokenType.LEFT_BRACE);
    //         case S_RBRACE:   return makeStructuralToken(JsonTokenType.RIGHT_BRACE);
    //         case S_LBRACKET: return makeStructuralToken(JsonTokenType.LEFT_BRACKET);
    //         case S_RBRACKET: return makeStructuralToken(JsonTokenType.RIGHT_BRACKET);
    //         case S_COLON:    return makeStructuralToken(JsonTokenType.COLON);
    //         case S_COMMA:    return makeStructuralToken(JsonTokenType.COMMA);
    //         case S_STRING:   return stringScanner.scan(c);
    //         default:         return numberOrIdentifierAscii(c);
    //     }
    // }

    // private JsonToken handleUnicodeToken(char c) {

    //     if (c > 127) {
    //         JsonToken tok = handleUnicodeChar(c);
    //         if (tok == null) return nextToken();
    //         return tok;
    //     }

    //     byte kind = STRUCTURAL[c];

    //     switch (kind) {
    //         case S_LBRACE:   return makeStructuralToken(JsonTokenType.LEFT_BRACE);
    //         case S_RBRACE:   return makeStructuralToken(JsonTokenType.RIGHT_BRACE);
    //         case S_LBRACKET: return makeStructuralToken(JsonTokenType.LEFT_BRACKET);
    //         case S_RBRACKET: return makeStructuralToken(JsonTokenType.RIGHT_BRACKET);
    //         case S_COLON:    return makeStructuralToken(JsonTokenType.COLON);
    //         case S_COMMA:    return makeStructuralToken(JsonTokenType.COMMA);
    //         case S_STRING:   return stringScanner.scan(c);
    //         default:         return numberOrIdentifierUnicode(c);
    //     }
    // }




    // private JsonToken scanStringAscii(char quote) {
    //     // SIMD escape detection: only check for quote and control chars
    //     int pos = ((SourceByteBuffer) buffer).scanStringSimdAsciiOnly(buffer.offset(), (byte) quote);
    //     buffer.advanceBy(pos - buffer.offset());
    //     return scanStringAsciiScalar(quote);
    // }

    // private JsonToken scanNumberAscii(char first) {
    //     if (first > 127) throw error("Non-ASCII digit");
    //     return scanNumberOrIdentifierAscii(first);
    // }

    // private JsonToken scanIdentifierAscii(char first) {
    //     if (first > 127) throw error("Non-ASCII identifier");
    //     return scanIdentifierAsciiScalar(first);
    // }

    // private void skipAsciiWhitespaceSimd() {
    //     ((SourceByteBuffer) buffer).skipWhitespaceAndFormattingSimd();
    // }

    // private void skipUnicodeWhitespaceScalar() {
    //     advanceWhitespaceAndCommentsUnicode();
    // }

    private final boolean simdStringScanner(char quoteChar) {
        SourceByteBuffer bb = (SourceByteBuffer) buffer;

        byte quoteByte = (byte) quoteChar;
        int pos = bb.offset();
        int simdPos = bb.scanStringAsciiSimd(pos, quoteByte);

        bb.advanceBy(simdPos - pos);

        return scanString(quoteChar); // scalar fallback for escapes/Unicode
    }



    //
    // Token Creation
    //

    private JsonToken classifyLexeme(int startOffset, int line, int column, String lexeme) {
        switch (lexeme) {
            case TRUE:
                return new JsonLexemeBackedToken(
                    line, column, startOffset,
                    JsonTokenType.BOOLEAN,
                    JsonLiteral.TRUE
                );
            case FALSE:
                return new JsonLexemeBackedToken(
                    line, column, startOffset,
                    JsonTokenType.BOOLEAN,
                    JsonLiteral.FALSE
                );

            case NULL:
                return new JsonLexemeBackedToken(
                    line, column, startOffset,
                    JsonTokenType.NULL,
                    JsonLiteral.NULL
                );

            case INFINITY:
                return new JsonLexemeBackedToken(
                    line, column, startOffset,
                    JsonTokenType.IDENTIFIER,
                    JsonLiteral.INFINITY
                );

            case NAN:
                return new JsonLexemeBackedToken(
                    line, column, startOffset,
                    JsonTokenType.IDENTIFIER,
                    JsonLiteral.NAN
                );

            default:
                return factory.makeIdentifierToken(startOffset, line, column, lexeme);
        }
    }

    private JsonToken makeStructuralToken(JsonTokenType type) {
        buffer.advance();
        return new JsonStructuralToken(
            buffer.line(),
            buffer.column(),
            buffer.offset(),
            type
        );
    }

    private JsonToken makeEofToken() {
        return new JsonLexemeBackedToken(
            buffer.windowStartLine(),
            buffer.windowStartColumn(),
            buffer.windowStartOffset(),
            JsonTokenType.EOF
        );
    }

    private JsonLexemeBackedToken makeErrorToken(String message, int line, int column) {
        return new JsonLexemeBackedToken(
            line,
            column,
            buffer.windowStartOffset(),
            JsonTokenType.ERROR,
            message,
            null,
            QuoteStyle.PLAIN
        );
    }

    private JsonLexemeBackedToken toCommentToken(JsonComment c) {
        return new JsonLexemeBackedToken(
            c.getLine(),
            c.getColumn(),
            0, // TODO: Use a real value here
            JsonTokenType.COMMENT,
            c.getLexeme(),
            c.getValue(),
            QuoteStyle.PLAIN
        );
    }

    private interface TokenFactory {
        JsonToken makeNumberToken(int startOffset, int line, int column, String lexeme);
        JsonToken makeStringToken(int startOffset, int line, int column, QuoteStyle qs);
        JsonToken makeIdentifierToken(int startOffset, int line, int column, String lexeme);
    }

    private static class BufferBackedTokenFactory implements TokenFactory {
        private final SourceBuffer buffer;
        private final LexemeProvider lp;

        public BufferBackedTokenFactory(SourceBuffer buffer, LexemeProvider lp) {
            this.buffer = buffer;
            this.lp = lp;
        }

        public JsonBufferBackedToken makeNumberToken(int startOffset, int line, int column, String lexeme) {
            final int offset = buffer.offset();
            return new JsonBufferBackedToken(
                lp,
                line,
                column,
                startOffset,
                offset,
                JsonTokenType.NUMBER
            );
        }

        public JsonBufferBackedToken makeStringToken(int startOffset, int line, int column, QuoteStyle qs) {
            final int offset = buffer.offset();
            return new JsonBufferBackedToken(
                lp,
                line,
                column,
                startOffset,
                offset,
                qs
            );
        }

        public JsonBufferBackedToken makeIdentifierToken(int startOffset, int line, int column, String lexeme) {
            final int offset = buffer.offset();
            return new JsonBufferBackedToken(
                lp,
                line,
                column,
                startOffset,
                offset,
                JsonTokenType.IDENTIFIER
            );
        }
    }

    private static class LexemeBackedTokenFactory implements TokenFactory {
        private final SourceBuffer buffer;

        public LexemeBackedTokenFactory(SourceBuffer buffer) {
            this.buffer = buffer;
        }

        public JsonLexemeBackedToken makeNumberToken(int startOffset, int line, int column, String lexeme) {
            return new JsonLexemeBackedToken(
                line,
                column,
                startOffset,
                JsonTokenType.NUMBER,
                lexeme
            );
        }

        public JsonLexemeBackedToken makeStringToken(int startOffset, int line, int column, QuoteStyle qs) {
            // TODO: HOTSPOT
            String lexeme = buffer.getTokenWindowLexeme();

            String content = lexeme.substring(1, lexeme.length() - 1);

            return new JsonLexemeBackedToken(
                line,
                column,
                startOffset,
                JsonTokenType.STRING,
                lexeme,
                content,
                qs
            );
        }

        public JsonLexemeBackedToken makeIdentifierToken(int startOffset, int line, int column, String lexeme) {
            return new JsonLexemeBackedToken(
                line,
                column,
                startOffset,
                JsonTokenType.IDENTIFIER,
                lexeme
            );
        }
    }
}