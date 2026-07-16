package io.github.qishr.cascara.lang.json.token;

import java.util.List;

import io.github.qishr.cascara.common.lang.token.Token;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public interface JsonToken extends Token {
    JsonTokenType getType();
    QuoteStyle getQuoteStyle();
    JsonLiteral getLiteral();
    List<JsonComment> getComments();
    void attachComments(List<JsonComment> list);
}
