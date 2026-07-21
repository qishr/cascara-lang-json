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


package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.processor.Emitter;
import io.github.qishr.cascara.lang.json.ast.JsonMapEntryNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;
import io.github.qishr.cascara.lang.json.util.JsonOptions;

public class JsonEmitter extends AbstractJsonProcessor<JsonEmitter>  implements Emitter {
    private final StringBuilder output = new StringBuilder();
    private int indentLevel = 0;

    // TODO: I suspect the emitter always assumes prettyPrint is on

    @Override protected JsonEmitter self() { return this; }

    @Override
    public ContentType getContentType() {
        return JsonAstParser.JSON_CONTENT_TYPE;
    }

    public String emit(JsonNode root) {
        output.setLength(0);
        emitNode(root);
        return output.toString();
    }

    @Override
    public void emitScalar(String value) {
        output.append(value);
    }

    private void emitNode(JsonNode node) {
        if (node == null) return;
        // Handle Comments before the node
        if (node.getComments() != null) {
            for (var comment : node.getComments()) {
                emitScalar(comment.getLexeme());
                emitNewLine();
            }
        }

        if (node instanceof JsonScalarNode scalar) {
            emitScalar(formatScalar(scalar));
        } else if (node instanceof JsonMapNode map) {
            emitMapStart();
            var entries = map.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                JsonMapEntryNode entry = (JsonMapEntryNode) entries.get(i);



                //
                // emitNode(entry.getKey());
                emitScalar(formatKey(entry.getKey()));



                emitPropertySeparator();
                emitNode(entry.getValue());

                if (i < entries.size() - 1) {
                    emitItemSeparator();
                }
            }
            emitMapEnd();
        } else if (node instanceof JsonSequenceNode seq) {
            emitSequenceStart();
            int total = seq.size();
            int index = 0;
            for (JsonNode item : seq) {
                emitNode(item);

                if (++index < total) {
                    emitItemSeparator();
                }
            }
            emitSequenceEnd();
        }
    }

    private String formatScalar(JsonScalarNode scalar) {

        // asString returns the unescaped string content without quotes
        String value = scalar.asString();
        if (value == null) return "null";

        return switch (scalar.getQuoteStyle()) {

            // TODO: It goes wrong here:
            // I think here we want the lexeme form, not the content form

            case DOUBLE -> "\"" + escapeJson(value) + "\"";
            case SINGLE -> "'" + escapeJson(value) + "'";
            case LITERAL_BLOCK, FOLDED -> value; // Usually used for multi-line or raw blocks
            case PLAIN -> value; // For numbers, booleans, or unquoted keys
            default -> value;
        };
    }

    private String formatKey(String value) {
        if (value == null) return "null";
        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    @Override
    public void emitMapStart() {
        output.append("{");
        indent();
        emitNewLine();
    }

    @Override
    public void emitMapEnd() {
        dedent();
        emitNewLine();
        output.append("}");
    }

    @Override
    public void emitSequenceStart() {
        output.append("[");
        indent();
        emitNewLine();
    }

    @Override
    public void emitSequenceEnd() {
        dedent();
        emitNewLine();
        output.append("]");
    }

    @Override
    public void emitPropertySeparator() {
        output.append(":");
        if (options.insertSpaces()) {
            output.append(" ");
        }
    }

    @Override
    public void emitItemSeparator() {
        output.append(",");
        emitNewLine();
    }

    @Override
    public void emitNewLine() {
        output.append("\n");
        writePadding();
    }

    @Override
    public void indent() {
        indentLevel++;
    }

    @Override
    public void dedent() {
        if (indentLevel > 0) {
            indentLevel--;
        }
    }

    @Override
    public String getOutput() {
        return output.toString();
    }

    @Override
    public JsonEmitter setOptions(LanguageOptions<?> options) {
        if (options instanceof JsonOptions jsonOptions) {
            this.options = jsonOptions;
        }
        return this;
    }

    @Override
    public JsonEmitter setReporter(Reporter reporter) {
        this.reporter = reporter;
        return this;
    }

    private void writePadding() {
        int spaceCount = indentLevel * options.getIndentSize();
        if (options.insertSpaces()) {
            output.append(" ".repeat(spaceCount));
        } else {
            output.append("\t".repeat(indentLevel));
        }
    }
}