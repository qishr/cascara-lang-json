package io.github.qishr.cascara.lang.json;

import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class Json5SingleQuoteUnescaper {
    public static String unescape(String text) {
        // , QuoteStyle style) {
        // if (style != QuoteStyle.DOUBLE || text == null || text.isEmpty()) {
        //     return text;
        // }

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
