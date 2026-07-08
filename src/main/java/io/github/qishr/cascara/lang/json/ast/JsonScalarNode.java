package io.github.qishr.cascara.lang.json.ast;

import java.util.List;
import java.util.Objects;

import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.type.Primitive;
import io.github.qishr.cascara.lang.json.JsonPrimitiveDelegate;

public class JsonScalarNode extends JsonNode implements ScalarAstNode<JsonNode> {
    private static final JsonPrimitiveDelegate JSON_PRIMITIVE_DELEGATE = new JsonPrimitiveDelegate();

    // TODO: Perhaps instead of having a Primitive object here:
    // - Merge its functionality into JsonScalarNode (and other scalar nodes)
    // - Have a PrimitiveType enum
    //
    // At the point of JsonScalarNode creation we need to know:
    // - what type the scalar represents
    // - the preferred quote style
    //
    // At the point of use (eg in a converter or serializer) we need to know:
    // - required quote style, taking into account:
    //   - type
    //   - value
    //   - is it a key
    //
    // Other things that rely on what Primitive currently provides:
    // - ScalarDescriptor (TypeDescriptor)
    // - AbstractSerializer
    // - AstConverter
    // - Tables (sorting columns)
    //
    // What Primitive currently provides:
    // - takes unescaped text OR Object input
    // - retains original quote style
    // - determines:
    //   - primitive type
    //   - required quote style
    // - methods to convert to JVM types (String, Double, etc)
    // - unescaping
    // - unwrapping

    private String raw;
    private String content;
    private Primitive primitive;
    private QuoteStyle quoteStyle = QuoteStyle.PLAIN;
    private String keyStringCache;

    /// Constructor for use in parsers.
    /// Used when reading raw text from a file stream.
    /// Takes a String and triggers full lexical dialect type inference.
    public JsonScalarNode(int line, int column, String raw, String unescapedContent, QuoteStyle quoteStyle) {
        // TODO: This should not take quoteChar, or caller must ensure it matches the source exactly.
        super(line, column);
        this.raw = raw;
        // fromString treats the input as text content to be parsed
        this.primitive = Primitive.fromString(unescapedContent, quoteStyle)
            .setDelegate(JSON_PRIMITIVE_DELEGATE);
        this.content = unescapedContent;
        this.quoteStyle = quoteStyle;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public JsonScalarNode(Object primitiveValue, QuoteStyle quoteStyle) {
        super(0, 0);
        this.raw = null; // Cleared cache marks it as dirty for the emitter
        // Pass the object directly into the primitive wrapper
        this.primitive = Primitive.of(primitiveValue)
            .setQuoteStyle(quoteStyle);
        this.quoteStyle = quoteStyle;
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
        this.raw = null; // Cleared cache marks it as dirty for the emitter
        this.primitive = Primitive.of(primitiveValue)
            .setDelegate(JSON_PRIMITIVE_DELEGATE);
        this.quoteStyle = isKey ? QuoteStyle.DOUBLE : primitive.getQuoteStyle();
    }

    /// The default constructor
    public JsonScalarNode() {
        super(0, 0);
        this.raw = null;
        this.quoteStyle = QuoteStyle.PLAIN;
        this.primitive = Primitive.of(null)
            .setDelegate(JSON_PRIMITIVE_DELEGATE);
    }

    public static JsonScalarNode fromPrimitive(Primitive primitive) {
        JsonScalarNode node = new JsonScalarNode();
        node.raw = null; // Cleared cache marks it as dirty for the emitter
        node.primitive = primitive;
        node.primitive.setDelegate(JSON_PRIMITIVE_DELEGATE);
        node.quoteStyle = primitive.getQuoteStyle();
        return node;
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
        return this;
    }

    public String getKeyString() {
        if (keyStringCache != null) return keyStringCache;

        // For quoted keys, rawInput is already the unescaped content
        if (this.getQuoteStyle() == QuoteStyle.DOUBLE) {
            keyStringCache = content; // already unescaped by tokenizer
            return keyStringCache;
        }

        // For JSON5 unquoted keys, content is already correct
        keyStringCache = content;
        return keyStringCache;
    }

    @Override
    public String getRaw() {
        return raw;
    }

    public String getContent() {
        return content;
    }

    @Override
    public Object getPrimitive() {
        return primitive.unwrap();
    }

    // TODO: Why do we have setPrimitive?
    // Where is it used?
    // Should JsonScalarNode be immutable?
    @Override
    public JsonScalarNode setPrimitive(Object value) {
        this.primitive = Primitive.of(value)
            .setDelegate(JSON_PRIMITIVE_DELEGATE)
            .setQuoteStyle(this.quoteStyle);
        this.raw = null;
        keyStringCache = null;
        content = (value == null ? null : String.valueOf(value));
        return this;
    }

    /// Returns the logical clean text value, stripped of outer formatting and escape markers.
    @Override
    public String asString() {
        return primitive.asString();
    }

    @Override
    public int asInteger() {
        return asInteger(0);
    }

    @Override
    public int asInteger(int defaultValue) {
        return primitive.asInteger(defaultValue);
    }

    @Override
    public double asDouble() {
        return asDouble(0.0);
    }

    @Override
    public double asDouble(double defaultValue) {
        return primitive.asDouble(defaultValue);
    }

    @Override
    public boolean asBoolean() {
        return asBoolean(false);
    }

    @Override
    public boolean asBoolean(boolean defaultValue) {
        return primitive.asBoolean(defaultValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonScalarNode that)) return false;
        return Objects.equals(raw, that.raw) && Objects.equals(primitive, that.primitive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw, primitive);
    }

    /// {@inheritDoc}
    @Override
    public String toString() {
        return raw;
    }
}