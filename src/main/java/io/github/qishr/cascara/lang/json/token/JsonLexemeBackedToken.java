// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.json.token;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class JsonLexemeBackedToken implements JsonToken {
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String NULL = "null";
    private static final String INFINITY = "Infinity";
    private static final String NAN = "NaN";

    protected final int startLine;
    protected final int startColumn;
    protected final int startOffset;
    protected final JsonTokenType type;
    private final JsonLiteral literal;
    private final String lexeme;
    private final String content;
    private final QuoteStyle quoteStyle;
    private List<JsonComment> comments;

    /// Structural Token
    public JsonLexemeBackedToken(
        int line,
        int column,
        int startOffset,
        JsonTokenType type)
    {
        this.startLine = line;
        this.startColumn = column;
        this.startOffset = startOffset;
        this.type = type;

        this.lexeme = null;
        this.content = null;
        this.literal = null;
        this.quoteStyle = QuoteStyle.PLAIN;
    }

    /// Literals Token
    public JsonLexemeBackedToken(
        int line, int column,
        int startOffset,
        JsonTokenType type,
        JsonLiteral literal
    ) {
        this.startLine = line;
        this.startColumn = column;
        this.startOffset = startOffset;
        this.type = type;
        this.literal = literal;

        this.lexeme = null;

        this.content = switch (literal) {
            case TRUE -> TRUE;
            case FALSE -> FALSE;
            case NULL -> NULL;
            case INFINITY -> INFINITY;
            case NAN -> NAN;
        };

        this.quoteStyle = QuoteStyle.PLAIN;
    }

    /// Number & Identifier Tokens
    public JsonLexemeBackedToken(
        int line, int column,
        int startOffset,
        JsonTokenType type,
        String lexeme
    ) {
        this.startLine = line;
        this.startColumn = column;
        this.startOffset = startOffset;
        this.type = type;
        this.lexeme = lexeme;

        this.content = null;
        this.literal = null;
        this.quoteStyle = QuoteStyle.PLAIN;
    }

    /// String Token
    public JsonLexemeBackedToken(
        int line, int column,
        int startOffset,
        JsonTokenType type,
        String lexeme, String content,
        QuoteStyle quoteStyle
    ) {
        this.startLine = line;
        this.startColumn = column;
        this.startOffset = startOffset;
        this.type = type;
        this.lexeme = lexeme;
        this.content = content;
        this.quoteStyle = quoteStyle;

        this.literal = null;
    }

    @Override
    public int getStartLine() {
        return startLine;
    }

    @Override
    public int getStartColumn() {
        return startColumn;
    }

    @Override
    public int getOffset() {
        return startOffset;
    }

    @Override
    public JsonTokenType getType() {
        return type;
    }

    public JsonLiteral getLiteral() {
        return literal;
    }

    @Override
    public String getLexeme() {
        return lexeme;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public QuoteStyle getQuoteStyle() {
        return quoteStyle;
    }

    public List<JsonComment> getComments() {
        return comments;
    }

    public void attachComments(List<JsonComment> list) {
        if (list == null || list.isEmpty()) return;
        this.comments = new ArrayList<>(list);
    }


    @Override
    public String toString() {
        String displayLexeme = lexeme.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
        String valuePart = (content != null) ? " (Value: " + content + ")" : "";

        return String.format("[%-20s | '%-15s'%s | L:%d C:%d]",
            type,
            displayLexeme,
            valuePart,
            startLine,
            startColumn);
    }
}
