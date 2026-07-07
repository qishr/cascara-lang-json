package io.github.qishr.cascara.lang.json.processor;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;

public class JsonTokenizer extends AbstractJsonProcessor<JsonTokenizer> implements Tokenizer<JsonToken>{
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
    private boolean ALLOW_SINGLE_QUOTED_STRINGS;
    private boolean ALLOW_UNICODE;
    private boolean CAPTURE_COMMENTS;

    private final Deque<JsonToken> pendingTokens = new ArrayDeque<>();

    private SourceBuffer buffer;
    private List<JsonToken> tokens;
    private boolean isLegacyMode = false;
    private boolean streamEnded = false;

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

        // TODO: this preallocation kills performance for small files
        // this.tokens = new ArrayList<>(4096);
        this.tokens = new ArrayList<>();
        this.streamEnded = false;

        open(source);
        this.isLegacyMode = true;

        // Drain the stream using the sequential nextToken logic
        JsonToken token;
        while ((token = nextToken()) != null) {
            if (token.getType() == JsonTokenType.EOF) {
                break;
            }
        }
        return this.tokens;
    }

    @Override
    public Set<JsonTokenType> getTokenTypes() {
        return EnumSet.allOf(JsonTokenType.class);
    }

    @Override
    public JsonToken nextToken() {
        if (!pendingTokens.isEmpty()) {
            return queueToken(pendingTokens.pollFirst());
        }

        if (streamEnded) {
            return null;
        }

        // 1. Loop until we either parse a token or drain the stream buffer
        while (!buffer.isAtEnd() && pendingTokens.isEmpty()) {
            advanceWhitespaceAndComments();

            if (buffer.isAtEnd()) break;

            buffer.startTokenWindow();
            scanToken();
        }

        // 2. If scanning populated tokens, return the first one
        if (!pendingTokens.isEmpty()) {
            return queueToken(pendingTokens.pollFirst());
        }

        // 3. Handle standard EOF termination without stream lifecycle markers
        if (buffer.isAtEnd()) {
            streamEnded = true;
            JsonToken eof = new JsonToken(buffer.line(), buffer.column(), buffer.offset(), JsonTokenType.EOF, "", null, QuoteStyle.PLAIN);
            return queueToken(eof);
        }

        return null;
    }

    private void scanToken() {
        // I doubt this makes any difference TBH.
        // trace("scanToken");

        char c = buffer.advance();

        JsonTokenType type = null;
        String lexeme = null;
        String value = null;
        QuoteStyle quoteStyle = QuoteStyle.PLAIN;

        if (c > 127) {
            if (!ALLOW_UNICODE) {
                // Raw unicode not allowed in strict mode
                type = JsonTokenType.UNKNOWN;
                lexeme = Character.toString(c);
                value  = lexeme;
                addToken(type, lexeme, value, QuoteStyle.PLAIN);
                return;
            }

            // Unicode allowed → treat as identifier start or UNKNOWN
            handleUnicodeChar(c);
            return;
        }

        switch (c) {

            //
            // FAST‑PATH STRUCTURAL TOKENS (zero allocation)
            //
            case '{' -> {
                addToken(JsonTokenType.LEFT_BRACE, LBRACE, LBRACE, QuoteStyle.PLAIN);
                return;
            }
            case '}' -> {
                addToken(JsonTokenType.RIGHT_BRACE, RBRACE, RBRACE, QuoteStyle.PLAIN);
                return;
            }
            case '[' -> {
                addToken(JsonTokenType.LEFT_BRACKET, LBRACKET, LBRACKET, QuoteStyle.PLAIN);
                return;
            }
            case ']' -> {
                addToken(JsonTokenType.RIGHT_BRACKET, RBRACKET, RBRACKET, QuoteStyle.PLAIN);
                return;
            }
            case ',' -> {
                addToken(JsonTokenType.COMMA, COMMA, COMMA, QuoteStyle.PLAIN);
                return;
            }
            case ':' -> {
                addToken(JsonTokenType.COLON, COLON, COLON, QuoteStyle.PLAIN);
                return;
            }

            //
            // STRING
            //
            case '"', '\'' -> {
                // If single-quoted strings are disallowed, emit UNKNOWN and return.
                if (c == '\'') {
                    if (!ALLOW_SINGLE_QUOTED_STRINGS) {
                        // Consume the string anyway so offsets remain correct
                        scanString(c);
                        lexeme = buffer.getTokenWindowLexeme();

                        type = JsonTokenType.UNKNOWN;
                        value = lexeme; // preserve raw content for diagnostics
                        break;
                    }
                    quoteStyle = QuoteStyle.SINGLE;
                } else {
                    quoteStyle = QuoteStyle.DOUBLE;
                }

                if (!scanString(c)) {
                    type = JsonTokenType.UNKNOWN;
                    break;
                }

                lexeme = buffer.getTokenWindowLexeme();

                // Keep this: parser expects unquoted content
                value = (lexeme.length() >= 2)
                        ? lexeme.substring(1, lexeme.length() - 1)
                        : "";

                type = JsonTokenType.STRING;
            }

            //
            // NUMBER (including JSON5 +Infinity, -Infinity, NaN)
            //
            case '0','1','2','3','4','5','6','7','8','9','-','+' -> {
                scanNumber(c);
                lexeme = buffer.getTokenWindowLexeme();
                type = JsonTokenType.NUMBER;
            }

            //
            // DOT (either number or standalone)
            //
            case '.' -> {
                if (isDigit(buffer.peek())) {
                    scanNumber(c);
                    lexeme = buffer.getTokenWindowLexeme();
                    type = JsonTokenType.NUMBER;
                } else {
                    type = JsonTokenType.DOT;
                    lexeme = ".";
                    value  = ".";
                }
            }

            //
            // IDENTIFIER FAST‑PATH (letters, $, _)
            //
            case 'a','b','c','d','e','f','g','h','i','j','k','l','m',
                'n','o','p','q','r','s','t','u','v','w','x','y','z',
                'A','B','C','D','E','F','G','H','I','J','K','L','M',
                'N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
                '_','$' -> {

                scanIdentifierFast();
                lexeme = buffer.getTokenWindowLexeme();

                switch (lexeme) {
                    case "true", "false"   -> {
                        type  = JsonTokenType.BOOLEAN;
                        value = lexeme;
                    }
                    case "null"            -> {
                        type  = JsonTokenType.NULL;
                        value = lexeme;
                    }
                    case "Infinity", "NaN" -> {
                        type  = JsonTokenType.NUMBER;
                        value = lexeme;
                    }
                    default                -> {
                        type  = JsonTokenType.IDENTIFIER;
                        value = lexeme;
                    }
                }
            }

            //
            // UNKNOWN
            //
            default -> {
                type = JsonTokenType.UNKNOWN;
                lexeme = String.valueOf(c);
                value  = lexeme;
            }
        }

        //
        // FALLBACKS
        //
        if (lexeme == null) {
            lexeme = buffer.getTokenWindowLexeme();
        }
        if (value == null) {
            value = lexeme;
        }

        addToken(type, lexeme, value, quoteStyle);
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

        // 1. JSON5 signed Infinity/NaN
        if (ALLOW_INFINITY_AND_NAN && (startChar == '-' || startChar == '+')) {
            char c = buffer.peek();
            if (c < 128 && IDENT_START[c]) {
                // Consume identifier characters
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

                // Consume hex digits
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
            buffer.advance(); // consume '.'

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
            buffer.advance(); // consume e/E

            c = buffer.peek();
            if (c == '+' || c == '-') {
                buffer.advance(); // consume sign
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
        trace("scanSingleLineComment");
        buffer.advance(); // `/`
        buffer.advance(); // `/`

        // Use a window or lookahead to capture the text inside
        StringBuilder valueBuilder = new StringBuilder();
        while (!buffer.isAtEnd() && buffer.peek() != '\n' && buffer.peek() != '\r') {
            valueBuilder.append(buffer.advance());
        }
        return valueBuilder.toString();
    }

    private String scanMultiLineComment() {
        trace("scanMultiLineComment");
        buffer.advance(); // `/`
        buffer.advance(); // `*`

        StringBuilder valueBuilder = new StringBuilder();
        while (!buffer.isAtEnd()) {
            if (buffer.peek() == '*' && buffer.peekNext() == '/') {
                buffer.advance(); // `*`
                buffer.advance(); // `/`
                break;
            }
            valueBuilder.append(buffer.advance());
        }
        return valueBuilder.toString();
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
                    String value = scanSingleLineComment();
                    if (capture) {
                        String lexeme = buffer.getTokenWindowLexeme();
                        addToken(JsonTokenType.COMMENT, lexeme, value, QuoteStyle.PLAIN);
                    }
                    continue;
                }

                // Multi-line comment
                if (n == '*') {
                    if (capture) buffer.startTokenWindow();
                    String value = scanMultiLineComment();
                    if (capture) {
                        String lexeme = buffer.getTokenWindowLexeme();
                        addToken(JsonTokenType.COMMENT, lexeme, value, QuoteStyle.PLAIN);
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

    private void handleUnicodeChar(char c) {
        // Unicode whitespace (JSON5)
        if (Character.isWhitespace(c)) {
            // skip it
            return;
        }

        // Unicode identifier start (JSON5)
        if (Character.isUnicodeIdentifierStart(c)) {
            scanUnicodeIdentifier();
            String lexeme = buffer.getTokenWindowLexeme();
            addToken(JsonTokenType.IDENTIFIER, lexeme, lexeme, QuoteStyle.PLAIN);
            return;
        }

        // Unicode identifier part (rare case)
        if (Character.isUnicodeIdentifierPart(c)) {
            scanUnicodeIdentifier();
            String lexeme = buffer.getTokenWindowLexeme();
            addToken(JsonTokenType.IDENTIFIER, lexeme, lexeme, QuoteStyle.PLAIN);
            return;
        }

        // Unicode digit (JSON5)
        if (Character.isDigit(c)) {
            scanUnicodeNumber(c);
            String lexeme = buffer.getTokenWindowLexeme();
            addToken(JsonTokenType.NUMBER, lexeme, lexeme, QuoteStyle.PLAIN);
            return;
        }

        // Otherwise → UNKNOWN
        addToken(JsonTokenType.UNKNOWN, Character.toString(c), Character.toString(c), QuoteStyle.PLAIN);
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

    private void addToken(JsonTokenType type, String lexeme, String content, QuoteStyle quoteStyle) {
        JsonToken token;
        if (buffer instanceof LexemeProvider lp) {
            token = new JsonBufferBackedToken(
                lp,
                buffer.windowStartOffset(),
                buffer.offset(),
                buffer.windowStartLine(),
                buffer.windowStartColumn(),
                type, content, quoteStyle
            );
        } else {
            token = new JsonToken(
                buffer.windowStartLine(),
                buffer.windowStartColumn(),
                buffer.windowStartOffset(),
                type, lexeme, content, quoteStyle
            );
        }
        pendingTokens.add(token); // Queue it up so nextToken() can yield it
    }

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
}