package io.github.qishr.cascara.lang.json.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapEntryNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;

public class JsonPrettyPrinter {
    private final Writer writer;
    private int indentLevel = 0;
    private final String indentString = "  ";

    public JsonPrettyPrinter(Writer writer) {
        this.writer = writer;
    }

    public void print(JsonNode node) throws IOException {
        // 1. Print any comments attached to this node
        for (CommentAstNode comment : node.getComments()) {
            writeIndent();
            writer.write(comment.getRaw()); // Raw value includes # or //
            writer.write("\n");
        }

        // 2. Dispatch based on the Cascara AST interfaces
        if (node instanceof JsonMapNode map) {
            printMap(map);
        } else if (node instanceof JsonSequenceNode seq) {
            printSequence(seq);
        } else if (node instanceof JsonScalarNode scalar) {
            // ScalarAstNode provides getRaw() to preserve quotes/formatting
            writer.write(scalar.getRaw());
        }
    }

    private void printMap(JsonMapNode map) throws IOException {
        writer.write("{\n");
        indentLevel++;

        // API doc: keys() returns Set<K> (which are AstNodes/JsonNodes)
        var keys = new ArrayList<>(map.keySet());
        for (int i = 0; i < keys.size(); i++) {
            JsonNode keyNode = (JsonNode) keys.get(i);
            // API doc: getEntry takes the Key node, not a String
            JsonMapEntryNode entry = (JsonMapEntryNode) map.getEntry(keyNode);

            writeIndent();
            print(entry.getKey());
            writer.write(": ");
            print(entry.getValue());

            if (i < keys.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }

        indentLevel--;
        writeIndent();
        writer.write("}");
    }

    private void printSequence(JsonSequenceNode seq) throws IOException {
        writer.write("[\n");
        indentLevel++;

        // API doc: items() returns Iterable<? extends AstNode>
        // We track index to handle commas
        int total = seq.size();
        int current = 0;
        for (JsonNode item : seq) {
            writeIndent();
            print((JsonNode) item);

            current++;
            if (current < total) {
                writer.write(",");
            }
            writer.write("\n");
        }

        indentLevel--;
        writeIndent();
        writer.write("]");
    }

    private void writeIndent() throws IOException {
        for (int i = 0; i < indentLevel; i++) {
            writer.write(indentString);
        }
    }
}