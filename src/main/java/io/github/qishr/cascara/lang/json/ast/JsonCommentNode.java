package io.github.qishr.cascara.lang.json.ast;

import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;

import java.net.URI;
import java.util.List;

public class JsonCommentNode extends JsonNode implements ScalarAstNode<JsonNode>, CommentAstNode {
    private String value;
    private String rawValue;
    private QuoteStyle quoteStyle;
    private final boolean multiLine;

    // public JsonCommentNode() {}

    public JsonCommentNode(int line, int column, URI uri, String rawValue, String stringValue, boolean multiLine) {
        this.value = stringValue;
        this.rawValue = rawValue;
        this.multiLine = multiLine;
    }

    // public JsonCommentNode(int line, int column, URI uri, String rawValue, String stringValue, QuoteStyle quoteStyle) {
    //     super(line, column, uri);
    //     this.rawValue = rawValue;
    //     this.value = stringValue;
    //     this.quoteStyle = quoteStyle;
    //     this.multiLine = false;
    // }

    // public JsonCommentNode(String stringValue) {
    //     this.value = stringValue;
    //     this.rawValue = stringValue;
    // }

    //    @Override
    public String getValue() {
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
    public void setValue(String value) {
        // Here you can add logic to try and parse the string back
        // into a Boolean or Double if your AST requires typed primitives
        this.value = value;
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
    public String getRawValue() {
        return (rawValue != null) ? rawValue : value;
    }


    // @Override
    // public String getString() {
    //     return rawValue;
    // }

    @Override
    public int getInteger() {
        return 0; //TODO:  cascara://projman/CASC-00027711
    }

    @Override
    public int getInteger(int defaultValue) {
        return 0;
    }

    @Override
    public double getDouble() {
        return 0;
    }

    @Override
    public double getDouble(double defaultValue) {
        return 0;
    }

    @Override
    public boolean getBoolean() {
        return false;
    }

    @Override
    public boolean getBoolean(boolean defaultValue) {
        return false;
    }

    @Override
    public Object getPrimitiveValue() {
        return null;
    }

    @Override
    public void setPrimitiveValue(Object value) {
        this.value = String.valueOf(value);
    }

    @Override
    public String getString() {
        return value;
    }

    @Override
    public boolean isMultiLine() {
        return multiLine;
    }
}
