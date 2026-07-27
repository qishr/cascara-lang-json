// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.json.ast;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
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
    @Override
    public JsonMapEntryNode getEntry(String key) {
        if (key == null) return null;
        return entriesByKey.get(key);
    }

    @Nullable
    @Override
    public JsonMapEntryNode getEntry(int i) {
        if (i < 0 || i > size()) throw new NoSuchElementException();
        return entriesByKey.sequencedValues().toArray(new JsonMapEntryNode[]{})[i];
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
    @Nullable
    public JsonScalarNode getScalar(String key) {
        if (get(key) instanceof JsonScalarNode scalar) {
            return scalar;
        }
        return null;
    }

    @Override
    public JsonMapNode put(String key, JsonNode value) {
        JsonMapEntryNode existing = entriesByKey.get(key);
        if (existing != null) {
            // TODO: Should the key's comments be removed?
            // This needs to be in the javadoc.
            existing.setRaw(value);
            return this;
        }

        JsonMapEntryNode entry = new JsonMapEntryNode(0, 0, key, value);
        entriesByKey.put(key, entry);
        return this;
    }

    public JsonMapNode put(JsonMapEntryNode entry) {
        String key = entry.getKey();
        JsonMapEntryNode existing = entriesByKey.get(key);

        if (existing != null) {
            existing.setRaw(entry.getValue());
            // Optionally merge comments:
            existing.getComments().addAll(entry.getComments());
            return this;
        }

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

    /// {@inheritDoc}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonMapNode that)) return false;
        return Objects.equals(this.entriesByKey, that.entriesByKey);
    }

    /// {@inheritDoc}
    @Override
    public int hashCode() {
        return Objects.hash(entriesByKey);
    }
}