package io.github.qishr.cascara.lang.json.ast;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.lang.json.util.JsonOptions;

public class JsonMapNode extends JsonNode implements MapAstNode<String, JsonNode, JsonMapEntryNode> {
    private final LinkedHashMap<String, JsonMapEntryNode> entriesByKey = new LinkedHashMap<>();
    private final JsonOptions options;

    public JsonMapNode() {
        super();
        this.options = JsonOptions.STRICT;
    }

    public JsonMapNode(int line, int column) {
        super(line, column);
        this.options = JsonOptions.STRICT;
    }

    public JsonMapNode(JsonOptions options) {
        super();
        this.options = options;
    }

    public JsonMapNode(int line, int column, JsonOptions options) {
        super(line, column);
        this.options = options;
    }

    @Override
    public List<JsonMapEntryNode> getChildren() {
        return List.copyOf(entriesByKey.values());
    }

    @Nullable
    public JsonMapEntryNode getEntry(String key) {
        if (key == null) return null;
        return entriesByKey.get(key);
    }

    @Override
    public List<JsonMapEntryNode> getEntries() {
        return List.copyOf(entriesByKey.values());
    }

    @Override
    public Set<String> keySet() {
        Set<String> keys = new LinkedHashSet<>();
        for (JsonMapEntryNode entry : entriesByKey.values()) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    @Override
    public Set<JsonMapEntryNode> entrySet() {
        return new LinkedHashSet<>(entriesByKey.values());
    }

    @Override
    public JsonMapNode remove(String key) {
        entriesByKey.remove(key);
        return this;
    }

    @Override
    public JsonNode get(String key) {
        JsonMapEntryNode entry = entriesByKey.get(key);
        return entry == null ? null : entry.getValue();
    }

    @Override
    public JsonMapNode getMap(String key) {
        JsonNode node = this.get(key);
        return (node instanceof JsonMapNode map) ? map : new JsonMapNode();
    }

    @Override
    public JsonSequenceNode getSequence(String key) {
        JsonNode node = this.get(key);
        return (node instanceof JsonSequenceNode seq) ? seq : new JsonSequenceNode();
    }

    @Override
    public JsonMapNode put(String key, JsonNode value) {
        JsonMapEntryNode existing = entriesByKey.get(key);
        if (existing != null) {
            // TODO: Should the key's comments be removed?
            // This should be in the javadoc.
            existing.setRaw(value);
            return this;
        }

        JsonMapEntryNode entry = new JsonMapEntryNode(0, 0, key, value);
        entriesByKey.put(key, entry);
        return this;
    }

    public boolean containsKey(String key) {
        return entriesByKey.containsKey(key);
    }

    /// {@inheritDoc}
    @Override
    public List<JsonNode> values() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'values'");
    }

    /// {@inheritDoc}
    @Override
    public JsonMapNode put(String key, String value) {
        return put(key, new JsonScalarNode(value, options));
    }

    @Override
    public int size() {
        return entriesByKey.size();
    }

    @Override
    public boolean isEmpty() {
        return entriesByKey.isEmpty();
    }

    /// Returns Iterator instance
    @Override
    public Iterator<JsonMapEntryNode> iterator() {
        return entriesByKey.sequencedValues().iterator();
    }
}