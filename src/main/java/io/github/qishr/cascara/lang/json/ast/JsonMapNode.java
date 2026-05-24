package io.github.qishr.cascara.lang.json.ast;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.common.lang.simple.SimpleMapEntryNode;

public class JsonMapNode extends JsonNode implements MapAstNode<JsonNode, JsonMapEntryNode> {
    private final List<JsonMapEntryNode> entries = new ArrayList<>();

    public JsonMapNode() { super(); }
    public JsonMapNode(int line, int column, URI uri) { super(line, column, uri); }

    @Override
    public boolean containsKey(JsonNode key) {
        return getEntry(key) != null;
    }

    @Override
    public JsonNode get(JsonNode key) {
        JsonMapEntryNode entry = getEntry(key);
        return entry == null ? null : entry.getValue();
    }

    @Override
    public List<JsonMapEntryNode> getChildren() {
        return entries;
    }

    @Override
    @Nullable
    public JsonMapEntryNode getEntry(JsonNode key) {
        for (JsonMapEntryNode entry : entries) {
            if (entry.getKey().equals(key)) return entry;
        }
        return null;
    }

    /// Convenience method for internal use and testing.
    /// Not part of the MapAstNode interface.
    public JsonMapEntryNode getEntry(String keyName) {
        // Create a temporary "search" node
        JsonScalarNode searchKey = new JsonScalarNode(0, 0, null, keyName, keyName, QuoteStyle.PLAIN);
        return getEntry(searchKey);
    }

    @Override
    public List<JsonMapEntryNode> getEntries() {
        return entries;
    }

    @Override
    public Set<JsonNode> keySet() {
        return entries.stream().map(JsonMapEntryNode::getKey).collect(Collectors.toSet());
    }

    @Override
    public Set<JsonMapEntryNode> entrySet() {
        return new HashSet<JsonMapEntryNode>(entries);
    }

    @Override
    public JsonMapNode put(JsonNode key, JsonNode value) {
        for (JsonMapEntryNode entry : entries) {
            if (entry.getKey().equals(key)) {
                entry.setValue(value);
                return this;
            }
        }
        entries.add(new JsonMapEntryNode(key.getStartLine(), key.getStartColumn(), getOriginUri(), key, value));
        return this;
    }

    @Override
    public void remove(JsonNode key) {
        entries.removeIf(e -> e.getKey().equals(key));
    }

    @Override
    public void remove(String key) {
        entries.removeIf(e -> {
            if (e.getKey() instanceof JsonScalarNode scalar) {
                return scalar.getString().equals(key);
            }
            return false;
        });
    }

    // --- Convenience Accessors ---

    @Override
    public JsonNode get(String key) {
        if (key == null) return null;
        for (JsonMapEntryNode entry : entries) {
            JsonNode kNode = entry.getKey();
            String entryKey = (kNode instanceof JsonScalarNode scalar) ? scalar.getString() : kNode.toString();
            if (key.equals(entryKey)) return entry.getValue();
        }
        return null;
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
        for (JsonMapEntryNode entry : entries) {
            if (entry.getKey() instanceof JsonScalarNode scalar && key.equals(scalar.getString())) {
                entry.setValue(value);
                return this;
            }
        }
        JsonScalarNode keyNode = new JsonScalarNode(0, 0, getOriginUri(), key, key, QuoteStyle.DOUBLE);
        entries.add(new JsonMapEntryNode(0, 0, getOriginUri(), keyNode, value));
        return this;
    }

    public boolean containsKey(String key) {
        for (JsonMapEntryNode entry : entries) {
            if (entry.getKey() instanceof JsonScalarNode scalar && key.equals(scalar.getString())) {
                return true;
            }
        }
        return false;
    }

    /// {@inheritDoc}
    @Override
    public Collection<JsonNode> values() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'values'");
    }

    /// {@inheritDoc}
    @Override
    public JsonNode put(String key, String value) {
        return put(key, new JsonScalarNode(value));
    }
}