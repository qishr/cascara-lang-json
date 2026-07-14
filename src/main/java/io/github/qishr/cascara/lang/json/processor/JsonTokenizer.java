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
import io.github.qishr.cascara.lang.json.exception.JsonDiagnosticCode;
import io.github.qishr.cascara.lang.json.token.JsonBufferBackedToken;
import io.github.qishr.cascara.lang.json.token.JsonComment;
import io.github.qishr.cascara.lang.json.token.JsonErrorToken;
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
    private boolean ALLOW_JSON5_NUMBERS;
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

    @FunctionalInterface
    private interface NumberValidator {
        boolean validate(int start, int end);
    }

    private TokenHandler tokenHandler;
    private TokenScanner tokenScanner;
    private StringScanner stringScanner;
    private NumberScanner numberScanner;
    private DigitScanner digitScanner;
    private IdentifierScanner identifierScanner;
    private Runnable triviaHandler;
    private NumberValidator numberValidator;

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
        this.ALLOW_JSON5_NUMBERS      = options.allowJson5Numbers();
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
        tokens.add(makeEofToken());
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
            if (ALLOW_UNICODE) {
                buffer.advance();
            } else {
                throw new IllegalArgumentException("BOM not allowed in strict JSON");
            }
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
            return factory.makeNumberToken(line, column, startOffset);
        }

        // Otherwise - UNKNOWN
        buffer.advance();
        return makeErrorToken(line, column, JsonDiagnosticCode.UNEXPECTED_CHARACTER, c);
    }

    private JsonToken scanNumberOrIdentifierOrError(char startChar) {
        final int startOffset = buffer.offset();
        final int startLine   = buffer.line();
        final int startColumn = buffer.column();

        buffer.startTokenWindow();

        // NUMBERS?
        if (NUM_START[startChar]) {
            int start = buffer.offset();
            numberScanner.scan(startChar);
            int end = buffer.offset();
            if (!numberValidator.validate(start, end)) {
                return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.INVALID_NUMBER);
            }
            return factory.makeNumberToken(startLine, startColumn, startOffset);
        }

        // IDENTIFIER?
        if (startChar < 128 && IDENT_START[startChar]) {
            identifierScanner.scan();
            return classifyLexeme(startOffset, startLine, startColumn, buffer.getTokenWindowLexeme());
        }

        // ERROR
        buffer.advance();
        return makeErrorToken(
            startLine,
            startColumn,
            JsonDiagnosticCode.UNEXPECTED_CHARACTER,
            startChar
        );
    }

    private boolean validateNumberLexeme(int start, int end) {
        final boolean allowJson5Numbers = ALLOW_JSON5_NUMBERS;

        String lexeme = buffer.getTokenWindowLexeme();

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
            if (allowJson5Numbers) {

                // TODO: I think these are treated as identifiers somethere else?
                // Should they be identifiers or numbers?

                if (lexeme.equals("Infinity") ||
                    lexeme.equals("+Infinity") ||
                    lexeme.equals("-Infinity") ||
                    lexeme.equals("NaN") ||
                    lexeme.equals("+NaN") ||
                    lexeme.equals("-NaN")) {
                    return true;
                }
            }

            return false;
        }

        // Strict JSON number validation (disallow JSON5 forms when options say so)
        if (!allowJson5Numbers) {
            // Leading '+'
            if (lexeme.charAt(0) == '+') {
                return false;
                // return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.NOT_ALLOWED_LEADING_PLUS);
            }

            // Leading zero on integer part: 012, -012
            if (lexeme.charAt(0) == '0' && lexeme.length() > 1 && Character.isDigit(lexeme.charAt(1))) {
                return false;
                // return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.NOT_ALLOWED_LEADING_ZERO);
            }
            if (lexeme.startsWith("-0") && lexeme.length() > 2 && Character.isDigit(lexeme.charAt(2))) {
                return false;
                // return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.NOT_ALLOWED_LEADING_ZERO);
            }

            // Starting with '.' or '-.' (no integer part)
            if (lexeme.charAt(0) == '.' || lexeme.startsWith("-.")) {
                return false;
                // return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.MISSING_INTEGER_PART);
            }

            // Trailing dot: 1., 2., -2.
            if (lexeme.endsWith(".")) {
                return false;
                // return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.NOT_ALLOWED_TRAILING_DOT);
            }

            // Fractional part must have at least one digit after '.'
            int dotPos = lexeme.indexOf('.');
            if (dotPos >= 0) {
                int i = dotPos + 1;
                if (i >= lexeme.length()) {
                    return false;
                    // return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.MISSING_FRACTIONAL_PART);
                }
                char c = lexeme.charAt(i);
                if (c == 'e' || c == 'E') {
                    return false;
                    // return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.MISSING_FRACTIONAL_PART);
                }
            }

            // Exponent must have digits after optional sign
            int ePos = lexeme.indexOf('e');
            if (ePos < 0) ePos = lexeme.indexOf('E');
            if (ePos >= 0) {
                int i = ePos + 1;
                if (i >= lexeme.length()) {
                    return false;
                    // return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.MISSING_EXPONENT);
                }
                char c = lexeme.charAt(i);
                if (c == '+' || c == '-') {
                    i++;
                    if (i >= lexeme.length()) {
                        return false;
                        // return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.MISSING_EXPONENT);
                    }
                }
                if (!Character.isDigit(lexeme.charAt(i))) {
                    return false;
                    // return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.MISSING_EXPONENT_DIGITS);
                }
            }
        }

        return true;

    }


    private boolean validateNumberBytes(int start, int end) {
        JsonSourceByteBuffer bb = (JsonSourceByteBuffer) buffer;
        byte[] raw = bb.raw;

        boolean seenDot = false;
        boolean seenExp = false;
        boolean seenExpSign = false;

        int digitsBeforeDot = 0;
        int digitsAfterDot = 0;
        int digitsAfterExp = 0;

        int i = start;
        byte first = raw[i];

        if (!ALLOW_JSON5_NUMBERS && first == '+') return false;

        if (!ALLOW_JSON5_NUMBERS) {
            if (first == '0' && i + 1 < end) {
                byte next = raw[i + 1];
                if (next >= '0' && next <= '9') return false;
            }
            if (first == '-' && i + 2 < end && raw[i + 1] == '0') {
                byte next = raw[i + 2];
                if (next >= '0' && next <= '9') return false;
            }
        }

        for (; i < end; i++) {
            byte b = raw[i];

            if (b >= '0' && b <= '9') {
                if (!seenDot && !seenExp) digitsBeforeDot++;
                else if (seenDot && !seenExp) digitsAfterDot++;
                else digitsAfterExp++;
                continue;
            }

            if (b == '.') {
                if (seenDot || seenExp) return false;
                seenDot = true;
                continue;
            }

            if (b == 'e' || b == 'E') {
                if (seenExp) return false;
                seenExp = true;
                continue;
            }

            if (b == '+' || b == '-') {
                if (!seenExp || seenExpSign) return false;
                seenExpSign = true;
                continue;
            }

            return false;
        }

        if (digitsBeforeDot == 0) return false;
        if (seenDot && digitsAfterDot == 0) return false;
        if (seenExp && digitsAfterExp == 0) return false;

        return true;
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
            return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.NOT_ALLOWED_SINGLE_QUOTED_STRING);
        }

        if (!ok) {
            return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.UNTERMINATED_STRING);
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
                return !invalidUnicode && !pendingHighSurrogate;
            }

            // Only treat BOM as invalid when Unicode is disallowed
            if (!ALLOW_UNICODE && next == '\uFEFF') {
                invalidUnicode = true;
            }

            // Unescaped control chars are always invalid
            if (next < 0x20) {
                return false;
            }

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
                        break;

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
                            if (pendingHighSurrogate) return false;
                            pendingHighSurrogate = true;
                        } else if (isLowSurrogate(codeUnit)) {
                            if (!pendingHighSurrogate) return false;
                            pendingHighSurrogate = false;
                        } else {
                            if (pendingHighSurrogate) return false;
                        }
                        break;
                    }

                    default:
                        return false;
                }
            }
        }

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
        if (ALLOW_JSON5_NUMBERS && (startChar == '-' || startChar == '+')) {
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

        final VectorSpecies<Byte> S = ByteVector.SPECIES_256;

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

            // classify identifier chars
            VectorMask<Byte> mAZ =
                vec.compare(VectorOperators.GE, (byte)'A')
                   .and(vec.compare(VectorOperators.LE, (byte)'Z'));

            VectorMask<Byte> maz =
                vec.compare(VectorOperators.GE, (byte)'a')
                   .and(vec.compare(VectorOperators.LE, (byte)'z'));

            VectorMask<Byte> m09 =
                vec.compare(VectorOperators.GE, (byte)'0')
                   .and(vec.compare(VectorOperators.LE, (byte)'9'));

            VectorMask<Byte> mus = vec.compare(VectorOperators.EQ, (byte)'_');
            VectorMask<Byte> mdl = vec.compare(VectorOperators.EQ, (byte)'$');

            long maskIdent = (mAZ.or(maz).or(m09).or(mus).or(mdl)).toLong();

            // Only lane 0 matters for the first byte
            if ((maskIdent & 1L) == 0L) {
                return;
            }

            // find first non-identifier lane
            long maskNonIdent = ~maskIdent;

            int firstBad = Long.numberOfTrailingZeros(maskNonIdent);
            if (firstBad < S.length()) {
                bb.advanceBy(firstBad);
                return;
            }

            // all 128 bytes are identifier chars
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
            numberValidator = this::validateNumberBytes;

            // Whitespace / trivia skipping
            if (ALLOW_COMMENTS) {
                tokenHandler = this::nextTokenWithComments;
                triviaHandler = byteBuffer::skipWhitespaceAndFormattingSimd;
            } else {
                tokenHandler = this::nextTokenWithoutComments;
                triviaHandler = byteBuffer::skipWhitespaceSimd;
            }

            if (!ALLOW_UNICODE) {


                // TODO: Make this use SIMD
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
            numberValidator = this::validateNumberLexeme;
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

                // TODO: if lexeme is empty here, it should be an error.
                // Should reaching here always be an error? Probably

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

    private JsonToken makeErrorToken(int line, int column, JsonDiagnosticCode code, Object... details) {
        return new JsonErrorToken(
            line,
            column,
            buffer.windowStartOffset(),
            // JsonTokenType.ERROR,
            // null,
            // message,
            // QuoteStyle.PLAIN
            code, details
        );
    }

    private JsonToken toCommentToken(JsonComment c) {
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
        JsonToken makeNumberToken(int line, int column, int startOffset);
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
        public JsonBufferBackedToken makeNumberToken(int line, int column, int startOffset) {
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
        public JsonLexemeBackedToken makeNumberToken(int line, int column, int startOffset) {
            return new JsonLexemeBackedToken(
                line,
                column,
                startOffset,
                JsonTokenType.NUMBER,
                buffer.getTokenWindowLexeme()
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