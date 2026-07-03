package io.github.qishr.cascara.lang.json.ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
}



