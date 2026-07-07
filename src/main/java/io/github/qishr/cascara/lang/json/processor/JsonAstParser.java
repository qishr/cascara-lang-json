package io.github.qishr.cascara.lang.json.processor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.json.JsonOptions;
import io.github.qishr.cascara.lang.json.ast.JsonCommentNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;
import io.github.qishr.cascara.lang.json.exception.JsonDiagnosticCode;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;

/// A recursive descent parser for JSON/JSON5.
public class JsonAstParser extends AbstractJsonProcessor<JsonAstParser> implements AstParser<JsonNode, JsonToken> {
    private List<JsonToken> tokens;
    private int current = 0;
    private int depth = 0;

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<JsonCommentNode> pendingComments = new ArrayList<>();

    // Note: Keep this in alphabetical order,
    // or it will become time consuming to maintain
    private boolean ALLOW_COMMENTS;
    private boolean ALLOW_INFINITY_AND_NAN;
    private boolean ALLOW_TRAILING_COMMA;
    private boolean ALLOW_UNQUOTED_KEYS;
    private boolean CAPTURE_COMMENTS;

    /// Default constructor for SPI
    public JsonAstParser() {
        applyOptions(new JsonOptions());
    }

    public JsonAstParser setOptions(JsonOptions options) {
        super.setOptions(options);
        applyOptions(options);
        return this;
    }

    private void applyOptions(JsonOptions options) {
        // Note: Keep this in alphabetical order,
        // or it will become time consuming to maintain
        this.ALLOW_COMMENTS         = options.allowComments();
        this.ALLOW_INFINITY_AND_NAN = options.allowInfinityAndNaN();
        this.ALLOW_TRAILING_COMMA   = options.allowTrailingComma();
        this.ALLOW_UNQUOTED_KEYS    = options.allowUnquotedKeys();
        this.CAPTURE_COMMENTS       = options.captureComments();
    }

    @Override protected JsonAstParser self() { return this; }

    @Override
    public JsonNode parse(String text) {
        JsonTokenizer tokenizer = new JsonTokenizer();
        tokenizer.setOptions(options);
        tokenizer.setReporter(reporter);
        return parse(tokenizer.tokenize(text));
    }

    @Override
    public JsonNode parse(InputStream is) {
        JsonTokenizer tokenizer = new JsonTokenizer();
        tokenizer.setOptions(options);
        tokenizer.setReporter(reporter);
        return parse(tokenizer.tokenize(is));
    }

    @Override
    public JsonNode parse(List<JsonToken> tokens) {
        this.tokens = tokens;
        this.current = 0;

        // Headers and structural trivia
        skipTrivia();

        JsonNode root = null;
        if (!isAtEnd()) {
            root = parseValue();
        }

        skipTrivia();

        // Header comments stay in the document
        root.getComments().addAll(pendingComments);
        pendingComments.clear();

        return root;
    }

    private JsonNode parseValue() {
        depth++;
        trace("parseValue");

        try {
            skipTrivia();

            JsonToken token = peek();
            JsonTokenType type = token.getType();

            // Structural values first (most common in real JSON)
            if (type == JsonTokenType.LEFT_BRACE) {
                return parseMap();
            }
            if (type == JsonTokenType.LEFT_BRACKET) {
                return parseSequence();
            }

            // Primitive values (second most common)
            if (type == JsonTokenType.STRING ||
                type == JsonTokenType.NUMBER ||
                type == JsonTokenType.BOOLEAN ||
                type == JsonTokenType.NULL) {
                return parseScalar();
            }

            // JSON5 identifiers (Infinity/NaN only)
            if (type == JsonTokenType.IDENTIFIER) {
                String ident = token.getContent();

                if ("Infinity".equals(ident) || "NaN".equals(ident)) {
                    advance();
                    JsonScalarNode node = new JsonScalarNode(
                        token.getStartLine(),
                        token.getStartColumn(),
                        token.getLexeme(),
                        ident,
                        QuoteStyle.PLAIN
                    );
                    attachComments(node);
                    return node;
                }

                // Everything else is an error
                error(token, JsonDiagnosticCode.UNEXPECTED_UNQUOTED_STRING_VALUE, ident);
                return new JsonScalarNode(); // safe empty node
            }

            // Unexpected token
            error(token, JsonDiagnosticCode.UNEXPECTED_TOKEN, type);
            return new JsonScalarNode(
                token.getStartLine(),
                token.getStartColumn(),
                "",
                "",
                null
            );

        } finally {
            depth--;
        }
    }

