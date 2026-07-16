// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.json.processor;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.util.SourceBuffer;
import io.github.qishr.cascara.common.lang.util.SourceInputStreamBuffer;
import io.github.qishr.cascara.common.lang.util.SourceStringBuffer;
import io.github.qishr.cascara.lang.json.exception.JsonDiagnosticCode;
import io.github.qishr.cascara.lang.json.token.JsonBufferBackedToken;
import io.github.qishr.cascara.lang.json.token.JsonComment;
import io.github.qishr.cascara.lang.json.token.JsonErrorToken;
import io.github.qishr.cascara.lang.json.token.JsonLiteral;
import io.github.qishr.cascara.lang.json.token.JsonNumberToken;
import io.github.qishr.cascara.lang.json.token.JsonStructuralToken;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonLexemeBackedToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;
import io.github.qishr.cascara.lang.json.token.ScannedNumber;
import io.github.qishr.cascara.lang.json.token.JsonSourceByteBuffer;
import io.github.qishr.cascara.lang.json.util.JsonOptions;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class JsonTokenizer extends AbstractJsonProcessor<JsonTokenizer> implements Tokenizer<JsonToken>{

    private static final JsonToken LBRACE  = new JsonStructuralToken(JsonTokenType.LEFT_BRACE);
    private static final JsonToken RBRACE  = new JsonStructuralToken(JsonTokenType.RIGHT_BRACE);
    private static final JsonToken LBRACKET = new JsonStructuralToken(JsonTokenType.LEFT_BRACKET);
    private static final JsonToken RBRACKET = new JsonStructuralToken(JsonTokenType.RIGHT_BRACKET);
    private static final JsonToken COLON   = new JsonStructuralToken(JsonTokenType.COLON);
    private static final JsonToken COMMA   = new JsonStructuralToken(JsonTokenType.COMMA);

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

    private static final int[] HEX_VALUE = new int[128];

    static {
        for (char c = '0'; c <= '9'; c++) HEX_VALUE[c] = c - '0';
        for (char c = 'a'; c <= 'f'; c++) HEX_VALUE[c] = 10 + (c - 'a');
        for (char c = 'A'; c <= 'F'; c++) HEX_VALUE[c] = 10 + (c - 'A');
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
    private boolean USE_SIMD;

    private SourceBuffer buffer;

    private List<JsonToken> tokens;
    private final List<JsonComment> pendingComments = new ArrayList<>();
    private TokenFactory factory;

    private boolean usingByteBuffer;

    /// Default constructor for SPI
    public JsonTokenizer() {
        // SPI will call this
        // Default is strict JSON
        applyOptions(new JsonOptions());
    }

    public int getLine() {
        return buffer.line();
    }

    public int getColumn() {
        return buffer.column();
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
        this.USE_SIMD            = options.useSimd();
    }

    @Override protected JsonTokenizer self() { return this; }

    @Override
    public void open(String text) {
        buffer = setupStringBuffer(text);
        factory = setupTokenFactory(buffer);
        skipBom();
    }

    public void open(byte[] data) {
        // Strict UTF‑8 validation
        if (options.validateUnicode()) {
            validateUtf8(data);
        }
        buffer = setupByteBuffer(data);
        factory = setupTokenFactory(buffer);
        skipBom();
    }

    @Override
    public void open(Reader reader) {
        buffer = new SourceInputStreamBuffer(reader);
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
        tokens.add(makeEofToken());
        return this.tokens;
    }

    @Override
    public Set<JsonTokenType> getTokenTypes() {
        return EnumSet.allOf(JsonTokenType.class);
    }

    public JsonToken nextToken() {
        return ALLOW_COMMENTS ? nextTokenWithComments() : nextTokenWithoutComments();
    }

    private JsonToken nextTokenWithoutComments() {
        if (buffer instanceof JsonSourceByteBuffer byteBuffer) {
            byteBuffer.skipWhitespaceSimd();
            if (!ALLOW_UNICODE) {
                return buffer.isAtEnd() ? makeEofToken() : scanTokenByte(byteBuffer.peekByte());
            } else {
                return buffer.isAtEnd() ? makeEofToken() : scanTokenAsciiOrUnicode(buffer.peek());
            }
        } else {
            scanWhitespaceAndComments();
            return buffer.isAtEnd() ? makeEofToken() : scanTokenAsciiOrUnicode(buffer.peek());
        }
    }

    private JsonToken nextTokenWithComments() {
        if (!pendingComments.isEmpty()) {
            return toCommentToken(pendingComments.remove(0));
        }

        if (buffer instanceof JsonSourceByteBuffer byteBuffer) {
            byteBuffer.skipWhitespaceAndFormattingSimd();
        } else {
            scanWhitespaceAndComments();
        }

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

        final JsonToken tok;
        if (ALLOW_UNICODE) {
            tok = scanTokenAsciiOrUnicode(buffer.peek());
        } else {
            tok = scanTokenAscii(buffer.peek());
        }

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

    private JsonToken scanTokenByte(byte b) {
        switch (b) {
            case '{': return makeStructuralToken(JsonTokenType.LEFT_BRACE);
            case '}': return makeStructuralToken(JsonTokenType.RIGHT_BRACE);
            case '[': return makeStructuralToken(JsonTokenType.LEFT_BRACKET);
            case ']': return makeStructuralToken(JsonTokenType.RIGHT_BRACKET);
            case ':': return makeStructuralToken(JsonTokenType.COLON);
            case ',': return makeStructuralToken(JsonTokenType.COMMA);

            case '"':
            case '\'':
                return scanStringTokenByte(b);

            default:
                return scanNumberOrIdentifierOrErrorByte(b);
        }
    }

    private JsonToken scanTokenUnicode(char c) {
        buffer.startTokenWindow();
        final int startOffset = buffer.windowStartOffset();
        final int line        = buffer.windowStartLine();
        final int column      = buffer.windowStartColumn();

        // Are we really sure we need to check validateUnicode in this path which is only taken when useUnicode is true?
        if (!options.validateUnicode()) {
            // Unicode path disabled → treat all non‑ASCII as error
            buffer.advance();
            return makeErrorToken(line, column, JsonDiagnosticCode.UNEXPECTED_CHARACTER, c);
        }

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
            ScannedNumber number = scanNumberUnicode(c);
            return factory.makeNumberToken(line, column, startOffset, number);
        }

        // Otherwise - UNKNOWN
        buffer.advance();
        return makeErrorToken(line, column, JsonDiagnosticCode.UNEXPECTED_CHARACTER, c);
    }

    //
    // Strings
    //

    private JsonToken scanStringTokenByte(byte quoteByte) {
        final JsonSourceByteBuffer buffer = (JsonSourceByteBuffer) this.buffer;

        final int startOffset = buffer.offset();
        final int startLine   = buffer.line();
        final int startColumn = buffer.column();

        buffer.startTokenWindow();
        buffer.advanceByte(); // consume opening quote

        final boolean ok;
        if (USE_SIMD) {
            ok = scanStringSimd((char) quoteByte); // existing SIMD path
        } else {
            ok = scanString((char) quoteByte);     // existing scalar path
        }

        final QuoteStyle qs = (quoteByte == '"')
            ? QuoteStyle.DOUBLE
            : QuoteStyle.SINGLE;

        if (qs == QuoteStyle.SINGLE && !options.allowSingleQuotedStrings()) {
            return makeErrorToken(startLine, startColumn,
                                  JsonDiagnosticCode.NOT_ALLOWED_SINGLE_QUOTED_STRING);
        }

        if (!ok) {
            return makeErrorToken(startLine, startColumn,
                                  JsonDiagnosticCode.UNTERMINATED_STRING);
        }

        return factory.makeStringToken(startLine, startColumn, startOffset, qs);
    }

    private JsonToken scanStringToken(char quoteChar) {
        final int startOffset = buffer.offset(); // actual position of the quote
        final int startLine   = buffer.line();
        final int startColumn = buffer.column();

        buffer.startTokenWindow();
        buffer.advance(); // consume opening quote

        // Call either scanString or scanStringSimd
        final boolean ok;
        if (USE_SIMD) {
            ok = scanStringSimd(quoteChar);
        } else {
            ok = scanString(quoteChar);
        }

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
                        if (!options.validateUnicode()) {
                            // Fast path: skip surrogate correctness entirely
                            for (int i = 0; i < 4; i++) {
                                char h = buffer.advance();
                                if (!HEX[h]) return false;
                            }
                            break;
                        }
                        int codeUnit = 0;
                        for (int i = 0; i < 4; i++) {
                            if (buffer.isAtEnd()) return false;
                            char h = buffer.advance();
                            if (!HEX[h]) return false;
                            int v = (h <= '9')
                                ? (h - '0')
                                : 10 + ((h & 0xDF) - 'A');
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

    // TODO: Why is a SIMD method taking char instead of byte?
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
                buffer.advance();
                return !pendingHighSurrogate;
            }

            if (c == '\\') {
                buffer.advance(); // consume '\'
                if (bb.isAtEnd()) {
                    return false;
                }

                final char esc = buffer.advance(); // escaped char

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
                        if (!options.validateUnicode()) {
                            // Fast path: skip surrogate correctness entirely
                            for (int i = 0; i < 4; i++) {
                                char h = buffer.advance();
                                if (!HEX[h]) return false;
                            }
                            break;
                        }
                        int codeUnit = 0;
                        for (int i = 0; i < 4; i++) {
                            if (bb.isAtEnd()) return false;
                            char h = buffer.advance();
                            if (!HEX[h]) return false;
                            int v = (h <= '9')
                                ? (h - '0')
                                : 10 + ((h & 0xDF) - 'A');
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
            buffer.advance();
            pos = bb.offset();
        }

        // EOF before closing quote
        return false;
    }

    //
    // Numbers and Identifiers
    //

    private JsonToken scanNumberOrIdentifierOrErrorByte(byte b) {
        final int startOffset = buffer.offset();
        final int startLine   = buffer.line();
        final int startColumn = buffer.column();

        buffer.startTokenWindow();

        // JSON5 Infinity / NaN
        if (ALLOW_JSON5_NUMBERS) {
            if (startsWithInfinityOrNaNByte(b)) {
                buffer.startTokenWindow();
                ScannedNumber number = scanJson5KeywordNumberByte(b);
                return factory.makeNumberToken(startLine, startColumn, startOffset, number);
            }
        }

        // ----- NUMBER? -----
        if (b < 128 && NUM_START[b]) {
            // Use the existing numberScanner, but feed it a char
            ScannedNumber number = scanNumberByte(b);

            int end = buffer.offset();
            if (!validateNumberBytes(startOffset, end)) {
                return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.INVALID_NUMBER);
            }

            return factory.makeNumberToken(startLine, startColumn, startOffset, number);
        }

        // ----- IDENTIFIER? -----
        if (b < 128 && IDENT_START[b]) {
            if (ALLOW_UNICODE) {
                scanIdentifierUnicode();
            } else {
                scanIdentifierSimd();
            }
            String lexeme = buffer.getTokenWindowLexeme();
            return classifyLexeme(startOffset, startLine, startColumn, lexeme);
        }

        // ----- ERROR -----
        buffer.advance(); // same as char path
        return makeErrorToken(
            startLine,
            startColumn,
            JsonDiagnosticCode.UNEXPECTED_CHARACTER,
            (char)b
        );
    }

    private JsonToken scanNumberOrIdentifierOrError(char startChar) {
        final int startOffset = buffer.offset();
        final int startLine   = buffer.line();
        final int startColumn = buffer.column();

        buffer.startTokenWindow();

        // JSON5 Infinity / NaN
        if (ALLOW_JSON5_NUMBERS) {
            if (startsWithInfinityOrNaN(startChar)) {
                buffer.startTokenWindow();
                ScannedNumber number = scanJson5KeywordNumber(startChar);
                return factory.makeNumberToken(startLine, startColumn, startOffset, number);
            }
        }

        // NUMBERS?
        if (NUM_START[startChar]) {
            int start = buffer.offset();
            ScannedNumber number = scanNumberAscii(startChar);
            int end = buffer.offset();
            if (!validateNumberLexeme(start, end)) {
                return makeErrorToken(startLine, startColumn, JsonDiagnosticCode.INVALID_NUMBER);
            }
            return factory.makeNumberToken(startLine, startColumn, startOffset, number);
        }

        // IDENTIFIER?
        if (startChar < 128 && IDENT_START[startChar]) {
            if (ALLOW_UNICODE) {
                scanIdentifierUnicode();
            } else {
                if (usingByteBuffer) {
                    scanIdentifierSimd();
                } else {
                    scanIdentifierAscii();
                }
            }
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

    //
    // Numbers
    //

    private ScannedNumber scanNumberByte(byte first) {
        JsonSourceByteBuffer buffer = (JsonSourceByteBuffer)this.buffer;

        final JsonSourceByteBuffer bb = (JsonSourceByteBuffer) buffer;
        final byte[] raw = bb.raw;

        int start = buffer.offset();
        int i = start;

        boolean negative = false;
        boolean isInteger = true;
        boolean isHex = false;

        // Leading sign
        if (first == '-' || first == '+') {
            negative = (first == '-');
            buffer.advanceByte();
            i++;
            first = raw[i];
        }

        // ------------------------------------------------------------
        // JSON5 Hexadecimal 0x...
        // ------------------------------------------------------------
        if (ALLOW_HEXADECIMAL_NUMBERS &&
            first == '0' &&
            i + 1 < raw.length &&
            (raw[i + 1] == 'x' || raw[i + 1] == 'X')) {

            isHex = true;
            isInteger = true;

            buffer.advanceByte(); // consume '0'
            buffer.advanceByte(); // consume 'x' or 'X'
            i += 2;

            long hex = 0;
            while (i < raw.length) {
                byte b = raw[i];
                if (b < 128 && HEX[b]) {
                    hex = (hex << 4) | HEX_VALUE[b];
                    buffer.advanceByte();
                    i++;
                    continue;
                }
                break;
            }

            double val = negative ? -hex : hex;
            return new ScannedNumber(val, true, true);
        }

        // ------------------------------------------------------------
        // Integer part
        // ------------------------------------------------------------
        long intPart = 0;
        int limit = raw.length;
        // SIMD fast path
        while (buffer.isEightDigitsSIMD(raw, i, limit)) {
            int chunk = buffer.parseEightDigitsSIMD(raw, i);
            intPart = intPart * 100000000L + chunk;

            // advance 8 bytes
            for (int k = 0; k < 8; k++) buffer.advanceByte();
            i += 8;
        }

        // scalar tail
        while (i < limit) {
            byte b = raw[i];
            if (b < '0' || b > '9') break;
            intPart = intPart * 10 + (b - '0');
            buffer.advanceByte();
            i++;
        }

        // TODO: Use the above pattern for the fractional part

        // ------------------------------------------------------------
        // Fractional part
        // ------------------------------------------------------------
        double fracPart = 0.0;
        double divisor = 1.0;
        if (i < raw.length && raw[i] == '.') {
            isInteger = false;
            buffer.advanceByte();
            i++;

            while (i < raw.length) {
                byte b = raw[i];
                if (b >= '0' && b <= '9') {
                    fracPart = fracPart * 10 + (b - '0');
                    divisor *= 10;
                    buffer.advanceByte();
                    i++;
                    continue;
                }
                break;
            }
        }

        // ------------------------------------------------------------
        // Exponent part
        // ------------------------------------------------------------
        int exp = 0;
        boolean expNeg = false;
        if (i < raw.length && (raw[i] == 'e' || raw[i] == 'E')) {
            isInteger = false;
            buffer.advanceByte();
            i++;

            if (i < raw.length && raw[i] == '-') {
                expNeg = true;
                buffer.advanceByte();
                i++;
            } else if (i < raw.length && raw[i] == '+') {
                buffer.advanceByte();
                i++;
            }

            while (i < raw.length) {
                byte b = raw[i];
                if (b >= '0' && b <= '9') {
                    exp = exp * 10 + (b - '0');
                    buffer.advanceByte();
                    i++;
                    continue;
                }
                break;
            }
        }

        double result = (double) intPart + (fracPart / divisor);
        if (exp != 0) {
            result *= Math.pow(10, expNeg ? -exp : exp);
        }
        if (negative) result = -result;

        return new ScannedNumber(result, isInteger, isHex);
    }

    private ScannedNumber scanNumberAscii(char first) {
        int start = buffer.windowStartOffset();
        int i = start;

        boolean negative = false;
        boolean isInteger = true;
        boolean isHex = false;

        // Leading sign (+ or -)
        if (first == '-' || first == '+') {
            negative = (first == '-');
            i++; // skip sign
            if (i >= buffer.length()) {
                buffer.advance();
                return new ScannedNumber(Double.NaN, false, false);
            }
            first = buffer.charAt(i);
        }

        // JSON5 Hexadecimal 0x...
        if (ALLOW_HEXADECIMAL_NUMBERS && first == '0') {
            int j = i + 1;
            if (j < buffer.length()) {
                char c = buffer.charAt(j);
                if (c == 'x' || c == 'X') {
                    isHex = true;
                    isInteger = true;

                    i = j + 1; // skip "0x"
                    long hex = 0;

                    while (i < buffer.length()) {
                        c = buffer.charAt(i);
                        if (c < 128 && HEX[c]) {
                            hex = (hex << 4) | HEX_VALUE[c];
                            i++;
                            continue;
                        }
                        break;
                    }

                    int consumed = i - start;
                    if (consumed <= 0) {
                        buffer.advance();
                        return new ScannedNumber(Double.NaN, false, false);
                    }

                    for (int k = 0; k < consumed; k++) buffer.advance();
                    return new ScannedNumber(negative ? -hex : hex, true, true);
                }
            }
        }

        // Integer part
        long intPart = 0;
        while (i < buffer.length()) {
            char c = buffer.charAt(i);
            if (c < 128 && DIGIT[c]) {
                intPart = intPart * 10 + (c - '0');
                i++;
                continue;
            }
            break;
        }

        // Fractional part (handles leading '.' like +.5)
        double fracPart = 0.0;
        double divisor = 1.0;
        if (i < buffer.length() && buffer.charAt(i) == '.') {
            isInteger = false;
            i++;
            while (i < buffer.length()) {
                char c = buffer.charAt(i);
                if (c < 128 && DIGIT[c]) {
                    fracPart = fracPart * 10 + (c - '0');
                    divisor *= 10;
                    i++;
                    continue;
                }
                break;
            }
        }

        // Exponent part
        int exp = 0;
        boolean expNeg = false;
        if (i < buffer.length()) {
            char c = buffer.charAt(i);
            if (c == 'e' || c == 'E') {
                isInteger = false;

                i++;
                if (i < buffer.length()) {
                    c = buffer.charAt(i);
                    if (c == '-') {
                        expNeg = true;
                        i++;
                    } else if (c == '+') {
                        i++;
                    }
                }

                while (i < buffer.length()) {
                    c = buffer.charAt(i);
                    if (c < 128 && DIGIT[c]) {
                        exp = exp * 10 + (c - '0');
                        i++;
                        continue;
                    }
                    break;
                }
            }
        }

        double result = (double) intPart + (fracPart / divisor);
        if (exp != 0) {
            result = result * Math.pow(10, expNeg ? -exp : exp);
        }
        if (negative) result = -result;

        int consumed = i - start;
        if (consumed <= 0) {
            buffer.advance();
            return new ScannedNumber(Double.NaN, false, false);
        }

        for (int k = 0; k < consumed; k++) buffer.advance();

        return new ScannedNumber(result, isInteger, isHex);
    }

    private ScannedNumber scanNumberUnicode(char startChar) {
        boolean isInteger = true;
        boolean isHex = false;

        if (startChar == '-' || startChar == '+') {
            buffer.advance();
            startChar = buffer.peek();
        }

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
            isInteger = false;
            buffer.advance();
            while (!buffer.isAtEnd() && Character.isDigit(buffer.peek())) {
                buffer.advance();
            }
        }

        // exponent part
        char c = buffer.peek();
        if (c == 'e' || c == 'E') {
            isInteger = false;
            buffer.advance();
            c = buffer.peek();
            if (c == '+' || c == '-') buffer.advance();
            while (!buffer.isAtEnd() && Character.isDigit(buffer.peek())) {
                buffer.advance();
            }
        }

        // Unicode scanner does not compute numeric value — caller handles it
        return new ScannedNumber(Double.NaN, isInteger, isHex);
    }

    private ScannedNumber scanJson5KeywordNumberByte(byte first) {
        JsonSourceByteBuffer bb = (JsonSourceByteBuffer) buffer;
        byte[] raw = bb.raw;
        int off = bb.offset();

        boolean negative = false;

        // Leading sign
        if (first == '-' || first == '+') {
            negative = (first == '-');
            bb.advanceByte();
            off++;
            first = raw[off];
        }

        // Infinity
        if (first == 'I') {
            bb.advanceBy(INFINITY.length());
            return new ScannedNumber(
                negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY,
                false,
                false
            );
        }

        // NaN
        if (first == 'N') {
            bb.advanceBy(NAN.length());
            return new ScannedNumber(Double.NaN, false, false);
        }

        // Should never happen if caller checks startsWithInfinityOrNaNByte
        bb.advanceByte();
        return new ScannedNumber(Double.NaN, false, false);
    }

    private ScannedNumber scanJson5KeywordNumber(char first) {
        boolean negative = false;

        if (first == '-' || first == '+') {
            negative = (first == '-');
            buffer.advance();
            first = buffer.peek();
        }

        if (buffer instanceof JsonSourceByteBuffer) {
            if (asciiStartsWith("Infinity")) {
                advanceBytes("Infinity".length());
                return new ScannedNumber(
                    negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY,
                    false,
                    false
                );
            }
            if (byteStartsWith("NaN")) {
                advanceBytes("NaN".length());
                return new ScannedNumber(Double.NaN, false, false);
            }
        } else {
            if (asciiStartsWith("Infinity")) {
                advanceChars("Infinity".length());
                return new ScannedNumber(
                    negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY,
                    false,
                    false
                );
            }
            if (asciiStartsWith("NaN")) {
                advanceChars("NaN".length());
                return new ScannedNumber(Double.NaN, false, false);
            }
        }

        // Should never reach here
        return new ScannedNumber(Double.NaN, false, false);
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
    // Identifiers
    //

    // TODO: This uses SIMD directly. Move it.
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
                        buffer.advance();
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

    private void scanWhitespaceAndComments() {
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

    //
    // Helpers
    //

    private boolean startsWithInfinityOrNaNByte(byte first) {
        JsonSourceByteBuffer bb = (JsonSourceByteBuffer) buffer;
        byte[] raw = bb.raw;
        int off = bb.offset();

        // Unsigned byte check
        int c = first & 0xFF;

        // Infinity
        if (c == 'I') {
            return bb.matchKeywordByte(raw, off, INFINITY);
        }

        // NaN
        if (c == 'N') {
            return bb.matchKeywordByte(raw, off, NAN);
        }

        // Signed Infinity/NaN
        if (c == '-' || c == '+') {
            if (off + 1 < raw.length) {
                int next = raw[off + 1] & 0xFF;
                if (next == 'I') {
                    return bb.matchKeywordByte(raw, off, INFINITY, 1);
                }
                if (next == 'N') {
                    return bb.matchKeywordByte(raw, off, NAN, 1);
                }
            }
        }

        return false;
    }

    private boolean startsWithInfinityOrNaN(char first) {
        if (first == 'I') return asciiStartsWith("Infinity");
        if (first == 'N') return asciiStartsWith("NaN");
        if (first == '-' || first == '+') {
            char c = buffer.peekAhead(1);
            return c == 'I' && charStartsWith("Infinity", 1)
                || c == 'N' && charStartsWith("NaN", 1);
        }
        return false;
    }

    private boolean asciiStartsWith(String kw) {
        int n = kw.length();
        for (int k = 0; k < n; k++) {
            if (buffer.peekAhead(k) != kw.charAt(k)) return false;
        }
        return true;
    }

    private boolean byteStartsWith(String kw) {
        JsonSourceByteBuffer bb = (JsonSourceByteBuffer) buffer;
        byte[] raw = bb.raw;
        int off = buffer.offset();
        int n = kw.length();

        if (off + n > raw.length) return false;

        for (int k = 0; k < n; k++) {
            if (raw[off + k] != (byte) kw.charAt(k)) return false;
        }
        return true;
    }

    // // TODO: This is not called from anywhere. Remove it?
    // private boolean byteStartsWith(String kw, int relativeOffset) {
    //     JsonSourceByteBuffer bb = (JsonSourceByteBuffer) buffer;
    //     byte[] raw = bb.raw;
    //     int off = buffer.offset() + relativeOffset;
    //     int n = kw.length();

    //     if (off + n > raw.length) return false;

    //     for (int k = 0; k < n; k++) {
    //         if (raw[off + k] != (byte) kw.charAt(k)) return false;
    //     }
    //     return true;
    // }

    private boolean charStartsWith(String kw, int relativeOffset) {
        int n = kw.length();

        for (int i = 0; i < n; i++) {
            char c = buffer.peekAhead(relativeOffset + i);
            if (c != kw.charAt(i)) {
                return false;
            }
        }

        return true;
    }

    private void advanceChars(int count) {
        for (int i = 0; i < count; i++) buffer.advance();
    }

    private void advanceBytes(int count) {
        JsonSourceByteBuffer buffer = (JsonSourceByteBuffer)this.buffer;
        for (int i = 0; i < count; i++) buffer.advanceByte();
    }



    // // TODO: This is not called from anywhere. Remove it?
    // private boolean startsWithInfinityOrNaNByte(byte first) {
    //     int off = buffer.offset();
    //     byte[] raw = ((JsonSourceByteBuffer)buffer).raw;

    //     if (first == 'I') return matchKeywordByte(raw, off, "Infinity");
    //     if (first == 'N') return matchKeywordByte(raw, off, "NaN");

    //     if (first == '-' || first == '+') {
    //         if (off + 1 < raw.length) {
    //             byte c = raw[off + 1];
    //             if (c == 'I') return matchKeywordByte(raw, off + 1, "Infinity");
    //             if (c == 'N') return matchKeywordByte(raw, off + 1, "NaN");
    //         }
    //     }
    //     return false;
    // }

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

    // TODO: The following 2 methods should be replaced by a single JsonSourceByteBuffer instantiation.
    private SourceBuffer setupByteBuffer(byte[] data) {
        // SIMD & no JSON5 unquoted keys - use byte-based buffer
        if (options.useSimd() && !options.allowUnquotedKeys()) {
            usingByteBuffer = true;
            return new JsonSourceByteBuffer(data, options);
        } else {
            return new SourceStringBuffer(new String(data));
        }
    }

    private SourceBuffer setupStringBuffer(String text) {
        // SIMD & no JSON5 unquoted keys - use byte-based buffer
        if (options.useSimd() && !options.allowUnquotedKeys()) {
            usingByteBuffer = true;
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            return new JsonSourceByteBuffer(data, options);
        } else {
            return new SourceStringBuffer(text);
        }
    }

    private SourceBuffer setupStreamBuffer(InputStream stream) {
        final SourceInputStreamBuffer buffer = new SourceInputStreamBuffer(stream);

        // TODO: Make this work

        // SourceInputStreamBuffer doesn't currently implement SimdCapableBuffer
        // so this will be simpler than setupStringBuffer.

        // digitScanner = this::scanDigits;
        // triviaHandler = this::advanceWhitespaceAndComments;
        // advance = buffer::advance;

        // if (ALLOW_COMMENTS) {
        //     tokenHandler = this::nextTokenWithComments;
        // } else {
        //     tokenHandler = this::nextTokenWithoutComments;
        // }
        // if (ALLOW_UNICODE) {
        //     tokenScanner = this::scanTokenAsciiOrUnicode;
        //     stringScanner = this::scanString;
        //     numberScanner = this::scanNumberUnicode;
        //     numberScanner = this::scanNumberAscii;
        //     identifierScanner = this::scanIdentifierUnicode;
        // } else {
        //     tokenScanner = this::scanTokenAscii;
        //     stringScanner = this::scanString;
        //     numberScanner = this::scanNumberAscii;
        //     identifierScanner = this::scanIdentifierAscii;
        // }

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
        switch (type) {
            case LEFT_BRACE:    return LBRACE;
            case RIGHT_BRACE:   return RBRACE;
            case LEFT_BRACKET:  return LBRACKET;
            case RIGHT_BRACKET: return RBRACKET;
            case COLON:         return COLON;
            case COMMA:         return COMMA;
            default: throw new IllegalStateException();
        }
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
            line, column,
            buffer.windowStartOffset(),
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
        JsonToken makeNumberToken(int line, int column, int startOffset, ScannedNumber number);
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
        public JsonToken makeNumberToken(int line, int column, int startOffset, ScannedNumber number) {
            return new JsonNumberToken(lp, line, column, startOffset, buffer.offset(), number);
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
        public JsonLexemeBackedToken makeNumberToken(int line, int column, int startOffset, ScannedNumber number) {
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