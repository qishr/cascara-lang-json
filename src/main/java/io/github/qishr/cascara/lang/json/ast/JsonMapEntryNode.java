package io.github.qishr.cascara.lang.json.ast;

import java.util.List;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;

/// Represents the structural pairing of a key and a value in a JSON object.
public class JsonMapEntryNode extends JsonNode implements MapEntryAstNode<String,JsonNode> {
    private final String key;
    private JsonNode value;

    public JsonMapEntryNode(int line, int column, String key, JsonNode value) {
        super(line, column);
        this.key = key;
        this.value = value;
    }

    /// Convenience constructor for programmatic node creation.
    public JsonMapEntryNode(String key, JsonNode value) {
        super(0, 0);
        this.key = key;
        this.value = value;
    }

    @Override public String getKey() { return key; }

    @Override public JsonNode getValue() { return value; }

    @Override public JsonMapEntryNode setRaw(JsonNode value) {
        this.value = value;
        return this;
    }

    @Override public List<JsonNode> getChildren() {
        // TODO:
        // return List.of(key, value);
        return List.of(value);
    }

    @Override
    public JsonMapEntryNode setValue(JsonNode value) {
        this.value = value;
        return this;
    }
}