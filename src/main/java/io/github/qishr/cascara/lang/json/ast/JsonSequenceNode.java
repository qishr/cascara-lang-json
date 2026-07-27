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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;

public class JsonSequenceNode extends JsonNode implements SequenceAstNode<JsonNode> {
    private final List<JsonNode> elements = new ArrayList<>();

    public JsonSequenceNode() { super(); }
    public JsonSequenceNode(int line, int column) { super(line, column); }

    @Override public JsonSequenceNode add(JsonNode item) { elements.add(item); return this; }

    @Override
    public JsonSequenceNode remove(int index) {
        if (index >= 0 && index < elements.size()) {
            elements.remove(index);
        }
        return this;
    }

    @Override public JsonSequenceNode clear() { elements.clear(); return this; }
    @Override public int size() { return elements.size(); }
    @Override public JsonNode get(int index) { return elements.get(index); }
    @Override public List<JsonNode> getElements() { return elements; }
    @Override public List<JsonNode> getChildren() { return elements; }

    @Override
    public JsonNode getFirst() {
        if (elements.isEmpty()) {
            throw new NoSuchElementException();
        }
        return elements.getFirst();
    }

    @Override
    public JsonNode getLast() {
        if (elements.isEmpty()) {
            throw new NoSuchElementException();
        }
        return elements.getLast();
    }

    @Override
    public JsonSequenceNode remove(JsonNode node) {
        elements.remove(node);
        return this;
    }

    /// Returns Iterator instance
    @Override
    public Iterator<JsonNode> iterator() {
        return new SequenceIterator<JsonNode>(this);
    }

    static class SequenceIterator<T> implements Iterator<JsonNode> {
        JsonSequenceNode list;
        int currentIndex = 0;

        // initialize pointer to head of the list for iteration
        public SequenceIterator(JsonSequenceNode list) {
            this.list = list;
        }

        // returns false if next element does not exist
        public boolean hasNext() {
            return currentIndex < list.size();
        }

        // return current data and update pointer
        public JsonNode next() {
            JsonNode data = list.get(currentIndex++);
            return data;
        }

        // implement if needed
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isEmpty'");
    }

    /// {@inheritDoc}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonSequenceNode that)) return false;
        return Objects.equals(this.elements, that.elements);
    }

    /// {@inheritDoc}
    @Override
    public int hashCode() {
        return Objects.hash(elements);
    }
}



