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
import io.github.qishr.cascara.lang.json.ast.JsonProperty;
import io.github.qishr.cascara.lang.json.ast.JsonObject;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalar;
import io.github.qishr.cascara.lang.json.ast.JsonArray;
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

        if (node instanceof JsonScalar scalar) {
            emitScalar(formatScalar(scalar));
        } else if (node instanceof JsonObject map) {
            emitMapStart();
            var entries = map.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                JsonProperty entry = (JsonProperty) entries.get(i);

                emitScalar(formatKey(entry.getKey()));
                emitPropertySeparator();
                emitNode(entry.getValue());

                if (i < entries.size() - 1) {
                    emitItemSeparator();
                }
            }
            emitMapEnd();
        } else if (node instanceof JsonArray seq) {
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

    private String formatScalar(JsonScalar scalar) {

        // asString returns the unescaped string content without quotes
        String value = scalar.asString();
        if (value == null) return "null";

        return switch (scalar.getQuoteStyle()) {
            case DOUBLE -> "\"" + escapeJson(value) + "\"";
            case SINGLE -> "'" + escapeJson(value) + "'";
            case PLAIN -> value; // For numbers, booleans, or unquoted keys
            default -> value;
        };
    }

    private String formatKey(String value) {
        if (value == null) return "null";
        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
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
        // if (options.insertSpaces()) {
        //     output.append(" ");
        // }
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