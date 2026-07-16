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

import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class JsonNumberToken implements JsonToken {
    private final LexemeProvider provider;

    protected final int startLine;
    protected final int startColumn;
    protected final int startOffset;
    private final int endOffset;
    // protected final JsonTokenType type;

    private final double value;
    private final boolean isInteger;
    private final boolean isHex;

    private List<JsonComment> comments;

    private String cachedLexeme;
    private boolean isLexemeCached;

    // Identifiers & Numbers
    // public JsonNumberToken(
    //     LexemeProvider provider,
    //     int startLine, int startColumn,
    //     int startOffset, int endOffset,
    //     JsonTokenType type
    // ) {
    //     this.startLine = startLine;
    //     this.startColumn = startColumn;
    //     this.startOffset = startOffset;
    //     this.endOffset = endOffset;
    //     this.type = type;

    //     this.provider = provider;
    // }

    public JsonNumberToken(
        LexemeProvider provider,
        int startLine, int startColumn,
        int startOffset, int endOffset,
        ScannedNumber n
    ) {
        this.provider = provider;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.value = n.value;
        this.isInteger = n.isInteger;
        this.isHex = n.isHex;
        // this.provider = provider;
    }

    public Number getNumber() {
        if (isHex) return (long)value;
        if (isInteger) return (long)value;
        return value;
    }
    @Override
    public String getLexeme() {
        if (!isLexemeCached) {
            cachedLexeme = provider.slice(startOffset, endOffset);
            isLexemeCached = true;
        }
        return cachedLexeme;
    }

    @Override
    public String getContent() {
        return null;
    }

    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public JsonTokenType getType() {
        return JsonTokenType.NUMBER;
    }

    public JsonLiteral getLiteral() {
        return null;
    }

    @Override
    public QuoteStyle getQuoteStyle() {
        return QuoteStyle.PLAIN;
    }

    @Override
    public int getOffset() {
        return startOffset;
    }

    @Override
    public int getStartLine() {
        return startLine;
    }

    @Override
    public int getStartColumn() {
        return startColumn;
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
        return "NUMBER('" + value + "')@" + startLine + ":" + startColumn;
    }
}
