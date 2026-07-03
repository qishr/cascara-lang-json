package io.github.qishr.cascara.lang.json.ast;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SequencedCollection;
import java.util.SequencedSet;
import java.util.Set;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode.SequenceIterator;

public class JsonMapNode extends JsonNode implements MapAstNode<JsonNode, JsonMapEntryNode> {
    private final LinkedHashMap<JsonNode,JsonMapEntryNode> entriesByKey = new LinkedHashMap<>();

    // private Set<JsonMapEntryNode> cachedEntrySet;

    // private final LinkedHashMap<String,JsonMapEntryNode> entriesByStringKey = new LinkedHashMap<>();
    // TODO:
    // Doing the above would double the memory usage:
    // Everywhere entriesByKey is updated, updated entriesByStringKey too.
    // Idea: hash map with dual keys: string and JsonNode
    // This is touched on here: https://www.baeldung.com/java-multiple-keys-map
    // But their implementations sound rather slow.

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
        return entriesByKey.get(key);
    }

    /// Convenience method for internal use and testing.
    /// Not part of the MapAstNode interface.
    @Nullable
    public JsonMapEntryNode getEntry(String key) {
        if (key == null) return null;

        // TODO: (see comment at top)
        // return entriesByStringKey.get(key);

        for (Entry<JsonNode, JsonMapEntryNode> entry : entriesByKey.entrySet()) {
            if (entry.getKey() instanceof JsonScalarNode scalar) {
                if (key.equals(scalar.asString())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    @Override
    public List<JsonMapEntryNode> getEntries() {
        return List.copyOf(entriesByKey.values());
    }

    @Override
    public Set<JsonNode> keySet() {
        return entriesByKey.keySet();
    }

    @Override
    public Set<JsonMapEntryNode> entrySet() {
        // var values = entriesByKey.values();
        // we need an efficient way to return Set<JsonMapEntryNode>.
        // values's type is LinkedHashMap$LinkedValues.
        // maybe entriesByKey being LinkedHashMap isn't the way to go.
        return new HashSet<JsonMapEntryNode>(entriesByKey.values());
    }

    // @Override
    // public Set<JsonMapEntryNode> entrySet() {
    //     if (cachedEntrySet == null) {
    //         cachedEntrySet = new AbstractSet<JsonMapEntryNode>() {
    //             @Override
    //             public Iterator<JsonMapEntryNode> iterator() {
    //                 // This delegates directly to the highly optimized LinkedHashMap values iterator!
    //                 // Zero copying, zero temporary array/hash tables created.
    //                 return entriesByKey.values().iterator();
    //             }
    //             @Override
    //             public int size() {
    //                 return entriesByKey.size();
    //             }
    //             @Override
    //             public boolean contains(Object o) {
    //                 if (!(o instanceof JsonMapEntryNode)) return false;
    //                 JsonMapEntryNode entry = (JsonMapEntryNode) o;
    //                 JsonMapEntryNode match = entriesByKey.get(entry.getKey());
    //                 return match != null && match.equals(entry);
    //             }
    //         };
    //     }
    //     return cachedEntrySet;
    // }

    @Override
    public JsonMapNode put(JsonNode key, JsonNode value) {
        JsonMapEntryNode entry = getEntry(key);
        if (entry == null) {
            entry = new JsonMapEntryNode(0, 0, key, value);
            entriesByKey.put(key, entry);
            return this;
        }
        entry.setRaw(value);
        return this;
    }

    @Override
    public JsonMapNode remove(JsonNode key) {
        entriesByKey.remove(key);
        return this;
    }

    @Override
    public JsonMapNode remove(String key) {
        for (Map.Entry<JsonNode,JsonMapEntryNode> entry : entriesByKey.entrySet()) {
            if (entry.getKey() instanceof JsonScalarNode scalar) {
                if (scalar.asString().equals(key)) {
                    entriesByKey.remove(scalar);
                    return this;
                }
            }
        }
        return this;
    }

    // --- Convenience Accessors ---

    @Override
    public JsonNode get(String key) {
        if (key == null) return null;

        for (Map.Entry<JsonNode,JsonMapEntryNode> entry : entriesByKey.entrySet()) {
            JsonMapEntryNode entryNode = entry.getValue();

            JsonNode kNode = entryNode.getKey();
            String entryKey = null;
            if (kNode instanceof JsonScalarNode scalar) {
                entryKey = scalar.asString();
            } else {
                entryKey = kNode.toString();
            }

            if (key.equals(entryKey)) {
                JsonNode val = entryNode.getValue();
                // return (val instanceof JsonAnchorNode a) ? a.getInnerNode() : val;
                return val;
            }
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
        for (JsonMapEntryNode entry : entriesByKey.values()) {
            JsonNode kNode = entry.getKey();
            // Check if the existing key's string value matches the requested key
            if (kNode instanceof JsonScalarNode scalar && key.equals(scalar.asString())) {
                entry.setRaw(value);
                return this;
            }
        }

        // Only if not found, create the new entry
        JsonNode keyNode = new JsonScalarNode(0, 0, key, key, QuoteStyle.PLAIN);
        JsonMapEntryNode entry = new JsonMapEntryNode(0, 0, keyNode, value);
        entriesByKey.put(entry.getKey(), entry);
        return this;
    }

    public boolean containsKey(String key) {
        for (JsonNode keyNode : entriesByKey.keySet()) {
            if (keyNode instanceof JsonScalarNode scalar && key.equals(scalar.asString())) {
                return true;
            }
        }
        return false;
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