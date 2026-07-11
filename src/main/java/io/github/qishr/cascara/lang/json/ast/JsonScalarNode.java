package io.github.qishr.cascara.lang.json.ast;

import java.util.List;
import java.util.Objects;

import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.util.Json5SingleQuoteUnescaper;
import io.github.qishr.cascara.lang.json.util.JsonOptions;
import io.github.qishr.cascara.lang.json.util.JsonStringUnescaper;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.type.SchemaType;


public class JsonScalarNode extends JsonNode implements ScalarAstNode<JsonNode> {

    private final JsonOptions options;

    private SchemaType schemaType;
    private QuoteStyle quoteStyle = QuoteStyle.PLAIN;

    private Object jvmValue;
    private boolean isJvmValueCached;

    private String stringValue;
    private boolean isStringValueCached;

    private final boolean isKey;

    /// Constructor for use in parsers.
    /// Used when reading raw text from a file stream.
    /// Takes a String and triggers full lexical dialect type inference.
    public JsonScalarNode(
        JsonToken token,
        SchemaType schemaType,
        boolean isKey,
        JsonOptions options
    ) {
        super(token);
        this.schemaType = schemaType;
        this.quoteStyle = token.getQuoteStyle();
        this.options = (options == null) ? JsonOptions.STRICT : options;
        this.isKey = isKey;
    }

    public JsonScalarNode(String content, JsonOptions options) {
        this(content, QuoteStyle.UNDETERMINED, false, options);
    }

