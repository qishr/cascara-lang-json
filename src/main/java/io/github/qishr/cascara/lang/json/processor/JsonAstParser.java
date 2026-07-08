package io.github.qishr.cascara.lang.json.processor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.util.Properties;
import io.github.qishr.cascara.lang.json.JsonOptions;
import io.github.qishr.cascara.lang.json.JsonPrimitiveDescriptor;
import io.github.qishr.cascara.lang.json.ast.JsonCommentNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;
import io.github.qishr.cascara.lang.json.exception.JsonDiagnosticCode;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;

/// A recursive descent parser for JSON/JSON5.
public class JsonAstParser extends AbstractJsonProcessor<JsonAstParser>
        implements AstParser<JsonNode, JsonToken> {

    private JsonPrimitiveDescriptor descriptor;

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<JsonCommentNode> pendingComments = new ArrayList<>();

    // Note: Keep this in alphabetical order,
    // or it will become time consuming to maintain
    private boolean ALLOW_COMMENTS;
    private boolean ALLOW_INFINITY_AND_NAN;
    private boolean ALLOW_TRAILING_COMMA;
    private boolean ALLOW_UNQUOTED_KEYS;
    private boolean CAPTURE_COMMENTS;

    private JsonTokenizer tokenizer;
    private int depth = 0;

    private JsonToken currentToken;
    private JsonToken lookaheadToken;



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
        descriptor = new JsonPrimitiveDescriptor(options);
        // Note: Keep this in alphabetical order,
        // or it will become time consuming to maintain
        this.ALLOW_COMMENTS         = options.allowComments();
        this.ALLOW_INFINITY_AND_NAN = options.allowInfinityAndNaN();
        this.ALLOW_TRAILING_COMMA   = options.allowTrailingComma();
        this.ALLOW_UNQUOTED_KEYS    = options.allowUnquotedKeys();
        this.CAPTURE_COMMENTS       = options.captureComments();
    }

    @Override
    protected JsonAstParser self() { return this; }

    // ---------------------------------------------------------------------
    // High-level API: String / InputStream
    // ---------------------------------------------------------------------

    @Override
    public JsonNode parse(String text) {
        JsonTokenizer tokenizer = new JsonTokenizer();
        tokenizer.setOptions(options);
        tokenizer.setReporter(reporter);
        tokenizer.open(text);
        return parse(tokenizer);
    }

    @Override
    public JsonNode parse(InputStream is) {
        JsonTokenizer tokenizer = new JsonTokenizer();
        tokenizer.setOptions(options);
        tokenizer.setReporter(reporter);
        tokenizer.open(is);
        return parse(tokenizer);
    }

    // ---------------------------------------------------------------------
    // Eager API: List<JsonToken> (adapter to streaming)
    // ---------------------------------------------------------------------

    @Override
    public JsonNode parse(List<JsonToken> tokens) {
        return parse(new ListBackedJsonTokenizer(tokens));
    }

    // ---------------------------------------------------------------------
    // Streaming API: Tokenizer<JsonToken>
    // ---------------------------------------------------------------------

    @Override
    public JsonNode parse(Tokenizer<JsonToken> tokenizer) {
        this.tokenizer = (JsonTokenizer) tokenizer;
        this.currentToken = null;
        this.depth = 0;
        pendingComments.clear();

        // Headers and structural trivia
        skipTrivia();

        JsonNode root = null;
        if (!isAtEnd()) {
            root = parseValue();
        }

        skipTrivia();

        // Header comments stay in the document
        if (root != null) {
            root.getComments().addAll(pendingComments);
        }
        pendingComments.clear();

        return root;
    }

    // ---------------------------------------------------------------------
    // Core parsing
    // ---------------------------------------------------------------------

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
                        QuoteStyle.PLAIN,
                        descriptor,
                        false
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
                null,
                descriptor,
                false
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
                JsonToken keyTok = advance();
                JsonScalarNode key = parseKey(keyTok);

                // JSON5: unquoted keys allowed only when configured
                if (!ALLOW_UNQUOTED_KEYS && key.getQuoteStyle() != QuoteStyle.DOUBLE) {
                    error(key.getToken(), JsonDiagnosticCode.EXPECTED_MAP_KEY);
                }

                // Duplicate key detection
                String keyString = key.getKeyString();
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
                    QuoteStyle.DOUBLE,
                    descriptor,
                    true
                );
            }

            case IDENTIFIER -> {
                if (ALLOW_UNQUOTED_KEYS) {
                    key = new JsonScalarNode(
                        tok.getStartLine(),
                        tok.getStartColumn(),
                        tok.getLexeme(),
                        tok.getContent(),
                        QuoteStyle.PLAIN,
                        descriptor,
                        true
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
                        tok.getQuoteStyle(),
                        descriptor,
                        false
                    );
                }

                case NUMBER -> {
                    node = new JsonScalarNode(
                        tok.getStartLine(),
                        tok.getStartColumn(),
                        tok.getLexeme(),
                        tok.getContent(),
                        QuoteStyle.PLAIN,
                        descriptor,
                        false
                    );
                }

                case BOOLEAN -> {
                    node = new JsonScalarNode(
                        tok.getStartLine(),
                        tok.getStartColumn(),
                        tok.getLexeme(),
                        tok.getContent(),
                        QuoteStyle.PLAIN,
                        descriptor,
                        false
                    );
                }

                case NULL -> {
                    node = new JsonScalarNode(
                        tok.getStartLine(),
                        tok.getStartColumn(),
                        tok.getLexeme(),
                        tok.getContent(),
                        QuoteStyle.PLAIN,
                        descriptor,
                        false
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
                            QuoteStyle.PLAIN,
                            descriptor,
                            false
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
                        null,
                        descriptor,
                        false
                    );
                }
            }

            attachComments(node);
            return node;

        } finally {
            depth--;
        }
    }

    // ---------------------------------------------------------------------
    // Trivia / comments
    // ---------------------------------------------------------------------

    private void skipTrivia() {
        if (!ALLOW_COMMENTS) return;

        while (peek().getType() == JsonTokenType.COMMENT) {
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

    // ---------------------------------------------------------------------
    // Token navigation (streaming)
    // ---------------------------------------------------------------------

    private JsonToken peek() {
        if (currentToken == null) {
            if (lookaheadToken != null) {
                currentToken = lookaheadToken;
                lookaheadToken = null;
            } else {
                currentToken = tokenizer.nextToken();
            }
        }
        return currentToken;
    }

    private JsonToken advance() {
        JsonToken prev = peek();
        currentToken = null;
        return prev;
    }

    private JsonToken lookahead() {
        if (lookaheadToken == null) {
            lookaheadToken = tokenizer.nextToken();
        }
        return lookaheadToken;
    }

    private boolean isAtEnd() {
        return peek().getType() == JsonTokenType.EOF;
    }

    private boolean check(JsonTokenType type) {
        return peek().getType() == type;
    }

    private boolean match(JsonTokenType... types) {
        for (JsonTokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private JsonToken consume(JsonTokenType type, DiagnosticCode code, Object... details) {
        if (check(type)) return advance();

        // Report the error but don't crash
        error(peek(), code, details);

        // Return the current token anyway to avoid crashing the caller,
        // or return a dummy token.
        return peek();
    }

    // ---------------------------------------------------------------------
    // Diagnostics
    // ---------------------------------------------------------------------

    private void trace(String methodName) {
        if (reporter instanceof NoOpReporter) return;
        JsonToken tok = peek();
        String indent = "  ".repeat(Math.max(0, depth));
        reporter.trace("L%3d C%3d %s%s: %s",
            tok.getStartLine(), tok.getStartColumn(), indent, methodName, tok.getType());
    }

    private void error(JsonToken token, DiagnosticCode code, Object... details) {
        reporter.errorAt(token, code, details);
        if (!reporter.collectsProblems()) {
            throw new ParserException(token, code, details);
        }
    }

    // ---------------------------------------------------------------------
    // Adapter: List<JsonToken> → Tokenizer<JsonToken>
    // ---------------------------------------------------------------------

    private static final class ListBackedJsonTokenizer implements Tokenizer<JsonToken> {

        private final List<JsonToken> tokens;
        private int index = 0;

        // Required fields for Processor
        private Reporter reporter = new NoOpReporter();
        private LanguageOptions<?> options = null;

        ListBackedJsonTokenizer(List<JsonToken> tokens) {
            this.tokens = tokens;
        }

        // ---------------------------------------------------------------------
        // Tokenizer API
        // ---------------------------------------------------------------------

        @Override
        public void open(String text) {
            throw new UnsupportedOperationException("List-backed tokenizer does not support open(String)");
        }

        @Override
        public void open(InputStream is) {
            throw new UnsupportedOperationException("List-backed tokenizer does not support open(InputStream)");
        }

        @Override
        public JsonToken nextToken() {
            if (index >= tokens.size()) {
                // Return last token (EOF) repeatedly
                return tokens.get(tokens.size() - 1);
            }
            return tokens.get(index++);
        }

        @Override
        public Set<? extends JsonTokenType> getTokenTypes() {
            return Set.of(JsonTokenType.values());
        }

        // ---------------------------------------------------------------------
        // Processor API (inherited)
        // ---------------------------------------------------------------------

        @Override
        public ListBackedJsonTokenizer setReporter(Reporter reporter) {
            this.reporter = (reporter != null) ? reporter : new NoOpReporter();
            return this;
        }

        @Override
        public ListBackedJsonTokenizer setOptions(LanguageOptions<?> options) {
            this.options = options;
            return this;
        }

        // ---------------------------------------------------------------------
        // ServiceProvider API (inherited)
        // ---------------------------------------------------------------------

        @Override
        public Properties getServiceProperties() {
            return new Properties(); // no properties needed
        }

        @Override
        public ContentType getContentType() {
            return null; // safe default
        }
    }

}
