package io.github.qishr.cascara.lang.json.ast;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class JsonMapNode extends JsonNode implements MapAstNode<JsonNode, JsonMapEntryNode> {
    private final LinkedHashMap<String, JsonMapEntryNode> entriesByKey = new LinkedHashMap<>();

    public JsonMapNode() { super(); }
    public JsonMapNode(int line, int column) { super(line, column); }

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
        return List.copyOf(entriesByKey.values());
    }

    @Override
    @Nullable
    public JsonMapEntryNode getEntry(JsonNode key) {
        if (key == null) return null;
        String lookup = (key instanceof JsonScalarNode s)
            ? s.getContent()
            : key.toString();
        return entriesByKey.get(lookup);
    }


    /// Convenience method for internal use and testing.
    /// Not part of the MapAstNode interface.
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
    public Set<JsonNode> keySet() {
        Set<JsonNode> keys = new LinkedHashSet<>();
        for (JsonMapEntryNode entry : entriesByKey.values()) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    @Override
    public Set<JsonMapEntryNode> entrySet() {
        return new LinkedHashSet<>(entriesByKey.values());
    }

    public JsonMapNode put(JsonScalarNode keyNode, JsonNode value) {
        String key = keyNode.getContent();
        JsonMapEntryNode entry = entriesByKey.get(key);
        if (entry == null) {
            entry = new JsonMapEntryNode(0, 0, keyNode, value);
            entriesByKey.put(key, entry);
        } else {
            entry.setRaw(value);
        }
        return this;
    }

    @Override
    public JsonMapNode remove(JsonNode key) {
        JsonMapEntryNode entry = getEntry(key);
        if (entry != null) {
            String lookup;
            JsonNode kNode = entry.getKey();
            if (kNode instanceof JsonScalarNode scalar) {
                lookup = scalar.asString();
            } else {
                lookup = kNode.toString();
            }
            entriesByKey.remove(lookup);
        }
        return this;
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
    public JsonMapNode put(JsonNode key, JsonNode value) {
        JsonMapEntryNode entry = getEntry(key);
        if (entry == null) {
            entry = new JsonMapEntryNode(0, 0, key, value);
            entriesByKey.put(key.asString(), entry);
            return this;
        }
        entry.setRaw(value);
        return this;
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

        // TODO: Where does JsonPrimitiveDesciptor come from?
        // Since we're passing null in as the descriptor, the node needs to be able to handle all options? or only the default ones?
        JsonNode keyNode = new JsonScalarNode(0, 0, key, key, QuoteStyle.PLAIN, null, true);
        JsonMapEntryNode entry = new JsonMapEntryNode(0, 0, keyNode, value);
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
        return put(key, new JsonScalarNode(value));
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