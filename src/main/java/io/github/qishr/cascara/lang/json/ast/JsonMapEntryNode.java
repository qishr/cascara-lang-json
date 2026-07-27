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

import java.util.List;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonMapEntryNode other)) return false;
        return Objects.equals(value, other.value) &&
               key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

}