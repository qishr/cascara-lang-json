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
import io.github.qishr.cascara.lang.json.token.JsonSourceByteBuffer;
import io.github.qishr.cascara.lang.json.util.JsonOptions;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

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
    private static final boolean[] NUM_START   = new boolean[128];
    static {
        for (char c = '0'; c <= '9'; c++) DIGIT[c] = true;
        for (char c = '0'; c <= '9'; c++) HEX[c] = true;
        for (char c = 'a'; c <= 'f'; c++) HEX[c] = true;
        for (char c = 'A'; c <= 'F'; c++) HEX[c] = true;
        for (char c = '0'; c <= '9'; c++) NUM_START[c] = true;
        NUM_START['.'] = true;
        NUM_START['+'] = true;
        NUM_START['-'] = true;
    }

    private static final byte[] STRUCTURAL = new byte[128];

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

    private SourceBuffer buffer;
    private List<JsonToken> tokens;
    private final List<JsonComment> pendingComments = new ArrayList<>();

    private TokenFactory factory;

    @FunctionalInterface
    private interface TokenHandler {
        JsonToken handle();
    }

    @FunctionalInterface
    private interface TokenScanner {
        JsonToken scan(char c);
    }

    @FunctionalInterface
    private interface StringScanner {
        boolean scan(char quoteChar);
    }

    @FunctionalInterface
    private interface NumberScanner {
        void scan(char first);
    }

    @FunctionalInterface
    private interface DigitScanner {
        void scan();
    }

    @FunctionalInterface
    private interface IdentifierScanner {
        void scan();
    }

    private TokenHandler tokenHandler;
    private TokenScanner tokenScanner;
    private StringScanner stringScanner;
    private NumberScanner numberScanner;
    private DigitScanner digitScanner;
    private IdentifierScanner identifierScanner;
    private Runnable triviaHandler;

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

    private void applyOptions(JsonOptions options) {
        this.ALLOW_COMMENTS              = options.allowComments();
        this.ALLOW_HEXADECIMAL_NUMBERS   = options.allowHexadecimalNumbers();
        this.ALLOW_INFINITY_AND_NAN      = options.allowJson5Numbers();
        this.ALLOW_UNICODE               = options.allowUnicode();
        this.CAPTURE_COMMENTS            = options.captureComments();
    }

    @Override protected JsonTokenizer self() { return this; }

    @Override
    public void open(String text) {
        buffer = setupStringBuffer(text);
        factory = setupTokenFactory(buffer);
        setupHandlers();
        skipBom();
    }

    public void open(byte[] data) {
        // Strict UTF‑8 validation (no conversion to String)
        validateUtf8(data);

        // Keep SIMD path fully active
        buffer = setupByteBuffer(data);
        factory = setupTokenFactory(buffer);
        setupHandlers();
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

    public JsonToken nextToken() {
        return tokenHandler.handle();
    }

    public JsonToken nextTokenWithoutComments() {
        triviaHandler.run();
        return buffer.isAtEnd() ? makeEofToken() : tokenScanner.scan(buffer.peek());
    }

    public JsonToken nextTokenWithComments() {
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

        final char c = buffer.peek();

        // Call either scanTokenAscii or scanTokenAsciiOrUnicode
        final JsonToken tok = tokenScanner.scan(c);

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

    private void skipBom() {
        if (buffer.peek() == '\uFEFF') {
            buffer.advance();
        }
    }

    private JsonToken scanTokenAsciiOrUnicode(char c) {
        if (c < 128) {
            return scanTokenAscii(c);
        }

        // Unicode path (identifier, digit, whitespace)
        final JsonToken tok = scanTokenUnicode(c);
        if (tok == null) return nextToken();
        return tok;
    }

    private JsonToken scanTokenAscii(char c) {
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

    // // TODO: Make this use SIMD
    // private JsonToken scanTokenAsciiSimd(char c) {
    //     // Just use the current char; no SIMD structural skip
    //     switch (c) {
    //         case '{': return makeStructuralToken(JsonTokenType.LEFT_BRACE);
    //         case '}': return makeStructuralToken(JsonTokenType.RIGHT_BRACE);
    //         case '[': return makeStructuralToken(JsonTokenType.LEFT_BRACKET);
    //         case ']': return makeStructuralToken(JsonTokenType.RIGHT_BRACKET);
    //         case ':': return makeStructuralToken(JsonTokenType.COLON);
    //         case ',': return makeStructuralToken(JsonTokenType.COMMA);

    //         case '"':
    //         case '\'':
    //             return scanStringToken(c);

    //         default:
    //             return scanNumberOrIdentifierOrError(c);
    //     }
    // }

    private JsonToken scanTokenUnicode(char c) {
        buffer.startTokenWindow();
        final int startOffset = buffer.windowStartOffset();
        final int line        = buffer.windowStartLine();
        final int column      = buffer.windowStartColumn();

        // Unicode whitespace (JSON5)
        if (Character.isWhitespace(c)) {
            // skip it
            buffer.advance();
            return null; // caller will continue skipping trivia
        }

        // Unicode identifier start (JSON5)
        if (Character.isUnicodeIdentifierStart(c)) {
            scanIdentifierUnicode();
            String lexeme = buffer.getTokenWindowLexeme();
            return factory.makeIdentifierToken(line, column, startOffset, lexeme);
        }

        // Unicode identifier part (rare case)
        if (Character.isUnicodeIdentifierPart(c)) {
            scanIdentifierUnicode();
            String lexeme = buffer.getTokenWindowLexeme();
            return factory.makeIdentifierToken(line, column, startOffset, lexeme);
        }

        // Unicode digit (JSON5)
        if (Character.isDigit(c)) {
            scanNumberUnicode(c);
            String lexeme = buffer.getTokenWindowLexeme();
            return factory.makeNumberToken(line, column, startOffset, lexeme);
        }

        // Otherwise - UNKNOWN
        buffer.advance();
        return makeErrorToken("Unexpected character '" + c + "'", line, column);
    }

    private JsonToken scanNumberOrIdentifierOrError(char startChar) {
        final int startOffset = buffer.offset();
        final int startLine   = buffer.line();
        final int startColumn = buffer.column();

        buffer.startTokenWindow();

        if (NUM_START[startChar]) {
            numberScanner.scan(startChar);
            String lexeme = buffer.getTokenWindowLexeme();



            // TODO: The following code looks rather slow

            // Reject numbers with no digits at all: "-", "+", "."
            boolean hasDigit = false;
            for (int i = 0; i < lexeme.length(); i++) {
                char ch = lexeme.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    hasDigit = true;
                    break;
                }
            }
            if (!hasDigit) {
                // Allow JSON5 Infinity/NaN forms when enabled
                if (ALLOW_INFINITY_AND_NAN) {
                    if (lexeme.equals("Infinity") ||
                        lexeme.equals("+Infinity") ||
                        lexeme.equals("-Infinity") ||
                        lexeme.equals("NaN") ||
                        lexeme.equals("+NaN") ||
                        lexeme.equals("-NaN")) {
                        return factory.makeNumberToken(startLine, startColumn, startOffset, lexeme);
                    }
                }

                // Otherwise: lone '+', '-', '.', or other non‑digit → error
                return makeErrorToken(
                    "Unexpected character '" + startChar + "'",
                    startLine,
                    startColumn
                );
            }




            return factory.makeNumberToken(startLine, startColumn, startOffset, lexeme);
        }


        // IDENTIFIER?
        if (startChar < 128 && IDENT_START[startChar]) {
            identifierScanner.scan();
            return classifyLexeme(startOffset, startLine, startColumn, buffer.getTokenWindowLexeme());
        }

        // ERROR
        buffer.advance();
        return makeErrorToken(
            "Unexpected character '" + startChar + "'",
            startLine,
            startColumn
        );
    }

    //
    // Strings
    //

    private JsonToken scanStringToken(char quoteChar) {
        final int startOffset = buffer.offset(); // actual position of the quote
        final int startLine   = buffer.line();
        final int startColumn = buffer.column();

        buffer.startTokenWindow();
        buffer.advance(); // consume opening quote

        // Call either scanString or scanStringSimd
        final boolean ok = stringScanner.scan(quoteChar);

        final QuoteStyle qs = (quoteChar == '"')
            ? QuoteStyle.DOUBLE
            : QuoteStyle.SINGLE;

        if (qs == QuoteStyle.SINGLE && !options.allowSingleQuotedStrings()) {
            return makeErrorToken("Single-quoted strings are not allowed in strict JSON", startLine, startColumn);
        }

        if (!ok) {
            return makeErrorToken("Unterminated string literal", startLine, startColumn);
        }

        return factory.makeStringToken(startLine, startColumn, startOffset, qs);
    }

    private final boolean scanString(char quoteChar) {
        boolean invalidUnicode = false;
        boolean pendingHighSurrogate = false;

        while (!buffer.isAtEnd()) {
            final char next = buffer.advance();

            // Normal termination
            if (next == quoteChar) {
                // invalid if we ended with an unmatched high surrogate
                return !invalidUnicode && !pendingHighSurrogate;
            }

            // Raw unicode check
            if (!ALLOW_UNICODE && next > 127) {
                invalidUnicode = true;
            }

            // Escape sequence
            if (next == '\\' && !buffer.isAtEnd()) {
                final char esc = buffer.advance();

                switch (esc) {
                    case '"':
                    case '\'':
                    case '\\':
                    case '/':
                    case 'b':
                    case 'f':
                    case 'n':
                    case 'r':
                    case 't':
                        // simple escape, nothing more to do
                        break;

                    // case 'u':
                    //     // must have 4 hex digits
                    //     for (int i = 0; i < 4; i++) {
                    //         if (buffer.isAtEnd()) {
                    //             return false;
                    //         }
                    //         char h = buffer.advance();
                    //         if (!((h >= '0' && h <= '9') ||
                    //             (h >= 'A' && h <= 'F') ||
                    //             (h >= 'a' && h <= 'f'))) {
                    //             return false;
                    //         }
                    //     }
                    //     break;
                    case 'u': {
                        int codeUnit = 0;
                        for (int i = 0; i < 4; i++) {
                            if (buffer.isAtEnd()) return false;
                            char h = buffer.advance();
                            int v;
                            if (h >= '0' && h <= '9') v = h - '0';
                            else if (h >= 'A' && h <= 'F') v = 10 + (h - 'A');
                            else if (h >= 'a' && h <= 'f') v = 10 + (h - 'a');
                            else return false;
                            codeUnit = (codeUnit << 4) | v;
                        }

                        if (isHighSurrogate(codeUnit)) {
                            // must be followed by a low surrogate escape
                            if (pendingHighSurrogate) {
                                // two highs in a row → invalid
                                return false;
                            }
                            pendingHighSurrogate = true;
                        } else if (isLowSurrogate(codeUnit)) {
                            if (!pendingHighSurrogate) {
                                // lonely low surrogate → invalid
                                return false;
                            }
                            pendingHighSurrogate = false;
                        } else {
                            // non‑surrogate code unit
                            if (pendingHighSurrogate) {
                                // high surrogate not followed by low → invalid
                                return false;
                            }
                        }
                        break;
                    }

                    default:
                        // invalid escape: \0, \x, \v, etc.
                        return false;
                }
            }
        }

        // EOF before closing quote - invalid string
        return false;
    }

    private boolean scanStringSimd(char quoteChar) {
        final JsonSourceByteBuffer bb = (JsonSourceByteBuffer) buffer;
        final byte quoteByte = (byte) quoteChar;
        boolean pendingHighSurrogate = false;

        int pos = bb.offset();
        int len = bb.length();

        while (pos < len) {
            // SIMD: find first quote, backslash, or control char
            int next = bb.scanStringAsciiSimd(pos, quoteByte);

            if (next >= len) {
                // EOF before closing quote
                bb.setOffset(len);
                return false;
            }

            // Advance logical offset/line/column to `next`
            int delta = next - bb.offset();
            if (delta > 0) {
                bb.advanceBy(delta);
            }

            // We are now at `next`
            final char c = bb.peek();

            if (c == quoteChar) {
                bb.advance();
                return !pendingHighSurrogate;
            }





            // TODO: This looks slow...

            if (c == '\\') {
                bb.advance(); // consume '\'
                if (bb.isAtEnd()) {
                    return false;
                }

                final char esc = bb.advance(); // escaped char

                switch (esc) {
                    case '"':
                    case '\'':
                    case '\\':
                    case '/':
                    case 'b':
                    case 'f':
                    case 'n':
                    case 'r':
                    case 't':
                        // simple escape
                        break;

                    // case 'u':
                    //     for (int i = 0; i < 4; i++) {
                    //         if (bb.isAtEnd()) {
                    //             return false;
                    //         }
                    //         char h = bb.advance();
                    //         if (!((h >= '0' && h <= '9') ||
                    //               (h >= 'A' && h <= 'F') ||
                    //               (h >= 'a' && h <= 'f'))) {
                    //             return false;
                    //         }
                    //     }
                    //     break;

                        case 'u': {
                            int codeUnit = 0;
                            for (int i = 0; i < 4; i++) {
                                if (bb.isAtEnd()) return false;
                                char h = bb.advance();
                                int v;
                                if (h >= '0' && h <= '9') v = h - '0';
                                else if (h >= 'A' && h <= 'F') v = 10 + (h - 'A');
                                else if (h >= 'a' && h <= 'f') v = 10 + (h - 'a');
                                else return false;
                                codeUnit = (codeUnit << 4) | v;
                            }

                            if (isHighSurrogate(codeUnit)) {
                                // must be followed by a low surrogate escape
                                if (pendingHighSurrogate) {
                                    // two highs in a row → invalid
                                    return false;
                                }
                                pendingHighSurrogate = true;
                            } else if (isLowSurrogate(codeUnit)) {
                                if (!pendingHighSurrogate) {
                                    // lonely low surrogate → invalid
                                    return false;
                                }
                                pendingHighSurrogate = false;
                            } else {
                                // non‑surrogate code unit
                                if (pendingHighSurrogate) {
                                    // high surrogate not followed by low → invalid
                                    return false;
                                }
                            }
                            break;
                        }

                    default:
                        return false;
                }

                // Continue SIMD from new position
                pos = bb.offset();
                len = bb.length();
                continue;
            }




            if (c < 0x20) {
                // Control char inside string → invalid
                return false;
            }

            // Should not happen (non‑interesting byte), but advance conservatively
            bb.advance();
            pos = bb.offset();
        }

        // EOF before closing quote
        return false;
    }

    //
    // Numbers
    //

    private void scanNumberAscii(char startChar) {
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

        digitScanner.scan();
    }

    private void scanDigits() {
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

    private void scanDigitsSimd() {
        final SimdCapableBuffer simd = (SimdCapableBuffer)buffer;

        int pos = simd.scanDigitsSimd(buffer.offset());
        buffer.setOffset(pos);

        // 4. Fractional part
        if (buffer.peek() == '.') {
            buffer.advance();
            pos = simd.scanDigitsSimd(buffer.offset());
            buffer.setOffset(pos);
        }

        // 5. Exponent part
        char c = buffer.peek();
        if (c == 'e' || c == 'E') {
            buffer.advance();
            c = buffer.peek();
            if (c == '+' || c == '-') {
                buffer.advance();
            }
            pos = simd.scanDigitsSimd(buffer.offset());
            buffer.setOffset(pos);
        }
    }

    private void scanNumberUnicode(char startChar) {
        buffer.advance(); // consume first digit

        // integer part
        while (!buffer.isAtEnd()) {
            final char c = buffer.peek();
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
    // Identifiers
    //

    private void scanIdentifierAscii() {
        // We already consumed the first character in nextToken()
        while (true) {
            final char c = buffer.peek();
            if (c < 128 && IDENT_PART[c]) {
                buffer.advance();
                continue;
            }
            break;
        }
    }

    private void scanIdentifierSimd() {
        final JsonSourceByteBuffer bb = (JsonSourceByteBuffer) buffer;

        int pos = bb.offset();
        int len = bb.length();

        // SIMD classify identifier characters:
        // IDENT_PART: [A-Za-z0-9_$]
        final byte A = (byte)'A';
        final byte Z = (byte)'Z';
        final byte a = (byte)'a';
        final byte z = (byte)'z';
        final byte zero = (byte)'0';
        final byte nine = (byte)'9';
        final byte us = (byte)'_';
        final byte dl = (byte)'$';

        final VectorSpecies<Byte> S = ByteVector.SPECIES_128;

        while (pos < len) {
            int remaining = len - pos;

            if (remaining < S.length()) {
                // scalar tail
                while (!bb.isAtEnd()) {
                    char c = bb.peek();
                    if (c < 128 && IDENT_PART[c]) {
                        bb.advance();
                        continue;
                    }
                    return;
                }
                return;
            }

            ByteVector vec = ByteVector.fromArray(S, bb.raw, pos);

            // ASCII only (IDENT_PART is ASCII)
            VectorMask<Byte> mAscii = vec.compare(VectorOperators.LT, (byte)0x80);

            // A–Z
            VectorMask<Byte> mAZ =
                vec.compare(VectorOperators.GE, A)
                   .and(vec.compare(VectorOperators.LE, Z));

            // a–z
            VectorMask<Byte> maz =
                vec.compare(VectorOperators.GE, a)
                   .and(vec.compare(VectorOperators.LE, z));

            // 0–9
            VectorMask<Byte> m09 =
                vec.compare(VectorOperators.GE, zero)
                   .and(vec.compare(VectorOperators.LE, nine));

            // '_' or '$'
            VectorMask<Byte> mus = vec.compare(VectorOperators.EQ, us);
            VectorMask<Byte> mdl = vec.compare(VectorOperators.EQ, dl);

            // Combine all identifier masks
            long maskIdent =
                mAscii.toLong() &
                (mAZ.or(maz).or(m09).or(mus).or(mdl)).toLong();

            if (maskIdent == 0L) {
                // First byte is non-identifier
                return;
            }

            // Find first non-identifier byte
            long maskNonIdent = ~maskIdent;

            if (maskNonIdent != 0L) {
                int firstBad = Long.numberOfTrailingZeros(maskNonIdent);
                int end = pos + firstBad;

                bb.advanceBy(end - bb.offset());
                return;
            }

            // All 128 bytes are identifier chars
            bb.advanceBy(S.length());
            pos = bb.offset();
        }
    }

    private void scanIdentifierUnicode() {
        while (!buffer.isAtEnd()) {
            final char c = buffer.peek();
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

    //
    // Comments
    //

    private String scanSingleLineComment() {
        buffer.advance(); // '/'
        buffer.advance(); // '/'

        while (true) {
            final char c = buffer.peek();
            if (c == '\n' || c == '\r' || c == '\0') {
                break;
            }
            buffer.advance();
        }

        if (buffer.peek() == '\n' || buffer.peek() == '\r') {
            buffer.advance();
        }

        // Full lexeme, including slashes
        final String lexeme = buffer.getTokenWindowLexeme();

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
            final char c = buffer.peek();
            final char n = buffer.peekNext();

            // End of comment: "*/"
            if (c == '*' && n == '/') {
                buffer.advance(); // '*'
                buffer.advance(); // '/'
                break;
            }

            buffer.advance();
        }

        // Extract inner text: everything between "/*" and "*/"
        final String lexeme = buffer.getTokenWindowLexeme();
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
            final char c = buffer.peek();
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
                final char n = buffer.peekNext();

                // Single-line comment
                if (n == '/') {
                    if (capture) buffer.startTokenWindow();
                    final int line = buffer.line();
                    final int col  = buffer.column();
                    final String value = scanSingleLineComment();
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
                    final int line = buffer.line();
                    final int col = buffer.column();
                    final String value = scanMultiLineComment();
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
    // Helpers
    //

    private static boolean isHighSurrogate(int codeUnit) {
        return codeUnit >= 0xD800 && codeUnit <= 0xDBFF;
    }

    private static boolean isLowSurrogate(int codeUnit) {
        return codeUnit >= 0xDC00 && codeUnit <= 0xDFFF;
    }

    // private static char[] decodeUtf8Strict(byte[] data) {
    //     char[] out = new char[data.length]; // worst case
    //     int o = 0;

    //     for (int i = 0; i < data.length; ) {
    //         int b = data[i] & 0xFF;

    //         if (b < 0x80) {
    //             // ASCII
    //             out[o++] = (char)b;
    //             i++;
    //             continue;
    //         }

    //         // Reject continuation bytes as leading bytes
    //         if (b >= 0x80 && b < 0xC2) {
    //             throw new IllegalArgumentException("Invalid UTF-8: lone continuation byte");
    //         }

    //         // 2-byte sequence
    //         if (b < 0xE0) {
    //             if (i + 1 >= data.length) throw new IllegalArgumentException("Truncated UTF-8");
    //             int b2 = data[i+1] & 0xFF;
    //             if ((b2 & 0xC0) != 0x80) throw new IllegalArgumentException("Invalid UTF-8 continuation");
    //             int cp = ((b & 0x1F) << 6) | (b2 & 0x3F);
    //             if (cp < 0x80) throw new IllegalArgumentException("Overlong UTF-8");
    //             out[o++] = (char)cp;
    //             i += 2;
    //             continue;
    //         }

    //         // 3-byte sequence
    //         if (b < 0xF0) {
    //             if (i + 2 >= data.length) throw new IllegalArgumentException("Truncated UTF-8");
    //             int b2 = data[i+1] & 0xFF;
    //             int b3 = data[i+2] & 0xFF;
    //             if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80)
    //                 throw new IllegalArgumentException("Invalid UTF-8 continuation");

    //             int cp = ((b & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);

    //             // Reject surrogates
    //             if (cp >= 0xD800 && cp <= 0xDFFF)
    //                 throw new IllegalArgumentException("Invalid UTF-8: surrogate");

    //             // Reject overlong
    //             if (cp < 0x800)
    //                 throw new IllegalArgumentException("Overlong UTF-8");

    //             out[o++] = (char)cp;
    //             i += 3;
    //             continue;
    //         }

    //         // 4-byte sequence
    //         if (b < 0xF5) {
    //             if (i + 3 >= data.length) throw new IllegalArgumentException("Truncated UTF-8");
    //             int b2 = data[i+1] & 0xFF;
    //             int b3 = data[i+2] & 0xFF;
    //             int b4 = data[i+3] & 0xFF;
    //             if ((b2 & 0xC0) != 0x80 ||
    //                 (b3 & 0xC0) != 0x80 ||
    //                 (b4 & 0xC0) != 0x80)
    //                 throw new IllegalArgumentException("Invalid UTF-8 continuation");

    //             int cp = ((b & 0x07) << 18) |
    //                      ((b2 & 0x3F) << 12) |
    //                      ((b3 & 0x3F) << 6) |
    //                      (b4 & 0x3F);

    //             if (cp < 0x10000)
    //                 throw new IllegalArgumentException("Overlong UTF-8");

    //             if (cp > 0x10FFFF)
    //                 throw new IllegalArgumentException("Invalid UTF-8: out of range");

    //             // Encode surrogate pair
    //             cp -= 0x10000;
    //             out[o++] = (char)(0xD800 | (cp >> 10));
    //             out[o++] = (char)(0xDC00 | (cp & 0x3FF));

    //             i += 4;
    //             continue;
    //         }

    //         throw new IllegalArgumentException("Invalid UTF-8 leading byte");
    //     }

    //     char[] trimmed = new char[o];
    //     System.arraycopy(out, 0, trimmed, 0, o);
    //     return trimmed;
    // }

    private static void validateUtf8(byte[] data) {
        int i = 0;
        int len = data.length;

        while (i < len) {
            int b1 = data[i] & 0xFF;

            // ASCII
            if (b1 < 0x80) {
                i++;
                continue;
            }

            // Reject continuation bytes as leading bytes
            if (b1 < 0xC2) {
                throw new IllegalArgumentException("Invalid UTF-8: lone continuation byte");
            }

            // 2-byte sequence
            if (b1 < 0xE0) {
                if (i + 1 >= len) throw new IllegalArgumentException("Invalid UTF-8: truncated");
                int b2 = data[i+1] & 0xFF;
                if ((b2 & 0xC0) != 0x80) throw new IllegalArgumentException("Invalid UTF-8 continuation");
                int cp = ((b1 & 0x1F) << 6) | (b2 & 0x3F);
                if (cp < 0x80) throw new IllegalArgumentException("Invalid UTF-8: overlong");
                i += 2;
                continue;
            }

            // 3-byte sequence
            if (b1 < 0xF0) {
                if (i + 2 >= len) throw new IllegalArgumentException("Invalid UTF-8: truncated");
                int b2 = data[i+1] & 0xFF;
                int b3 = data[i+2] & 0xFF;
                if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80)
                    throw new IllegalArgumentException("Invalid UTF-8 continuation");

                int cp = ((b1 & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);

                if (cp < 0x800) throw new IllegalArgumentException("Invalid UTF-8: overlong");
                if (cp >= 0xD800 && cp <= 0xDFFF)
                    throw new IllegalArgumentException("Invalid UTF-8: surrogate");

                i += 3;
                continue;
            }

            // 4-byte sequence
            if (b1 < 0xF5) {
                if (i + 3 >= len) throw new IllegalArgumentException("Invalid UTF-8: truncated");
                int b2 = data[i+1] & 0xFF;
                int b3 = data[i+2] & 0xFF;
                int b4 = data[i+3] & 0xFF;

                if ((b2 & 0xC0) != 0x80 ||
                    (b3 & 0xC0) != 0x80 ||
                    (b4 & 0xC0) != 0x80)
                    throw new IllegalArgumentException("Invalid UTF-8 continuation");

                int cp = ((b1 & 0x07) << 18) |
                         ((b2 & 0x3F) << 12) |
                         ((b3 & 0x3F) << 6) |
                         (b4 & 0x3F);

                if (cp < 0x10000) throw new IllegalArgumentException("Invalid UTF-8: overlong");
                if (cp > 0x10FFFF) throw new IllegalArgumentException("Invalid UTF-8: out of range");

                i += 4;
                continue;
            }

            throw new IllegalArgumentException("Invalid UTF-8 leading byte");
        }
    }

    //
    // Setup
    //

    /// Token factory selection
    private TokenFactory setupTokenFactory(SourceBuffer buffer) {
        if (buffer instanceof LexemeProvider lp) {
            return new BufferBackedTokenFactory(buffer, lp);
        } else {
            return new LexemeBackedTokenFactory(buffer);
        }
    }

    private SourceBuffer setupByteBuffer(byte[] data) {
        // SIMD & no JSON5 unquoted keys - use byte-based buffer
        if (options.useSimd() && !options.allowUnquotedKeys()) {
            return new JsonSourceByteBuffer(data);
        } else {
            // TODO: A new SourceByteBuffer that's not JSON-specific
            return new SourceStringBuffer(new String(data));
        }
    }

    private SourceBuffer setupStringBuffer(String text) {
        // SIMD & no JSON5 unquoted keys - use byte-based buffer
        if (options.useSimd() && !options.allowUnquotedKeys()) {
            byte[] raw = text.getBytes(StandardCharsets.UTF_8);
            return new JsonSourceByteBuffer(raw);
        } else {
            return new SourceStringBuffer(text);
        }
    }

    private void setupHandlers() {
        // SIMD & no JSON5 unquoted keys - use byte-based buffer
        if (buffer instanceof JsonSourceByteBuffer byteBuffer) {

            digitScanner = this::scanDigitsSimd;

            // Whitespace / trivia skipping
            if (ALLOW_COMMENTS) {
                tokenHandler = this::nextTokenWithComments;
                triviaHandler = byteBuffer::skipWhitespaceAndFormattingSimd;
            } else {
                tokenHandler = this::nextTokenWithoutComments;
                triviaHandler = byteBuffer::skipWhitespaceSimd;
            }

            if (!ALLOW_UNICODE) {


                tokenScanner = this::scanTokenAscii;
                // tokenScanner = this::scanTokenAsciiSimd;


                stringScanner = this::scanStringSimd;
                numberScanner = this::scanNumberAscii;
                identifierScanner = this::scanIdentifierSimd;
            }

            buffer = byteBuffer;
        } else {
            // JSON5 identifiers or SIMD disabled - use char-based buffer
            digitScanner = this::scanDigits;
            triviaHandler = this::advanceWhitespaceAndComments;

            if (ALLOW_COMMENTS) {
                tokenHandler = this::nextTokenWithComments;
            } else {
                tokenHandler = this::nextTokenWithoutComments;
            }

            if (!ALLOW_UNICODE) {
                tokenScanner = this::scanTokenAscii;
                stringScanner = this::scanString;
                numberScanner = this::scanNumberAscii;
                identifierScanner = this::scanIdentifierAscii;
            }
        }

        if (ALLOW_UNICODE) {
            tokenScanner = this::scanTokenAsciiOrUnicode;
            stringScanner = this::scanString;
            numberScanner = this::scanNumberUnicode;
            numberScanner = this::scanNumberAscii;
            identifierScanner = this::scanIdentifierUnicode;
        }
    }

    private SourceBuffer setupStreamBuffer(InputStream stream) {
        final SourceInputStreamBuffer buffer = new SourceInputStreamBuffer(stream);

        // SourceInputStreamBuffer doesn't currently implement SimdCapableBuffer
        // so this will be simpler than setupStringBuffer.

        digitScanner = this::scanDigits;
        triviaHandler = this::advanceWhitespaceAndComments;

        if (ALLOW_COMMENTS) {
            tokenHandler = this::nextTokenWithComments;
        } else {
            tokenHandler = this::nextTokenWithoutComments;
        }
        if (ALLOW_UNICODE) {
            tokenScanner = this::scanTokenAsciiOrUnicode;
            stringScanner = this::scanString;
            numberScanner = this::scanNumberUnicode;
            numberScanner = this::scanNumberAscii;
            identifierScanner = this::scanIdentifierUnicode;
        } else {
            tokenScanner = this::scanTokenAscii;
            stringScanner = this::scanString;
            numberScanner = this::scanNumberAscii;
            identifierScanner = this::scanIdentifierAscii;
        }

        return buffer;
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
                return factory.makeIdentifierToken(line, column, startOffset, lexeme);
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
        JsonToken makeNumberToken(int line, int column, int startOffset, String lexeme);
        JsonToken makeStringToken(int line, int column, int startOffset, QuoteStyle qs);
        JsonToken makeIdentifierToken(int line, int column, int startOffset, String lexeme);
    }

    private static class BufferBackedTokenFactory implements TokenFactory {
        private final SourceBuffer buffer;
        private final LexemeProvider lp;

        public BufferBackedTokenFactory(SourceBuffer buffer, LexemeProvider lp) {
            this.buffer = buffer;
            this.lp = lp;
        }

        @Override
        public JsonBufferBackedToken makeNumberToken(int line, int column, int startOffset, String lexeme) {
            return new JsonBufferBackedToken(
                lp,
                line,
                column,
                startOffset,
                buffer.offset(),
                JsonTokenType.NUMBER
            );
        }

        @Override
        public JsonBufferBackedToken makeStringToken(int line, int column, int startOffset, QuoteStyle qs) {
            return new JsonBufferBackedToken(
                lp,
                line,
                column,
                startOffset,
                buffer.offset(),
                qs
            );
        }

        @Override
        public JsonBufferBackedToken makeIdentifierToken(int line, int column, int startOffset, String lexeme) {
            return new JsonBufferBackedToken(
                lp,
                line,
                column,
                startOffset,
                buffer.offset(),
                JsonTokenType.IDENTIFIER
            );
        }
    }

    private static class LexemeBackedTokenFactory implements TokenFactory {
        private final SourceBuffer buffer;

        public LexemeBackedTokenFactory(SourceBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public JsonLexemeBackedToken makeNumberToken(int line, int column, int startOffset, String lexeme) {
            return new JsonLexemeBackedToken(
                line,
                column,
                startOffset,
                JsonTokenType.NUMBER,
                lexeme
            );
        }

        @Override
        public JsonLexemeBackedToken makeStringToken(int line, int column, int startOffset, QuoteStyle qs) {
            final String lexeme = buffer.getTokenWindowLexeme();
            return new JsonLexemeBackedToken(
                line,
                column,
                startOffset,
                JsonTokenType.STRING,
                lexeme,
                lexeme.substring(1, lexeme.length() - 1),
                qs
            );
        }

        @Override
        public JsonLexemeBackedToken makeIdentifierToken(int line, int column, int startOffset, String lexeme) {
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