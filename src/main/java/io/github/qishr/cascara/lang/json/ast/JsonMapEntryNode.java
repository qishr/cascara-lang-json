package io.github.qishr.cascara.lang.json.ast;

import java.net.URI;
import java.util.List;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;

/// Represents the structural pairing of a key and a value in a JSON object.
public class JsonMapEntryNode extends JsonNode implements MapEntryAstNode<JsonNode> {
    private final JsonNode key;
    private JsonNode value;

    public JsonMapEntryNode(int line, int column, URI uri, JsonNode key, JsonNode value) {
        super(line, column, uri);
        this.key = key;
        this.value = value;
    }

    /// Convenience constructor for programmatic node creation.
    public JsonMapEntryNode(JsonNode key, JsonNode value) {
        super(0, 0, null);
        this.key = key;
        this.value = value;
    }

    @Override public JsonNode getKey() { return key; }

    @Override public JsonNode getValue() { return value; }

    @Override public void setValue(JsonNode value) {
        this.value = value;
    }

    @Override public List<JsonNode> getChildren() {
        return List.of(key, value);
    }
}