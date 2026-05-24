package io.github.qishr.cascara.lang.json.ast;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;

public class JsonScalarNode extends JsonNode implements ScalarAstNode<JsonNode> {
    private String value;
    private String rawValue;
    private QuoteStyle quoteStyle = QuoteStyle.PLAIN;

    public JsonScalarNode(int line, int column, URI uri, String rawValue, String stringValue, QuoteStyle quoteStyle) {
        super(line, column, uri);
        this.rawValue = rawValue;
        this.value = stringValue;
        this.quoteStyle = quoteStyle;
    }

    public JsonScalarNode(String stringValue) {
        super(0, 0, null);
        this.value = stringValue;
    }

    public JsonScalarNode() {
        super(0, 0, null);
        this.value = null;
    }

    @Override public String getString() { return value; }
    @Override public String getRawValue() { return value; }

    @Override
    public void setValue(String newValue) {
        this.value = newValue;
        this.rawValue = null; // Mark as dirty
    }

    @Override
    public int getInteger() { return getInteger(0); }

    @Override
    public int getInteger(int defaultValue) {
        try { return value == null ? defaultValue : Integer.parseInt(value); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    @Override
    public double getDouble() { return getDouble(0.0); }

    @Override
    public double getDouble(double defaultValue) {
        try { return value == null ? defaultValue : Double.parseDouble(value); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    @Override
    public boolean getBoolean() { return getBoolean(false); }

    @Override
    public boolean getBoolean(boolean defaultValue) {
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    @Override
    public Object getPrimitiveValue() {
        if (value == null || value.equals("null")) return null;
        if (isQuoted()) return value; // Quoted values are always strings

        // Boolean check
        if (value.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (value.equalsIgnoreCase("false")) return Boolean.FALSE;

        // Number checks
        try { return Integer.parseInt(value); } catch (NumberFormatException e) {}
        try { return Double.parseDouble(value); } catch (NumberFormatException e) {}

        return value;
    }

    @Override
    public void setPrimitiveValue(Object value) {
        this.value = String.valueOf(value);
    }

    public boolean isQuoted() { return quoteStyle != QuoteStyle.PLAIN; }
    @Override public QuoteStyle getQuoteStyle() { return quoteStyle; }
    @Override public void setQuoteStyle(QuoteStyle style) { this.quoteStyle = style; }

    @Override public List<JsonNode> getChildren() { return List.of(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonScalarNode that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    /// {@inheritDoc}
    @Override
    public String toString() {
        return value;
    }

}