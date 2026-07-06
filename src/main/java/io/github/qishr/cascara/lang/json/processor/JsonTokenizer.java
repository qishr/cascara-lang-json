package io.github.qishr.cascara.lang.json.processor;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.util.SourceBuffer;
import io.github.qishr.cascara.common.lang.util.SourceInputStreamBuffer;
import io.github.qishr.cascara.common.lang.util.SourceStringBuffer;
import io.github.qishr.cascara.lang.json.exception.JsonDiagnosticCode;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;

public class JsonTokenizer extends AbstractJsonProcessor<JsonTokenizer> implements Tokenizer<JsonToken>{
    private SourceBuffer buffer;
    private List<JsonToken> tokens;
    private boolean isLegacyMode = false;
    private final Deque<JsonToken> pendingTokens = new ArrayDeque<>();
    private boolean streamEnded = false;

    /// Default constructor for SPI
    public JsonTokenizer() {}

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
            JsonToken eof = new JsonToken(buffer.line(), buffer.column(), buffer.offset(), JsonTokenType.EOF, "", null);
            return queueToken(eof);
        }

        return null;
    }

    private void scanToken() {
        trace("scanToken");
        char c = buffer.advance();
        JsonTokenType type = null;
        String lexeme = String.valueOf(c);
        String value = null;

        switch (c) {
            case '{' -> type = JsonTokenType.LEFT_BRACE;
            case '}' -> type = JsonTokenType.RIGHT_BRACE;
            case '[' -> type = JsonTokenType.LEFT_BRACKET;
            case ']' -> type = JsonTokenType.RIGHT_BRACKET;
            case ',' -> type = JsonTokenType.COMMA;
            case ':' -> type = JsonTokenType.COLON;

            case '"', '\'' -> {
                scanString(c);
                lexeme = buffer.getTokenWindowLexeme();
                value = (lexeme.length() >= 2)
                    ? lexeme.substring(1, lexeme.length() - 1)
                    : "";
                type = JsonTokenType.STRING;
            }

            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+' -> {
                scanNumber(c);
                lexeme = buffer.getTokenWindowLexeme();
                type = JsonTokenType.NUMBER;
            }

            case '.' -> {
                if (isDigit(buffer.peek())) {
                    scanNumber(c);
                    lexeme = buffer.getTokenWindowLexeme();
                    type = JsonTokenType.NUMBER;
                } else {
                    type = JsonTokenType.DOT;
                }
            }

            case 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
                'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '_', '$' -> {

                // 1. Consume the entire alphanumeric/identifier word sequence completely
                while (isIdentifierPart(buffer.peek())) {
                    buffer.advance();
                }

                lexeme = buffer.getTokenWindowLexeme();

                // 2. Classify the fully-extracted token word string
                switch (lexeme) {
                    case "true", "false"   -> type = JsonTokenType.BOOLEAN;
                    case "null"            -> type = JsonTokenType.NULL;
                    case "Infinity", "NaN" -> type = JsonTokenType.NUMBER; // Maps directly to scalar route
                    default                -> type = JsonTokenType.IDENTIFIER;
                }
            }

            default -> {
                if (isIdentifierStart(c)) {
                    scanIdentifier(c);
                    lexeme = buffer.getTokenWindowLexeme();
                    type = JsonTokenType.IDENTIFIER;
                } else {
                    type = JsonTokenType.UNKNOWN;
                }
            }
        }

        if (value == null) value = lexeme;
        addToken(type, lexeme, value);
    }

    private void scanString(char quoteChar) {
        if (quoteChar == '\'' && !options.allowSingleQuotedStrings()) {
            error(JsonDiagnosticCode.UNEXPECTED_TOKEN, quoteChar);
        }
        while (!buffer.isAtEnd()) {
            char next = buffer.advance();
            if (next == quoteChar) return;

            if (next == '\\' && !buffer.isAtEnd()) {
                char escaped = buffer.advance();
                if (escaped == 'u' || escaped == 'x') {
                    int count = (escaped == 'u' ? 4 : 2);
                    for (int i = 0; i < count && !buffer.isAtEnd(); i++) {
                        buffer.advance();
                    }
                }
            }
        }
    }

    private void scanNumber(char startChar) {
        // 1. Handle JSON5 signed literal keywords (+Infinity, -Infinity, -NaN, etc.)
        if (options.allowInfinityAndNaN()) {
            if ((startChar == '-' || startChar == '+') && isIdentifierStart(buffer.peek())) {
                while (isIdentifierPart(buffer.peek())) {
                    buffer.advance();
                }
                return;
            }
        }

        // 2. Handle Hexadecimal (0x...)
        if (options.allowHexadecimalNumbers()) {
            if (startChar == '0' && (buffer.peek() == 'x' || buffer.peek() == 'X')) {
                buffer.advance(); // consume 'x'
                while (isHexDigit(buffer.peek())) {
                    buffer.advance();
                }
                return;
            }
        }

        // 3. Handle Decimal sequence
        while (isDigit(buffer.peek())) {
            buffer.advance();
        }

        // 4. Handle Fraction dot component
        if (buffer.peek() == '.') {
            buffer.advance();
            while (isDigit(buffer.peek())) {
                buffer.advance();
            }
        }

        // 5. Handle Exponent notation
        if (buffer.peek() == 'e' || buffer.peek() == 'E') {
            buffer.advance();
            if (buffer.peek() == '+' || buffer.peek() == '-') {
                buffer.advance();
            }
            while (isDigit(buffer.peek())) {
                buffer.advance();
            }
        }
    }

    private void scanIdentifier(char startChar) {
        trace("scanIdentifier");
        while (!buffer.isAtEnd() && isIdentifierPart(buffer.peek())) {
            buffer.advance();
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
        while (true) {
            char nextC = buffer.peek();
            if (nextC == '\u0000') return;

            if (Character.isWhitespace(nextC)) {
                buffer.advance();
                continue;
            }

            if (nextC == '/' && options.allowComments()) {
                char nextNextC = buffer.peekNext();
                if (nextNextC == '/') {
                    buffer.startTokenWindow(); // Mark comment start
                    String value = scanSingleLineComment();
                    String lexeme = buffer.getTokenWindowLexeme();
                    addToken(JsonTokenType.COMMENT, lexeme, value);
                    continue;
                } else if (nextNextC == '*') {
                    buffer.startTokenWindow(); // Mark comment start
                    String value = scanMultiLineComment();
                    String lexeme = buffer.getTokenWindowLexeme();
                    addToken(JsonTokenType.COMMENT, lexeme, value);
                    continue;
                }
            }
            break;
        }
    }

    //
    //
    //

    private JsonToken addToken(JsonToken token) {
        trace("addToken");
        if (token != null) {
            pendingTokens.add(token); // Queue it up so nextToken() can yield it
        }
        return token;
    }

    private JsonToken addToken(JsonTokenType type, String lexeme, String value) {
        return addToken(new JsonToken(
            buffer.windowStartLine(),
            buffer.windowStartColumn(),
            buffer.windowStartOffset(),
            type, lexeme, value
        ));
    }

    private JsonToken addToken(JsonTokenType type) {
        String text = buffer.getTokenWindowLexeme();
        return addToken(new JsonToken(buffer.windowStartLine(), buffer.windowStartColumn(), buffer.windowStartOffset(), type, text, text));
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

    //
    //
    //

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
               (c >= 'a' && c <= 'f') ||
               (c >= 'A' && c <= 'F');
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '$' || c == '_';
    }

    private boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || Character.isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    //
    // Errors & Diagnostics
    //

    private void error(JsonDiagnosticCode msgCode, Object... details) {
        JsonToken token = addToken(JsonTokenType.ERROR);
        reporter.errorAt(token, msgCode, details);
    }

    private void trace(String method) {
        if (reporter == null) return;
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