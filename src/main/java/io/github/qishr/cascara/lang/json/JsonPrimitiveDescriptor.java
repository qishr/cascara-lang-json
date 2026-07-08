package io.github.qishr.cascara.lang.json;

import io.github.qishr.cascara.common.lang.type.PrimitiveDescriptor;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;


public final class JsonPrimitiveDescriptor implements PrimitiveDescriptor {

    private final JsonOptions options;

    public JsonPrimitiveDescriptor(JsonOptions options) {
        this.options = options;
    }

    // ------------------------------------------------------------
    // Type inference
    // ------------------------------------------------------------
    @Override
    public PrimitiveType inferType(String raw, QuoteStyle quoteStyle, boolean isKey) {

        // -----------------------------
        // KEY POSITION
        // -----------------------------
        if (isKey) {

            // Strict JSON: keys must be double-quoted strings
            if (!options.allowUnquotedKeys()) {
                return PrimitiveType.STRING;
            }

            // JSON5-like: single-quoted keys allowed
            if (quoteStyle == QuoteStyle.SINGLE && options.allowSingleQuotedStrings()) {
                return PrimitiveType.STRING;
            }

            // JSON5-like: unquoted keys allowed
            if (quoteStyle == QuoteStyle.PLAIN) {

                // Identifiers allowed as keys
                if (isIdentifier(raw)) {
                    return PrimitiveType.STRING;
                }

                // Numbers allowed as keys
                if (options.allowHexadecimalNumbers() && isHexRaw(raw)) {
                    return PrimitiveType.STRING;
                }
                if (options.allowHexadecimalNumbers() && isOctalRaw(raw)) {
                    return PrimitiveType.STRING;
                }
                if (isDecimalNumber(raw)) {
                    return PrimitiveType.STRING;
                }

                // Infinity / NaN allowed as keys
                if (options.allowInfinityAndNaN() && isSpecialNumber(raw)) {
                    return PrimitiveType.STRING;
                }

                // Fallback: treat as string key
                return PrimitiveType.STRING;
            }

            // Double-quoted key
            return PrimitiveType.STRING;
        }

        // -----------------------------
        // VALUE POSITION
        // -----------------------------

        // Quoted strings
        if (quoteStyle == QuoteStyle.DOUBLE) {
            return PrimitiveType.STRING;
        }
        if (quoteStyle == QuoteStyle.SINGLE && options.allowSingleQuotedStrings()) {
            return PrimitiveType.STRING;
        }

        // Plain scalars
        if (quoteStyle == QuoteStyle.PLAIN) {

            // Boolean
            if ("true".equals(raw) || "false".equals(raw)) {
                return PrimitiveType.BOOLEAN;
            }

            // Null
            if ("null".equals(raw)) {
                return PrimitiveType.NULL;
            }

            // JSON5 special numbers
            if (options.allowInfinityAndNaN() && isSpecialNumber(raw)) {
                return PrimitiveType.NUMBER;
            }

            // JSON5 hex/octal
            if (options.allowHexadecimalNumbers() && isHexRaw(raw)) {
                return PrimitiveType.NUMBER;
            }
            if (options.allowHexadecimalNumbers() && isOctalRaw(raw)) {
                return PrimitiveType.NUMBER;
            }

            // JSON strict number
            if (isDecimalNumber(raw)) {
                return PrimitiveType.NUMBER;
            }

            return PrimitiveType.STRING;
        }

        return PrimitiveType.STRING;
    }

    // ------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------
    @Override
    public Object parse(String raw, QuoteStyle quoteStyle, boolean isKey) {

        PrimitiveType type = inferType(raw, quoteStyle, isKey);

        switch (type) {
            case BOOLEAN:
                return "true".equals(raw);

            case NULL:
                return null;

            case NUMBER:
                return parseNumber(raw);

            case STRING:
            default:
                return unescape(raw, quoteStyle, isKey);
        }
    }

    // ------------------------------------------------------------
    // Unescaping
    // ------------------------------------------------------------
    @Override
    public String unescape(String raw, QuoteStyle quoteStyle, boolean isKey) {

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

    // ------------------------------------------------------------
    // JVM conversions
    // ------------------------------------------------------------
    @Override
    public boolean toBoolean(Object scalar) {
        String s = String.valueOf(scalar);
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;
        throw new IllegalArgumentException("Not a boolean: " + s);
    }

    @Override
    public int toInteger(Object scalar) {
        return (int) toDouble(scalar);
    }

    @Override
    public long toLong(Object scalar) {
        return (long) toDouble(scalar);
    }

    @Override
    public float toFloat(Object scalar) {
        return (float) toDouble(scalar);
    }

    @Override
    public double toDouble(Object scalar) {
        return Double.parseDouble(String.valueOf(scalar));
    }

    @Override
    public int toIntegerOrDefault(Object scalar, int defaultValue) {
        try {
            return toInteger(scalar);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public long toLongOrDefault(Object scalar, long defaultValue) {
        try {
            return toLong(scalar);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public float toFloatOrDefault(Object scalar, float defaultValue) {
        try {
            return toFloat(scalar);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public double toDoubleOrDefault(Object scalar, double defaultValue) {
        try {
            return toDouble(scalar);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public boolean toBooleanOrDefault(Object scalar, boolean defaultValue) {
        try {
            return toBoolean(scalar);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ------------------------------------------------------------
    // Quote style inference
    // ------------------------------------------------------------
    @Override
    public QuoteStyle inferQuoteStyle(Object scalar, boolean isKey) {

        // Keys: strict JSON always double-quoted
        if (isKey && !options.allowUnquotedKeys()) {
            return QuoteStyle.DOUBLE;
        }

        // JSON5-like: single quotes allowed
        if (scalar instanceof String && options.allowSingleQuotedStrings()) {
            return QuoteStyle.SINGLE;
        }

        return QuoteStyle.DOUBLE;
    }

    // ------------------------------------------------------------
    // Internal helpers (fast, no regex, no startsWith)
    // ------------------------------------------------------------
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
        int len = s.length();
        if (len == 0) return false;

        int i = 0;
        char c = s.charAt(0);

        // optional sign
        if (c == '-' || c == '+') {
            if (len == 1) return false;
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
                if (hasDot) return false;
                hasDot = true;
                continue;
            }

            if (c == 'e' || c == 'E') {
                return isScientific(s, i);
            }

            return false;
        }

        return hasDigit;
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

    // ------------------------------------------------------------
    // Number parsing (fast, correct)
    // ------------------------------------------------------------
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

    public JsonOptions getOptions() {
        return options;
    }
}