    private JsonMapNode parseMap() {
        depth++;
        trace("parseMap");

        try {
            JsonToken start = consume(JsonTokenType.LEFT_BRACE, JsonDiagnosticCode.EXPECTED_OPEN_BRACE);
            JsonMapNode map = new JsonMapNode(start.getStartLine(), start.getStartColumn());

            attachComments(map);

            // Fast exit for empty object: {}
            if (check(JsonTokenType.RIGHT_BRACE)) {
                consume(JsonTokenType.RIGHT_BRACE, JsonDiagnosticCode.EXPECTED_CLOSE_BRACE);
                return map;
            }

            // Track unique keys
            Set<String> seenKeys = new HashSet<>();

            while (!isAtEnd()) {

                // Consume comments only (skipTrivia is cheap)
                skipTrivia();

                // JSON5 trailing comma: allow { key: value, }
                if (ALLOW_TRAILING_COMMA && check(JsonTokenType.RIGHT_BRACE)) {
                    break;
                }

                // ---- Parse key ----
                // JsonScalarNode key = parseScalar();

                JsonToken keyTok = advance();
                JsonScalarNode key = parseKey(keyTok);


                // JSON5: unquoted keys allowed only when configured
                if (!ALLOW_UNQUOTED_KEYS && key.getQuoteStyle() != QuoteStyle.DOUBLE) {
                    error(key.getToken(), JsonDiagnosticCode.EXPECTED_MAP_KEY);
                }

                // Duplicate key detection
                String keyString = key.getContent();
                if (!seenKeys.add(keyString)) {
                    error(key.getToken(), JsonDiagnosticCode.DUPLICATE_KEY, keyString);
                }

                // ---- Parse colon ----
                consume(JsonTokenType.COLON, JsonDiagnosticCode.EXPECTED_COLON_AFTER_MAP_KEY);

                // ---- Parse value ----
                JsonNode value = parseValue();
                map.put(key, value);

                // Consume comments only
                skipTrivia();

                // ---- Comma or end ----
                if (!match(JsonTokenType.COMMA)) {
                    break;
                }
            }

            consume(JsonTokenType.RIGHT_BRACE, JsonDiagnosticCode.EXPECTED_CLOSE_BRACE);
            return map;

        } finally {
            depth--;
        }
    }

    private JsonScalarNode parseKey(JsonToken tok) {
        JsonScalarNode key;

        switch (tok.getType()) {

            case STRING -> {
                key = new JsonScalarNode(
                    tok.getStartLine(),
                    tok.getStartColumn(),
                    tok.getLexeme(),
                    tok.getContent(),
                    QuoteStyle.DOUBLE
                );
            }

            case IDENTIFIER -> {
                if (ALLOW_UNQUOTED_KEYS) {
                    key = new JsonScalarNode(
                        tok.getStartLine(),
                        tok.getStartColumn(),
                        tok.getLexeme(),
                        tok.getContent(),
                        QuoteStyle.PLAIN
                    );
                } else {
                    error(tok, JsonDiagnosticCode.EXPECTED_MAP_KEY);
                    key = new JsonScalarNode();
                }
            }

            default -> {
                error(tok, JsonDiagnosticCode.EXPECTED_MAP_KEY);
                key = new JsonScalarNode();
            }
        }

        attachComments(key);
        return key;
    }

    private JsonSequenceNode parseSequence() {
        depth++;
        trace("parseSequence");

        try {
            JsonToken start = consume(JsonTokenType.LEFT_BRACKET, JsonDiagnosticCode.EXPECTED_OPEN_BRACKET);
            JsonSequenceNode seq = new JsonSequenceNode(start.getStartLine(), start.getStartColumn());

            attachComments(seq);

            // Fast exit for empty array: []
            if (check(JsonTokenType.RIGHT_BRACKET)) {
                consume(JsonTokenType.RIGHT_BRACKET, JsonDiagnosticCode.EXPECTED_CLOSE_BRACKET);
                return seq;
            }

            while (!isAtEnd()) {

                // Consume comments only
                skipTrivia();

                // JSON5 trailing comma: allow [ value, ]
                if (ALLOW_TRAILING_COMMA && check(JsonTokenType.RIGHT_BRACKET)) {
                    break;
                }

                // ---- Parse value ----
                JsonNode value = parseValue();
                seq.add(value);

                // Consume comments only
                skipTrivia();

                // ---- Comma or end ----
                if (!match(JsonTokenType.COMMA)) {
                    break;
                }
            }

            consume(JsonTokenType.RIGHT_BRACKET, JsonDiagnosticCode.EXPECTED_CLOSE_BRACKET);
            return seq;

        } finally {
            depth--;
        }
    }

