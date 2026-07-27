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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.lang.json.token.JsonLexemeBackedToken;
import io.github.qishr.cascara.lang.json.token.JsonToken;

public abstract class JsonNode implements AstNode {
    private final int startLine;
    private final int startColumn;
    private int endLine = 0;
    private int endColumn = 0;
    private final List<CommentAstNode> comments = new ArrayList<>();
    protected JsonToken token;

    protected JsonNode() {
        this.startLine = -1;
        this.startColumn = -1;
    }

    protected JsonNode(int line, int column) {
        this.startLine = line;
        this.startColumn = column;
    }

    protected JsonNode(JsonToken token) {
        this.token = token;
        this.startLine = token.getStartLine();
        this.startColumn = token.getStartColumn();
    }

    @Override public abstract List<? extends JsonNode> getChildren();
    @Override public int getStartLine() { return startLine; }
    @Override public int getStartColumn() { return startColumn; }
    @Override public int getEndLine() { return endLine; }
    @Override public int getEndColumn() { return endColumn; }
    @Override public List<CommentAstNode> getComments() { return comments; }
    @Override public JsonToken getToken() { return token; }
    public void setToken(JsonLexemeBackedToken token) { this.token = token; }

    public void addComment(CommentAstNode comment) {
        this.comments.add(comment);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonNode other)) return false;
        return Objects.equals(getChildren(), other.getChildren());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getChildren());
    }
}