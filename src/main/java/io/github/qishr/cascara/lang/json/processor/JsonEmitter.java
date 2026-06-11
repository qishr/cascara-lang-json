package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.LanguageOptions;
import io.github.qishr.cascara.common.lang.processor.Emitter;
import io.github.qishr.cascara.lang.json.JsonOptions;
import io.github.qishr.cascara.lang.json.ast.JsonMapEntryNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;

public class JsonEmitter extends AbstractJsonProcessor<JsonEmitter>  implements Emitter {
    private final StringBuilder output = new StringBuilder();
    private int indentLevel = 0;

    @Override protected JsonEmitter self() { return this; }

    @Override
    public ContentType getContentType() {
        return JsonParser.contentType;
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
                emitScalar(comment.getRaw());
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

                emitNode(entry.getKey());
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
        String value = scalar.asString();
        if (value == null) return "null";

        return switch (scalar.getQuoteStyle()) {
            case DOUBLE -> "\"" + escapeJson(value) + "\"";
            case SINGLE -> "'" + escapeJson(value) + "'";
            case LITERAL_BLOCK, FOLDED -> value; // Usually used for multi-line or raw blocks
            case PLAIN -> value; // For numbers, booleans, or unquoted keys
            default -> value;
        };
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
        if (options.isInsertSpaces()) {
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
        if (options.isInsertSpaces()) {
            output.append(" ".repeat(spaceCount));
        } else {
            output.append("\t".repeat(indentLevel));
        }
    }
}