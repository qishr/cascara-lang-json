package io.github.qishr.cascara.lang.json.processor;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;

public class JsonTokenizer extends AbstractJsonProcessor<JsonTokenizer> implements Tokenizer<JsonToken>{
    private String source;
    private List<JsonToken> tokens;
    private int current = 0;
    private int currentLine = 1;
    private int currentColumn = 1;
    private char c = 0;

    /// Default constructor for SPI
    public JsonTokenizer() {}

    @Override protected JsonTokenizer self() { return this; }

    @Override
    public List<JsonToken> tokenize(String text) {
        this.source = text;
        this.tokens = new ArrayList<>();

        current = 0;
        currentLine = 1;
        currentColumn = 1;

        scanTokens();
        return tokens;
    }

    private void scanTokens() {
        while (!isAtEnd()) {
            advanceWhitespaceAndComments();

            if (isAtEnd()) break;

            trace("scanTokens");

            int startPosition = current;
            int startLine = currentLine;
            int startColumn = currentColumn;

            c = advance();
            JsonTokenType type = null;
            String lexeme = String.valueOf(c);
            String value = null; // Reset for every token to prevent value bleeding

            switch (c) {
                case '{' -> type = JsonTokenType.LEFT_BRACE;
                case '}' -> type = JsonTokenType.RIGHT_BRACE;
                case '[' -> type = JsonTokenType.LEFT_BRACKET;
                case ']' -> type = JsonTokenType.RIGHT_BRACKET;
                case ',' -> type = JsonTokenType.COMMA;
                case ':' -> type = JsonTokenType.COLON;

                case '"', '\'' -> {
                    lexeme = scanString(c);
                    // Extract inner value (unquoted)
                    value = (lexeme.length() >= 2)
                        ? lexeme.substring(1, lexeme.length() - 1)
                        : "";
                    type = JsonTokenType.STRING;
                }

                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+' -> {
                    // Move the logic into a specialized method that handles the complex JSON5 cases
                    lexeme = scanNumber(c);
                    type = JsonTokenType.NUMBER;
                }

                case '.' -> {
                    if (isDigit(peek())) {
                        lexeme = scanNumber(c); // Start number with '.'
                        type = JsonTokenType.NUMBER;
                    } else {
                        type = JsonTokenType.DOT;
                    }
                }

                case 't', 'f', 'n' -> {
                    lexeme = scanLiteral(c);
                    if ("true".equals(lexeme) || "false".equals(lexeme)) {
                        type = JsonTokenType.BOOLEAN;
                    } else if ("null".equals(lexeme)) {
                        type = JsonTokenType.NULL;
                    } else {
                        type = JsonTokenType.ERROR;
                    }
                }

                default -> {
                    if (isIdentifierStart(c)) {
                        lexeme = scanIdentifier(c);
                        type = JsonTokenType.IDENTIFIER;
                    } else {
                        type = JsonTokenType.UNKNOWN;
                    }
                }
            }

            // If value wasn't explicitly set (like in strings), use lexeme
            if (value == null) value = lexeme;

            addToken(type, lexeme, value, startPosition, startLine, startColumn);
        }

