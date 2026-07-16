package io.github.qishr.cascara.lang.json.token;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class JsonStructuralToken implements JsonToken {
    protected final int startLine;
    protected final int startColumn;
    protected final int startOffset;
    protected final JsonTokenType type;
    private List<JsonComment> comments;

    public JsonStructuralToken(int line, int column, int startOffset, JsonTokenType type) {
        this.startLine = line;
        this.startColumn = column;
        this.startOffset = startOffset;
        this.type = type;
    }

    public JsonStructuralToken(JsonTokenType type) {
        this.startLine = 0;
        this.startColumn = 0;
        this.startOffset = -1;
        this.type = type;
    }

    @Override
    public String getLexeme() {
        return null;
    }

	@Override
	public String getContent() {
        return null;
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

	@Override
	public JsonTokenType getType() {
        return type;
	}

	@Override
	public QuoteStyle getQuoteStyle() {
        return QuoteStyle.PLAIN;
	}

	@Override
	public JsonLiteral getLiteral() {
        return null;
	}

    public List<JsonComment> getComments() {
        return comments;
    }

    public void attachComments(List<JsonComment> list) {
        if (list == null || list.isEmpty()) return;
        this.comments = new ArrayList<>(list);
    }
}
