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

public class JsonObject extends JsonNode implements MapAstNode<String, JsonNode, JsonProperty> {
    private final LinkedHashMap<String, JsonProperty> entriesByKey = new LinkedHashMap<>();
    private final JsonOptions options;

    public JsonObject() {
        super();
        this.options = JsonOptions.STRICT;
    }

    public JsonObject(int line, int column) {
        super(line, column);
        this.options = JsonOptions.STRICT;
    }

    public JsonObject(JsonOptions options) {
        super();
        this.options = options;
    }

    public JsonObject(int line, int column, JsonOptions options) {
        super(line, column);
        this.options = options;
    }

    @Override
    public List<JsonProperty> getChildren() {
        return List.copyOf(entriesByKey.values());
    }

    @Nullable
    @Override
    public JsonProperty getEntry(Object key) {
        if (key == null) return null;
        return entriesByKey.get(key);
    }

    @Nullable
    @Override
    public JsonProperty getEntry(int i) {
        if (i < 0 || i > size()) throw new NoSuchElementException();
        return entriesByKey.sequencedValues().toArray(new JsonProperty[]{})[i];
    }

    @Override
    public List<JsonProperty> getEntries() {
        return List.copyOf(entriesByKey.values());
    }

    @Override
    public Set<String> keySet() {
        Set<String> keys = new LinkedHashSet<>();
        for (JsonProperty entry : entriesByKey.values()) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    @Override
    public Set<JsonProperty> entrySet() {
        return new LinkedHashSet<>(entriesByKey.values());
    }

    @Override
    public JsonObject remove(String key) {
        entriesByKey.remove(key);
        return this;
    }

    @Override
    public JsonNode get(Object key) {
        JsonProperty entry = entriesByKey.get(key);
        return entry == null ? null : entry.getValue();
    }

    @Override
    public JsonObject getMap(Object key) {
        JsonNode node = this.get(key);
        return (node instanceof JsonObject map) ? map : new JsonObject();
    }

    @Override
    public JsonArray getSequence(Object key) {
        JsonNode node = this.get(key);
        return (node instanceof JsonArray seq) ? seq : new JsonArray();
    }

    @Override
    @Nullable
    public JsonScalar getScalar(Object key) {
        if (get(key) instanceof JsonScalar scalar) {
            return scalar;
        }
        return null;
    }

    public JsonObject getObject(String key) {
        return getMap(key);
    }

    public JsonArray getArray(String key) {
        return getArray(key);
    }

    @Override
    public JsonObject put(String key, JsonNode value) {
        JsonProperty existing = entriesByKey.get(key);
        if (existing != null) {
            // TODO: Should the key's comments be removed?
            // This needs to be in the javadoc.
            existing.setRaw(value);
            return this;
        }

        JsonProperty entry = new JsonProperty(0, 0, key, value);
        entriesByKey.put(key, entry);
        return this;
    }

    public JsonObject put(JsonProperty entry) {
        String key = entry.getKey();
        JsonProperty existing = entriesByKey.get(key);

        if (existing != null) {
            existing.setRaw(entry.getValue());
            // Optionally merge comments:
            existing.getComments().addAll(entry.getComments());
            return this;
        }

        entriesByKey.put(key, entry);
        return this;
    }

    public boolean containsKey(Object key) {
        if (key instanceof String string) {
            return entriesByKey.containsKey(string);
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
    public JsonObject put(String key, String value) {
        return put(key, new JsonScalar(value, options));
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
    public Iterator<JsonProperty> iterator() {
        return entriesByKey.sequencedValues().iterator();
    }

    /// {@inheritDoc}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonObject that)) return false;
        return Objects.equals(this.entriesByKey, that.entriesByKey);
    }

    /// {@inheritDoc}
    @Override
    public int hashCode() {
        return Objects.hash(entriesByKey);
    }
}