        addToken(JsonTokenType.EOF, "", "", current, currentLine, currentColumn);
    }

    private void addToken(JsonTokenType type, String lexeme, String value, int pos, int line, int col) {
        tokens.add(new JsonToken(type, lexeme, value, pos, line, col));
    }

    // --- Core Character Consumption ---

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char advance() {
        char c = this.source.charAt(this.current++);
        if (c == '\n') {
            currentLine++;
            currentColumn = 1;
        } else if (c == '\r') {
            currentLine++;
            currentColumn = 1;
            if (!isAtEnd() && source.charAt(current) == '\n') {
                 current++;
            }
        } else {
            currentColumn++;
        }
        return c;
    }

    private char peek() {
        return this.isAtEnd() ? '\u0000' : this.source.charAt(this.current);
    }

    private char peekNext() {
        int nextIndex = this.current + 1;
        return nextIndex >= this.source.length() ? '\u0000' : this.source.charAt(nextIndex);
    }

    // --- Comment/Whitespace Logic ---

    private void advanceWhitespaceAndComments() {
        while (true) {
            char nextC = peek();
            if (nextC == '\u0000') return;

            if (Character.isWhitespace(nextC)) {
                advance();
                continue;
            }

            if (nextC == '/') {
                char nextNextC = peekNext();
                int startPos = current;
                int startL = currentLine;
                int startC = currentColumn;

                if (nextNextC == '/') {
                    String value = scanSingleLineComment();
                    String lexeme = source.substring(startPos, current);
                    addToken(JsonTokenType.COMMENT, lexeme, value, startPos, startL, startC);
                    continue;
                } else if (nextNextC == '*') {
                    String value = scanMultiLineComment();
                    String lexeme = source.substring(startPos, current);
                    addToken(JsonTokenType.COMMENT, lexeme, value, startPos, startL, startC);
                    continue;
                }
            }
            break;
        }
    }

    private String scanSingleLineComment() {
        int startOffset = current; // Start after //
        advance(); // /
        advance(); // /

        // The 'value' starts after the two slashes
        int valueStart = current;

        while (!isAtEnd() && peek() != '\n' && peek() != '\r') {
            advance();
        }

        // Return the "clean" content
        return source.substring(valueStart, current);
    }

    private String scanMultiLineComment() {
        advance(); // /
        advance(); // *
        int valueStart = current;

        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                String value = source.substring(valueStart, current);
                advance(); // *
                advance(); // /
                return value;
            }
            advance();
        }
        return source.substring(valueStart, current); // Unterminated case
    }

    // --- Literals and Identifiers ---

    private String scanString(char quoteChar) {
        StringBuilder sb = new StringBuilder(String.valueOf(quoteChar));
        while (!isAtEnd()) {
            char peeked = peek();
            if (peeked == '\n' || peeked == '\r') break;

            char next = advance();
            sb.append(next);
            if (next == quoteChar) return sb.toString();

            if (next == '\\' && !isAtEnd()) {
                char escaped = advance();
                sb.append(escaped);
                if (escaped == 'u' || escaped == 'x') {
                    int count = (escaped == 'u' ? 4 : 2);
                    for (int i = 0; i < count && !isAtEnd(); i++) {
                        sb.append(advance());
                    }
                }
            }
        }
        return sb.toString();
    }

    private String scanIdentifier(char startChar) {
        StringBuilder sb = new StringBuilder(String.valueOf(startChar));
        while (!isAtEnd() && isIdentifierPart(peek())) {
            sb.append(advance());
        }
        return sb.toString();
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

    private String scanNumber(char startChar) {
        // 'start' was red because in this context we need the 'startPosition'
        // recorded at the beginning of scanTokens
        int startPos = current - 1;

        // 1. Handle Hexadecimal (0x...) - only if start was '0'
        if (startChar == '0' && (peek() == 'x' || peek() == 'X')) {
            advance(); // x
            while (isHexDigit(peek())) advance();
            return source.substring(startPos, current);
        }

        // 2. Handle Decimal Part
        while (isDigit(peek())) advance();

        // 3. Handle Fraction Part (.5 or 1.)
        if (peek() == '.') {
            advance(); // .
            while (isDigit(peek())) advance();
        }

        // 4. Handle Exponent Part (1e10)
        if (peek() == 'e' || peek() == 'E') {
            advance();
            if (peek() == '+' || peek() == '-') advance();
            while (isDigit(peek())) advance();
        }

        return source.substring(startPos, current);
    }

    // Helper to check the character we JUST advanced over
    private char previousChar() {
        if (current == 0) return '\0';
        return source.charAt(current - 1);
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
               (c >= 'a' && c <= 'f') ||
               (c >= 'A' && c <= 'F');
    }

    private String scanLiteral(char startChar) {
        StringBuilder sb = new StringBuilder(String.valueOf(startChar));
        String expected = switch (startChar) {
            case 't' -> "true";
            case 'f' -> "false";
            case 'n' -> "null";
            default -> "";
        };
        for (int i = 1; i < expected.length(); i++) {
            if (isAtEnd() || peek() != expected.charAt(i)) break;
            sb.append(advance());
        }
        return sb.toString();
    }

    private void trace(String method) {
        if (reporter == null) return;
        reporter.trace("C=%03d '%s' %03d:%03d %s", current, currentChar(c), currentLine, currentColumn, method);
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