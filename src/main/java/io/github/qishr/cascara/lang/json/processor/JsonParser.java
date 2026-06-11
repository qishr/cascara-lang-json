package io.github.qishr.cascara.lang.json.processor;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.Parser;
import io.github.qishr.cascara.lang.json.ast.*;
import io.github.qishr.cascara.lang.json.exception.JsonDiagnosticCode;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;

/// A recursive descent parser for JSON/JSON5.
public class JsonParser extends AbstractJsonProcessor<JsonParser> implements Parser<JsonNode, JsonToken> {
    private URI uri;
    private List<JsonToken> tokens;
    private int current = 0;
    private int depth = 0;

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<JsonCommentNode> pendingComments = new ArrayList<>();

    /// Default constructor for SPI
    public JsonParser() {}

    @Override protected JsonParser self() { return this; }

    @Override
    public JsonNode parse(String text) {
        JsonTokenizer tokenizer = new JsonTokenizer();
        tokenizer.setOptions(options);
        tokenizer.setReporter(reporter);
        return parse(tokenizer.tokenize(text));
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

            // Just return the node; don't attach yet!
            return switch (token.getType()) {
                case LEFT_BRACE -> parseMap();
                case LEFT_BRACKET -> parseSequence();
                case STRING, NUMBER, BOOLEAN, NULL, IDENTIFIER -> parseScalar();
                default -> new JsonScalarNode(token.getStartLine(), token.getStartColumn(), "", "", null);
            };
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

            if (!check(JsonTokenType.RIGHT_BRACE)) {
                do {
                    skipTrivia();
                    if (isAtEnd() || check(JsonTokenType.RIGHT_BRACE)) break; // Safety check

                    // JSON5: Handle trailing comma by checking for '}' after a comma
                    if (check(JsonTokenType.RIGHT_BRACE)) break;

                    JsonToken keyTok = consumeKey();

                    QuoteStyle style = (keyTok.getType() == JsonTokenType.IDENTIFIER)
                            ? QuoteStyle.PLAIN
                            : QuoteStyle.DOUBLE;

                    JsonScalarNode key = new JsonScalarNode(
                            keyTok.getStartLine(), keyTok.getStartColumn(),
                            keyTok.getLexeme(), keyTok.getContent(), style
                        );
                    key.setToken(keyTok);

                    // The key claims EVERYTHING in the buffer since the last key's value was finished
                    attachComments(key);

                    consume(JsonTokenType.COLON, JsonDiagnosticCode.EXPECTED_COLON_MAP_KEY);
                    JsonNode value = parseValue(); // parseValue should NOT call attachComments internally
                    map.put(key, value);

                    skipTrivia();
                } while (!isAtEnd() && match(JsonTokenType.COMMA));
            }

            consume(JsonTokenType.RIGHT_BRACE, JsonDiagnosticCode.EXPECTED_CLOSE_BRACE);
            return map;
        } finally {
            depth--;
        }
    }

    private JsonSequenceNode parseSequence() {
        depth++;
        trace("parseSequence");
        try {
            JsonToken start = consume(JsonTokenType.LEFT_BRACKET, JsonDiagnosticCode.EXPECTED_OPEN_BRACKET);
            JsonSequenceNode seq = new JsonSequenceNode(start.getStartLine(), start.getStartColumn(), uri);

            attachComments(seq);

            if (!check(JsonTokenType.RIGHT_BRACKET)) {
                do {
                    skipTrivia();
                    if (isAtEnd() || check(JsonTokenType.RIGHT_BRACKET)) break; // Safety check

                    if (check(JsonTokenType.RIGHT_BRACKET)) break;

                    // 1. Parse the value
                    JsonNode item = parseValue();

                    // 2. FIX: If it's a structural node (Map/Seq), it hasn't
                    // attached comments yet because parseValue is now "silent".
                    // Scalars handle themselves, but calling attachComments
                    // here is safe for all types.
                    attachComments(item);

                    seq.add(item);

                    skipTrivia();
                } while (!isAtEnd() && match(JsonTokenType.COMMA));
            }

            consume(JsonTokenType.RIGHT_BRACKET, JsonDiagnosticCode.EXPECTED_CLOSE_BRACKET);
            return seq;
        } finally {
            depth--;
        }
    }

    private JsonScalarNode parseScalar() {
        JsonToken token = advance();

        // TODO: This should be done in TypeDescriptor

        // JSON5/Standard logic:
        // Strings get DOUBLE (or SINGLE in JSON5), everything else is PLAIN
        QuoteStyle style = switch (token.getType()) {
            case STRING -> QuoteStyle.DOUBLE;
            case IDENTIFIER, NUMBER, BOOLEAN, NULL -> QuoteStyle.PLAIN;
            default -> QuoteStyle.PLAIN;
        };

        JsonScalarNode scalar = new JsonScalarNode(
            token.getStartLine(),
            token.getStartColumn(),
            token.getLexeme(),
            token.getContent(),
            style
        );

        return attachComments(scalar); // Claims leading comments
    }

    private void skipTrivia() {
        while (!isAtEnd()) {
            if (check(JsonTokenType.COMMENT)) {
                JsonToken tok = advance();

                // Determine if it's a block comment (/* ... */)
                // vs a line comment (// ...)
                boolean isBlock = tok.getLexeme().startsWith("/*");

                pendingComments.add(new JsonCommentNode(
                    tok.getStartLine(),
                    tok.getStartColumn(),
                    uri,
                    tok.getLexeme(),
                    tok.getContent(),
                    isBlock
                ));
                continue;
            }
            break;
        }
    }


    private <T extends JsonNode> T attachComments(T node) {
        if (node == null) return null;
        for (JsonCommentNode comment : pendingComments) {
            node.addComment(comment);
        }
        pendingComments.clear();
        return node;
    }

    private JsonToken consumeKey() {
        if (check(JsonTokenType.STRING) || check(JsonTokenType.IDENTIFIER)) {
            return advance();
        }
        error(peek(), JsonDiagnosticCode.EXPECTED_MAP_KEY);
        // If we're at EOF or wrong token, return current and let the parser try to recover
        return advance();
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
        if (reporter == null) return;
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
}