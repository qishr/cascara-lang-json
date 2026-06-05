package io.github.qishr.cascara.lang.json;

import io.github.qishr.cascara.common.lang.AbstractPrimitive;
import io.github.qishr.cascara.common.lang.QuoteStyle;

public class JsonPrimitive extends AbstractPrimitive {

    public JsonPrimitive(Object primitiveValue, QuoteStyle quoteStyle) {
        this(primitiveValue, quoteStyle, true);
    }

    public JsonPrimitive(Object primitiveValue) {
        super(primitiveValue);
    }

    /// Parses unescaped text and infers its type.
    public static JsonPrimitive fromString(String unescapedContent, QuoteStyle quoteStyle) {
        return new JsonPrimitive(unescapedContent, quoteStyle, false);
    }

    private JsonPrimitive(Object input, QuoteStyle quoteStyle, boolean isNative) {
        super(input, quoteStyle, isNative);
    }

    @Override
    protected QuoteStyle inferQuoteStyle(Object value) {
        QuoteStyle style = QuoteStyle.PLAIN;
        if (value instanceof CharSequence || value instanceof Character) {

            style = QuoteStyle.DOUBLE;
        }
        return style;
    }

    /// Converts JSON primitive literal values into native Java types.
    /// Strictly handles case-sensitive 'true', 'false', and 'null'.
    @Override
    protected Object coerceLiteralValue(String text) {
        if ("true".equals(text)) return Boolean.TRUE;
        if ("false".equals(text)) return Boolean.FALSE;
        if ("null".equals(text)) return null;

        return null; // Fallback to base number parsing or fallback types
    }

    /// Handles strict JSON string unescaping mechanics for double-quoted strings.
    @Override
    protected String unescapeQuotedString(String text, QuoteStyle style) {
        if (style != QuoteStyle.DOUBLE || text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (ch == '\\' && i + 1 < len) {
                char next = text.charAt(i + 1);
                switch (next) {
                    case '"'  -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case '/'  -> { sb.append('/'); i++; }
                    case 'b'  -> { sb.append('\b'); i++; }
                    case 'f'  -> { sb.append('\f'); i++; }
                    case 'n'  -> { sb.append('\n'); i++; }
                    case 'r'  -> { sb.append('\r'); i++; }
                    case 't'  -> { sb.append('\t'); i++; }
                    case 'u'  -> {
                        // Handle standard JSON 4-hex-character Unicode escape (\\uXXXX)
                        if (i + 5 < len) {
                            try {
                                String hex = text.substring(i + 2, i + 6);
                                int codePoint = Integer.parseInt(hex, 16);
                                sb.append((char) codePoint);
                                i += 5;
                            } catch (NumberFormatException e) {
                                // Fallback if hex sequence is malformed
                                sb.append(ch);
                            }
                        } else {
                            sb.append(ch);
                        }
                    }
                    default -> sb.append(ch);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}