    private JsonScalarNode parseScalar() {
        depth++;
        trace("parseScalar");

        try {
            skipTrivia();
            JsonToken tok = advance();   // consume the scalar token
            JsonTokenType type = tok.getType();

            JsonScalarNode node;

            switch (type) {

                case STRING -> {
                    node = new JsonScalarNode(
                        tok.getStartLine(),
                        tok.getStartColumn(),
                        tok.getLexeme(),
                        tok.getContent(),
                        tok.getQuoteStyle()
                    );
                }

                case NUMBER -> {
                    node = new JsonScalarNode(
                        tok.getStartLine(),
                        tok.getStartColumn(),
                        tok.getLexeme(),
                        tok.getContent(),
                        QuoteStyle.PLAIN
                    );
                }

                case BOOLEAN -> {
                    node = new JsonScalarNode(
                        tok.getStartLine(),
                        tok.getStartColumn(),
                        tok.getLexeme(),
                        tok.getContent(),
                        QuoteStyle.PLAIN
                    );
                }

                case NULL -> {
                    node = new JsonScalarNode(
                        tok.getStartLine(),
                        tok.getStartColumn(),
                        tok.getLexeme(),
                        tok.getContent(),
                        QuoteStyle.PLAIN
                    );
                }

                case IDENTIFIER -> {
                    String ident = tok.getContent();

                    if (ALLOW_INFINITY_AND_NAN &&
                        ("Infinity".equals(ident) || "NaN".equals(ident))) {

                        node = new JsonScalarNode(
                            tok.getStartLine(),
                            tok.getStartColumn(),
                            tok.getLexeme(),
                            ident,
                            QuoteStyle.PLAIN
                        );
                    } else {
                        error(tok, JsonDiagnosticCode.UNEXPECTED_UNQUOTED_STRING_VALUE, ident);
                        node = new JsonScalarNode();
                    }
                }

                default -> {
                    error(tok, JsonDiagnosticCode.UNEXPECTED_TOKEN, type);
                    node = new JsonScalarNode(
                        tok.getStartLine(),
                        tok.getStartColumn(),
                        "",
                        "",
                        null
                    );
                }
            }

            attachComments(node);
            return node;

        } finally {
            depth--;
        }
    }

    private void skipTrivia() {
        if (!ALLOW_COMMENTS) return;

        while (!isAtEnd()) {
            if (!check(JsonTokenType.COMMENT)) return;

            JsonToken tok = advance();

            // Avoid startsWith() cost when comment type is already known
            boolean isBlock = tok.getLexeme().length() > 1 && tok.getLexeme().charAt(1) == '*';

            pendingComments.add(new JsonCommentNode(
                tok.getStartLine(),
                tok.getStartColumn(),
                tok.getLexeme(),
                tok.getContent(),
                isBlock
            ));
        }
    }

    private <T extends JsonNode> T attachComments(T node) {
        if (node == null) return null;
        if (!(ALLOW_COMMENTS && CAPTURE_COMMENTS)) return node;
        for (JsonCommentNode comment : pendingComments) {
            node.addComment(comment);
        }
        pendingComments.clear();
        return node;
    }

    private JsonToken consume(JsonTokenType type, DiagnosticCode code, Object... details) {
        if (check(type)) return advance();

        // Report the error but don't crash
        error(peek(), code, details);

        // Return the current token anyway to avoid crashing the caller,
        // or return a dummy token.
        return peek();
    }

    private boolean check(JsonTokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private boolean match(JsonTokenType... types) {
        for (JsonTokenType type : types) {
            if (check(type)) { advance(); return true; }
        }
        return false;
    }

    private JsonToken advance() { if (!isAtEnd()) current++; return previous(); }
    private JsonToken peek() { return tokens.get(current); }
    private JsonToken previous() { return tokens.get(current - 1); }
    private boolean isAtEnd() { return current >= tokens.size() || peek().getType() == JsonTokenType.EOF; }

    private void trace(String methodName) {
        if (reporter instanceof NoOpReporter) return;
        String indent = "  ".repeat(Math.max(0, depth));
        reporter.trace("L%3d C%3d %s%s: %s",
            peek().getStartLine(), peek().getStartColumn(), indent, methodName, peek().getType());
    }

    private void error(JsonToken token, DiagnosticCode code, Object... details) {
        reporter.errorAt(token, code, details);
        if (!reporter.collectsProblems()) {
            throw new ParserException(token, code, details);
        }
    }

    @Override
    public JsonNode parse(Tokenizer<JsonToken> tokenizer) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'parse'");
    }
}