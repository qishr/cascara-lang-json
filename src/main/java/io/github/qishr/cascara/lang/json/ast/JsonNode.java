package io.github.qishr.cascara.lang.json.ast;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.lang.json.token.JsonToken;

public abstract class JsonNode implements AstNode {
    private final int startLine;
    private final int startColumn;
    private int endLine = 0;
    private int endColumn = 0;
    private final URI originUri;
    private final List<CommentAstNode> comments = new ArrayList<>();
    private JsonToken token;

    protected JsonNode() {
        this.startLine = 1;
        this.startColumn = 1;
        this.originUri = null;
    }

    protected JsonNode(int line, int column, URI uri) {
        this.startLine = line;
        this.startColumn = column;
        this.originUri = uri;
    }

    @Override public abstract List<? extends JsonNode> getChildren();
    @Override public int getStartLine() { return startLine; }
    @Override public int getStartColumn() { return startColumn; }
    @Override public int getEndLine() { return endLine; }
    @Override public int getEndColumn() { return endColumn; }
    @Override public URI getOriginUri() { return originUri; }
    @Override public List<CommentAstNode> getComments() { return comments; }
    @Override public JsonToken getToken() { return token; }
    public void setToken(JsonToken token) { this.token = token; }

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