    public JsonScalarNode(
        Object jvmValue,
        QuoteStyle quoteStyle,
        boolean isKey,
        JsonOptions options
    ) {
        super();
        this.schemaType = SchemaType.of(jvmValue);
        this.quoteStyle = quoteStyle;
        this.options = (options == null) ? JsonOptions.STRICT : options;
        this.isKey = false;
        this.jvmValue = jvmValue;
        this.isJvmValueCached = true;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public JsonScalarNode(Object jvmValue, QuoteStyle quoteStyle) {
        this(jvmValue, quoteStyle, false, null);
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public JsonScalarNode(Object jvmValue) {
        this(jvmValue, false);
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public JsonScalarNode(Object jvmValue, boolean isKey) {
        this(jvmValue, QuoteStyle.UNDETERMINED, isKey, null);
    }

    /// The default constructor
    public JsonScalarNode() {
        this(null);
    }

    @Override
    public List<JsonNode> getChildren() {
        return List.of();
    }

    public SchemaType getSchemaType() {
        return schemaType;
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

    @Override
    public String getLexeme() {
        return token == null ? null : token.getLexeme();
    }

    @Override
    public String getContent() {
        return token == null ? asString() : token.getContent();
    }

    /// Returns the dialect-aware JVM value (cached).
    @Override
    public Object getPrimitive() {
        if (isJvmValueCached) {
            return jvmValue;
        }
        jvmValue = parse(token.getContent(), quoteStyle, isKey);
        isJvmValueCached = true;
        return jvmValue;
    }

    /// Returns the logical clean text value, stripped of outer formatting and escape markers.
    @Override
    public String asString() {
        if (!isStringValueCached) {
            if (token == null) {
                stringValue = (jvmValue == null) ? null : String.valueOf(jvmValue);
            } else {
                stringValue = unescape(token.getContent(), quoteStyle, isKey);
            }
            isStringValueCached = true;
        }
        return stringValue;
    }

    @Override
    public int asInteger() {
        return asInteger(0);
    }

    @Override
    public int asInteger(int defaultValue) {
        Object v = getPrimitive();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public double asDouble() {
        return asDouble(0.0);
    }

    @Override
    public double asDouble(double defaultValue) {
        Object v = getPrimitive();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public boolean asBoolean() {
        return asBoolean(false);
    }

    @Override
    public boolean asBoolean(boolean defaultValue) {
        Object v = getPrimitive();
        if (v instanceof Boolean b) return b;
        if (v == null) return defaultValue;
        String s = String.valueOf(v);
        // TODO: JSON5...
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;
        return defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonScalarNode that)) return false;
        return Objects.equals(this.getPrimitive(), that.getPrimitive())
            && quoteStyle == that.quoteStyle
            && isKey == that.isKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLexeme(), getContent(), quoteStyle, isKey);
    }

    /// {@inheritDoc}
    @Override
    public String toString() {
        return getLexeme() != null ? getLexeme() : String.valueOf(getPrimitive());
    }

    //
    //
    //

    private Object parse(String raw, QuoteStyle quoteStyle, boolean isKey) {

        if (schemaType == null || schemaType == SchemaType.ANY) {
            schemaType = inferType(raw, quoteStyle, isKey);
        }

        switch (schemaType) {
            case BOOLEAN:
                return "true".equals(raw);

            case NULL:
                return null;

            case INTEGER:
                return parseInteger(raw);

            case NUMBER:
                return parseNumber(raw);

            case STRING:
            default:
                return unescape(raw, quoteStyle, isKey);
        }
    }

    private SchemaType inferType(String raw, QuoteStyle quoteStyle, boolean isKey) {
        if (isKey) {

            // Strict JSON: keys must be double-quoted strings
            if (!options.allowUnquotedKeys()) {
                return SchemaType.STRING;
            }

            // JSON5-like: single-quoted keys allowed
            if (quoteStyle == QuoteStyle.SINGLE && options.allowSingleQuotedStrings()) {
                return SchemaType.STRING;
            }

            // JSON5-like: unquoted keys allowed
            if (quoteStyle == QuoteStyle.PLAIN) {

                // Identifiers allowed as keys
                if (isIdentifier(raw)) {
                    return SchemaType.STRING;
                }

                // Numbers allowed as keys
                if (options.allowHexadecimalNumbers() && isHexRaw(raw)) {
                    return SchemaType.STRING;
                }
                if (options.allowHexadecimalNumbers() && isOctalRaw(raw)) {
                    return SchemaType.STRING;
                }
                if (isDecimalNumber(raw)) {
                    return SchemaType.STRING;
                }

                // Infinity / NaN allowed as keys
                if (options.allowInfinityAndNaN() && isSpecialNumber(raw)) {
                    return SchemaType.STRING;
                }

                // Fallback: treat as string key
                return SchemaType.STRING;
            }

            // Double-quoted key
            return SchemaType.STRING;
        }

        // Quoted strings
        if (quoteStyle == QuoteStyle.DOUBLE) {
            return SchemaType.STRING;
        }
        if (quoteStyle == QuoteStyle.SINGLE && options.allowSingleQuotedStrings()) {
            return SchemaType.STRING;
        }

        // Plain scalars
        if (quoteStyle == QuoteStyle.PLAIN) {

            // Boolean
            if ("true".equals(raw) || "false".equals(raw)) {
                return SchemaType.BOOLEAN;
            }

            // Null
            if ("null".equals(raw)) {
                return SchemaType.NULL;
            }

            // JSON5 special numbers
            if (options.allowInfinityAndNaN() && isSpecialNumber(raw)) {
                return SchemaType.NUMBER;
            }

            // JSON5 hex/octal
            if (options.allowHexadecimalNumbers() && isHexRaw(raw)) {
                return SchemaType.NUMBER;
            }
            if (options.allowHexadecimalNumbers() && isOctalRaw(raw)) {
                return SchemaType.NUMBER;
            }

            SchemaType numberType = inferNumberType(raw);
            if (numberType != null) {
                return numberType;
            }

            return SchemaType.STRING;
        }

        return SchemaType.STRING;
    }

    private String unescape(String raw, QuoteStyle quoteStyle, boolean isKey) {

        // Keys: strict JSON always double-quoted output
        if (isKey && !options.allowUnquotedKeys()) {
            return JsonStringUnescaper.unescape(raw);
        }

        // JSON strict: double-quoted strings
        if (quoteStyle == QuoteStyle.DOUBLE) {
            return JsonStringUnescaper.unescape(raw);
        }

        // JSON5-like: single-quoted strings
        if (quoteStyle == QuoteStyle.SINGLE && options.allowSingleQuotedStrings()) {
            return Json5SingleQuoteUnescaper.unescape(raw);
        }

        // Plain scalars: no escaping
        return raw;
    }

    //
    // Internal helpers
    //
    private boolean isIdentifier(String raw) {
        int len = raw.length();
        if (len == 0) return false;

        char c = raw.charAt(0);
        if (!((c >= 'A' && c <= 'Z') ||
              (c >= 'a' && c <= 'z') ||
              c == '_')) {
            return false;
        }

        for (int i = 1; i < len; i++) {
            c = raw.charAt(i);
            if (!((c >= 'A' && c <= 'Z') ||
                  (c >= 'a' && c <= 'z') ||
                  (c >= '0' && c <= '9') ||
                  c == '_')) {
                return false;
            }
        }

        return true;
    }

    private boolean isSpecialNumber(String raw) {
        return raw.equals("Infinity") ||
               raw.equals("-Infinity") ||
               raw.equals("NaN");
    }

    private boolean isHexRaw(String raw) {
        return raw.length() > 2 &&
               raw.charAt(0) == '0' &&
               (raw.charAt(1) == 'x' || raw.charAt(1) == 'X');
    }

    private boolean isOctalRaw(String raw) {
        return raw.length() > 2 &&
               raw.charAt(0) == '0' &&
               (raw.charAt(1) == 'o' || raw.charAt(1) == 'O');
    }


    private boolean isDecimalNumber(String s) {
        return inferNumberType(s) != null;
    }

    private SchemaType inferNumberType(String s) {
        int len = s.length();
        if (len == 0) return null;

        int i = 0;
        char c = s.charAt(0);

        // optional sign
        if (c == '-' || c == '+') {
            if (len == 1) return null;
            i = 1;
        }

        boolean hasDigit = false;
        boolean hasDot = false;

        for (; i < len; i++) {
            c = s.charAt(i);

            if (c >= '0' && c <= '9') {
                hasDigit = true;
                continue;
            }

            if (c == '.') {
                if (hasDot) return null;
                hasDot = true;
                continue;
            }

            if (c == 'e' || c == 'E') {
                if (isScientific(s, i)) {
                    return SchemaType.NUMBER;
                }
            }

            return null;
        }

        return hasDigit ? (hasDot ? SchemaType.NUMBER : SchemaType.INTEGER) : null;
    }

    private boolean isScientific(String s, int ePos) {
        int len = s.length();
        if (ePos == 0 || ePos == len - 1) return false;

        int i = ePos + 1;
        char c = s.charAt(i);

        // optional sign
        if (c == '+' || c == '-') {
            i++;
            if (i == len) return false;
        }

        boolean hasDigit = false;

        for (; i < len; i++) {
            c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
                continue;
            }
            return false;
        }

        return hasDigit;
    }

    //
    // Number parsing
    //
    private double parseNumber(String raw) {
        // JSON5 hex
        if (options.allowHexadecimalNumbers() && isHexRaw(raw)) {
            String digits = raw.substring(2);
            return Integer.parseUnsignedInt(digits, 16);
        }

        // JSON5 octal
        if (options.allowHexadecimalNumbers() && isOctalRaw(raw)) {
            String digits = raw.substring(2);
            return Integer.parseUnsignedInt(digits, 8);
        }

        // JSON5 special numbers
        if (options.allowInfinityAndNaN()) {
            if (raw.equals("Infinity")) return Double.POSITIVE_INFINITY;
            if (raw.equals("-Infinity")) return Double.NEGATIVE_INFINITY;
            if (raw.equals("NaN")) return Double.NaN;
        }

        // JSON strict / JSON5 decimal
        return Double.parseDouble(raw);
    }

    private long parseInteger(String raw) {
        // JSON5 hex
        if (options.allowHexadecimalNumbers() && isHexRaw(raw)) {
            String digits = raw.substring(2);
            return Integer.parseUnsignedInt(digits, 16);
        }

        // JSON5 octal
        if (options.allowHexadecimalNumbers() && isOctalRaw(raw)) {
            String digits = raw.substring(2);
            return Integer.parseUnsignedInt(digits, 8);
        }

        // JSON strict / JSON5 decimal
        return Long.parseLong(raw);
    }

    public JsonOptions getOptions() {
        return options;
    }

}

