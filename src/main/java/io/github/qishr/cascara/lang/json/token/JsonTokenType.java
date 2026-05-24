package io.github.qishr.cascara.lang.json.token;

import io.github.qishr.cascara.common.lang.token.TokenCategory;
import io.github.qishr.cascara.common.lang.token.TokenType;

public enum JsonTokenType implements TokenType {
    LEFT_BRACE(TokenCategory.PUNCTUATION),
    RIGHT_BRACE(TokenCategory.PUNCTUATION),
    LEFT_BRACKET(TokenCategory.PUNCTUATION),
    RIGHT_BRACKET(TokenCategory.PUNCTUATION),
    COLON(TokenCategory.PUNCTUATION),
    COMMA(TokenCategory.PUNCTUATION),
    DOT(TokenCategory.PUNCTUATION),
    STRING(TokenCategory.STRING),
    NUMBER(TokenCategory.NUMBER),
    BOOLEAN(TokenCategory.BOOLEAN),
    NULL(TokenCategory.NULL),
    EOF(TokenCategory.INTERNAL),
    UNKNOWN(TokenCategory.INTERNAL),
    ERROR(TokenCategory.ERROR),
    IDENTIFIER(TokenCategory.IDENTIFIER),
    COMMENT(TokenCategory.COMMENT);// <-- New type for syntax highlighting

    private final TokenCategory category;

    JsonTokenType(TokenCategory category) {
        this.category = category;
    }

    @Override
    public String getId() {
        return name();
    }

    @Override
    public TokenCategory getCategory() {
        return category;
    }
}