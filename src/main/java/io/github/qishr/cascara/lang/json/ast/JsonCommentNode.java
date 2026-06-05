package io.github.qishr.cascara.lang.json.ast;

import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;

import java.net.URI;
import java.util.List;

public class JsonCommentNode extends JsonNode implements ScalarAstNode<JsonNode>, CommentAstNode {
    private String value;
    private String rawValue;
    private final boolean multiLine;

    public JsonCommentNode(int line, int column, URI uri, String rawValue, String stringValue, boolean multiLine) {
        this.value = stringValue;
        this.rawValue = rawValue;
        this.multiLine = multiLine;
    }

    // @Override
    public String getContent() {
        return value != null ? value.toString() : null;
    }

    @Override
    public void setQuoteStyle(QuoteStyle style) {

    }

    @Override
    public QuoteStyle getQuoteStyle() {
        return null;
    }

    @Override
    public List<JsonNode> getChildren() {
        return List.of(); // Scalars never have children
    }

    @Override
    public List<CommentAstNode> getComments() {
        return List.of();
    }

    /// Returns the original raw string as seen in the source file.
    public String getRaw() {
        return (rawValue != null) ? rawValue : value;
    }

    @Override
    public int asInteger() {
        return 0; //TODO:  cascara://projman/CASC-00027711
    }

    @Override
    public int asInteger(int defaultValue) {
        return 0;
    }

    @Override
    public double asDouble() {
        return 0;
    }

    @Override
    public double asDouble(double defaultValue) {
        return 0;
    }

    @Override
    public boolean asBoolean() {
        return false;
    }

    @Override
    public boolean asBoolean(boolean defaultValue) {
        return false;
    }

    @Override
    public Object getPrimitive() {
        return null;
    }

    @Override
    public void setPrimitive(Object value) {
        this.value = String.valueOf(value);
    }

    @Override
    public String asString() {
        return value;
    }

    @Override
    public boolean isMultiLine() {
        return multiLine;
    }
}
