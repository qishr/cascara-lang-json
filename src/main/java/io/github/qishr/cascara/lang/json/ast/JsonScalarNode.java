package io.github.qishr.cascara.lang.json.ast;

import java.util.List;
import java.util.Objects;

import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.type.PrimitiveDescriptor;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
import io.github.qishr.cascara.lang.json.JsonPrimitiveDescriptor;


public class JsonScalarNode extends JsonNode implements ScalarAstNode<JsonNode> {

    private String raw;
    private String content;
    private final PrimitiveDescriptor descriptor;
    private QuoteStyle quoteStyle = QuoteStyle.PLAIN;
    private String keyStringCache;

    // dialect-aware native value cache
    private Object nativeValue;
    private boolean nativeValueCached;

    private String stringValue;
    private boolean stringValueCached;

    // key/value context
    private final boolean isKey;

    /// Constructor for use in parsers.
    /// Used when reading raw text from a file stream.
    /// Takes a String and triggers full lexical dialect type inference.
    public JsonScalarNode(
        int line,
        int column,
        String raw,
        String unescapedContent,
        QuoteStyle quoteStyle,
        PrimitiveDescriptor descriptor,
        boolean isKey
    ) {
        super(line, column);
        this.raw = raw;
        this.content = unescapedContent; // tokenizer already unescaped
        this.quoteStyle = quoteStyle;
        this.descriptor = descriptor;
        this.isKey = isKey;
        // System.out.println("** TOKEN UNESCAPED: " + unescapedContent);
    }

    public JsonScalarNode(
        Object primitiveValue,
        PrimitiveType type,
        QuoteStyle quoteStyle,
        JsonPrimitiveDescriptor descriptor
    ) {
        super(0, 0);
        this.raw = null;
        this.content = null;
        this.quoteStyle = quoteStyle;
        this.descriptor = descriptor;
        this.isKey = false;
        this.nativeValue = primitiveValue;
        this.nativeValueCached = true;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public JsonScalarNode(Object primitiveValue, QuoteStyle quoteStyle) {
        super(0, 0);
        this.raw = null;
        this.content = null;
        this.quoteStyle = quoteStyle;
        this.descriptor = null;
        this.isKey = false;
        this.nativeValue = primitiveValue;
        this.nativeValueCached = true;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public JsonScalarNode(Object primitiveValue) {
        this(primitiveValue, false);
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public JsonScalarNode(Object primitiveValue, boolean isKey) {
        super(0, 0);
        this.raw = null;
        this.content = null;
        this.quoteStyle = isKey ? QuoteStyle.DOUBLE : QuoteStyle.PLAIN;
        this.descriptor = null;
        this.isKey = isKey;
        this.nativeValue = primitiveValue;
        this.nativeValueCached = true;
    }

    /// The default constructor
    public JsonScalarNode() {
        super(0, 0);
        this.raw = null;
        this.content = null;
        this.quoteStyle = QuoteStyle.PLAIN;
        this.descriptor = null;
        this.isKey = false;
    }

    @Override
    public List<JsonNode> getChildren() {
        return List.of();
    }

    @Override
    public QuoteStyle getQuoteStyle() {
        return quoteStyle;
    }

    @Override
    public JsonScalarNode setQuoteStyle(QuoteStyle style) {
        this.quoteStyle = style;
        this.keyStringCache = null;
        return this;
    }

    public String getKeyString() {
        if (keyStringCache != null) return keyStringCache;

        if (descriptor != null) {
            // parser-constructed node: use descriptor + raw/content
            keyStringCache = descriptor.unescape(
                content != null ? content : raw,
                quoteStyle,
                true
            );
        } else if (content != null) {
            // descriptor-less node with lexical content
            keyStringCache = content;
        } else {
            // pure programmatic node: fall back to nativeValue
            Object v = nativeValue;
            keyStringCache = (v == null) ? "" : String.valueOf(v);
        }

        return keyStringCache;
    }

    @Override
    public String getRaw() {
        return raw;
    }

    @Override
    public String getContent() {
        return content;
    }

    /// Returns the dialect-aware JVM value (cached).
    @Override
    public Object getPrimitive() {
        if (nativeValueCached) {
            return nativeValue;
        }

        if (descriptor == null) {
            nativeValue = content;
            nativeValueCached = true;
            return nativeValue;
        }

        // TODO: Surely if we're looking for `content`, using `raw` is incorrect?

        // Use content, which is already de-quoted and unescaped by the tokenizer
        String source = (content != null) ? content : raw;

        // System.out.println("Calling parse with: " + source);

        nativeValue = descriptor.parse(source, quoteStyle, isKey);
        nativeValueCached = true;
        return nativeValue;
    }

    /// Returns the logical clean text value, stripped of outer formatting and escape markers.
    @Override
    public String asString() {
        if (stringValueCached) {
            return stringValue;
        }
        Object v = getPrimitive();
        stringValue = (v == null) ? null : String.valueOf(v);
        stringValueCached = true;
        return stringValue;
    }

    // @Override
    // public String asString() {
    //     Object v = getPrimitive();
    //     return (v == null) ? null : String.valueOf(v);
    // }

    @Override
    public int asInteger() {
        return asInteger(0);
    }

    @Override
    public int asInteger(int defaultValue) {
        Object v = getPrimitive();
        if (descriptor == null) {
            try {
                return Integer.parseInt(String.valueOf(v));
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return descriptor.toIntegerOrDefault(v, defaultValue);
    }

    @Override
    public double asDouble() {
        return asDouble(0.0);
    }

    @Override
    public double asDouble(double defaultValue) {
        Object v = getPrimitive();
        if (descriptor == null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return descriptor.toDoubleOrDefault(v, defaultValue);
    }

    @Override
    public boolean asBoolean() {
        return asBoolean(false);
    }

    @Override
    public boolean asBoolean(boolean defaultValue) {
        Object v = getPrimitive();
        if (descriptor == null) {
            if (v instanceof Boolean b) return b;
            if (v == null) return defaultValue;
            String s = String.valueOf(v);
            if ("true".equals(s)) return true;
            if ("false".equals(s)) return false;
            return defaultValue;
        }
        return descriptor.toBooleanOrDefault(v, defaultValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonScalarNode that)) return false;
        return Objects.equals(raw, that.raw)
            && Objects.equals(content, that.content)
            && Objects.equals(getPrimitive(), that.getPrimitive())
            && quoteStyle == that.quoteStyle
            && isKey == that.isKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw, content, getPrimitive(), quoteStyle, isKey);
    }

    /// {@inheritDoc}
    @Override
    public String toString() {
        return raw != null ? raw : String.valueOf(getPrimitive());
    }
}

