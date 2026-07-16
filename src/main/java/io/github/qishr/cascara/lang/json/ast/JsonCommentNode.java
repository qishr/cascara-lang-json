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


package io.github.qishr.cascara.lang.json.ast;

import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;

import java.util.List;

public class JsonCommentNode extends JsonNode implements ScalarAstNode<JsonNode>, CommentAstNode {
    private String value;
    private String rawValue;
    private final boolean multiLine;

    public JsonCommentNode(int line, int column, String rawValue, String stringValue, boolean multiLine) {
        super(line, column);
        this.value = stringValue;
        this.rawValue = rawValue;
        this.multiLine = multiLine;
    }

    // @Override
    public String getContent() {
        return value != null ? value.toString() : null;
    }

    @Override
    public JsonCommentNode setQuoteStyle(QuoteStyle style) {
        return this;
    }

    @Override
    public QuoteStyle getQuoteStyle() {
        return null;
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
    @Override
    public String getLexeme() {
        return (rawValue != null) ? rawValue : value;
    }

    @Override
    public int asInteger() {
        return 0; //TODO:  cascara://projman/CASC-00027711
    }

    @Override
    public int asInteger(int defaultValue) {
        return 0;
    }

    @Override
    public double asDouble() {
        return 0;
    }

    @Override
    public double asDouble(double defaultValue) {
        return 0;
    }

    @Override
    public boolean asBoolean() {
        return false;
    }

    @Override
    public boolean asBoolean(boolean defaultValue) {
        return false;
    }

    @Override
    public Object getPrimitive() {
        return null;
    }

    // @Override
    // public JsonCommentNode setPrimitive(Object value) {
    //     this.value = String.valueOf(value);
    //     return this;
    // }

    @Override
    public String asString() {
        return value;
    }

    @Override
    public boolean isMultiLine() {
        return multiLine;
    }
}
