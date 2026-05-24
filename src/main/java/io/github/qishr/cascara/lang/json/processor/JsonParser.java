package io.github.qishr.cascara.lang.json.processor;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.common.lang.processor.Parser;
import io.github.qishr.cascara.lang.json.JsonDocument;
import io.github.qishr.cascara.lang.json.ast.*;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;

/// A recursive descent parser for JSON/JSON5.
public class JsonParser extends AbstractJsonProcessor<JsonParser> implements Parser<JsonDocument, JsonToken> {
    private URI uri;
    private List<JsonToken> tokens;
    private int current = 0;
    private int depth = 0;

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<JsonCommentNode> pendingComments = new ArrayList<>();

    @Override protected JsonParser self() { return this; }

    @Override
    public JsonDocument parse(String text) {
        return parse(text, null);
    }

    @Override
    public JsonDocument parse(String text, URI uri) {
        JsonTokenizer tokenizer = new JsonTokenizer();
        tokenizer.setReporter(this.reporter);
        return parse(tokenizer.tokenize(text, uri), uri);
    }

    @Override
    public JsonDocument parse(List<JsonToken> tokens) {
        return parse(tokens, null);
    }

    @Override
    public JsonDocument parse(List<JsonToken> tokens, URI uri) {
        this.tokens = tokens;
        this.uri = uri;
        this.current = 0;
        return parseDocument();
    }

    private JsonDocument parseDocument() {
        // Headers and structural trivia
        skipTrivia();

        JsonNode root = null;
        if (!isAtEnd()) {
            root = parseValue();
        }

        skipTrivia();
        JsonDocument doc = new JsonDocument(root);
        // Header comments stay in the document
        doc.getComments().addAll(pendingComments);
        pendingComments.clear();

        return doc;
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
                default -> new JsonScalarNode(token.getStartLine(), token.getStartColumn(), uri, "", "", null);
            };
        } finally {
            depth--;
        }
    }

    private JsonMapNode parseMap() {
        depth++;
        trace("parseMap");
        try {
            JsonToken start = consume(JsonTokenType.LEFT_BRACE, "Expected '{'");
            JsonMapNode map = new JsonMapNode(start.getStartLine(), start.getStartColumn(), uri);

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
                            keyTok.getStartLine(), keyTok.getStartColumn(), uri,
                            keyTok.getLexeme(), (String) keyTok.getValue(), style
                        );
                    key.setToken(keyTok);

                    // The key claims EVERYTHING in the buffer since the last key's value was finished
                    attachComments(key);

                    consume(JsonTokenType.COLON, "Expected ':'");
                    JsonNode value = parseValue(); // parseValue should NOT call attachComments internally
                    map.put(key, value);

                    skipTrivia();
                } while (!isAtEnd() && match(JsonTokenType.COMMA));
            }

            consume(JsonTokenType.RIGHT_BRACE, "Expected '}'");
            return map;
        } finally {
            depth--;
        }
    }

    private JsonSequenceNode parseSequence() {
        depth++;
        trace("parseSequence");
        try {
            JsonToken start = consume(JsonTokenType.LEFT_BRACKET, "Expected '['");
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

            consume(JsonTokenType.RIGHT_BRACKET, "Expected ']'");
            return seq;
        } finally {
            depth--;
        }
    }

    private JsonScalarNode parseScalar() {
        JsonToken token = advance();

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
            uri,
            token.getLexeme(),
            (String) token.getValue(),
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
                    (String) tok.getValue(),
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
        error(peek(), "Expected key (string or identifier)");
        // If we're at EOF or wrong token, return current and let the parser try to recover
        return advance();
    }

    private JsonToken consume(JsonTokenType type, String message) {
        if (check(type)) return advance();

        // Report the error but don't crash
        error(peek(), message);

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

    private void error(JsonToken token, String message) {
        if (reporter != null) {
            reporter.errorAt(token.getStartLine(), token.getStartColumn(), uri, message);
        }
    }
}