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


package io.github.qishr.cascara.lang.json.util;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.lang.json.ast.JsonProperty;
import io.github.qishr.cascara.lang.json.ast.JsonObject;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalar;
import io.github.qishr.cascara.lang.json.ast.JsonArray;

public class JsonPrettyPrinter {
    private final Writer writer;
    private int indentLevel = 0;
    private final String indentString = "  ";

    public JsonPrettyPrinter(Writer writer) {
        this.writer = writer;
    }

    public void print(String key) throws IOException {
        writer.write(key);
    }

    public void print(JsonNode node) throws IOException {
        // 1. Print any comments attached to this node
        for (CommentAstNode comment : node.getComments()) {
            writeIndent();
            writer.write(comment.getLexeme()); // Raw value includes # or //
            writer.write("\n");
        }

        // 2. Dispatch based on the Cascara AST interfaces
        if (node instanceof JsonObject map) {
            printMap(map);
        } else if (node instanceof JsonArray seq) {
            printSequence(seq);
        } else if (node instanceof JsonScalar scalar) {
            // ScalarAstNode provides getLexeme() to preserve quotes/formatting
            writer.write(scalar.getLexeme());
        }
    }

    private void printMap(JsonObject map) throws IOException {
        writer.write("{\n");
        indentLevel++;

        // API doc: keys() returns Set<K> (which are AstNodes/JsonNodes)
        var keys = new ArrayList<>(map.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            // API doc: getEntry takes the Key node, not a String
            JsonProperty entry = (JsonProperty) map.getEntry(key);

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

    private void printSequence(JsonArray seq) throws IOException {
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