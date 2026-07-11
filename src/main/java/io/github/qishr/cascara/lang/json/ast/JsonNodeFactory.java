package io.github.qishr.cascara.lang.json.ast;

import io.github.qishr.cascara.common.lang.ast.AstNodeFactory;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.json.util.JsonOptions;

public class JsonNodeFactory implements AstNodeFactory<JsonNode,JsonScalarNode,JsonSequenceNode,JsonMapNode,JsonMapEntryNode,String> {

    @Override
    public JsonScalarNode createScalarNode(Object jvmValue) {
        return new JsonScalarNode(jvmValue);
    }

    @Override
    public JsonScalarNode createScalarNode(Object jvmValue, QuoteStyle quoteStyle) {
        return new JsonScalarNode(jvmValue, quoteStyle);
    }

	@Override
	public JsonScalarNode createScalarNode(Object jvmValue, QuoteStyle quoteStyle, LanguageOptions<?> options) {
        return new JsonScalarNode(jvmValue, quoteStyle, false, (JsonOptions)options);
	}

    @Override
    public String createKey(Object key) {
        if (key instanceof String s) return s;
        return String.valueOf(key);
        // return new JsonScalarNode(key, true);
    }

    @Override
    public JsonSequenceNode createSequenceNode() {
        return new JsonSequenceNode();
    }

    @Override
    public JsonMapNode createMapNode() {
        return new JsonMapNode();
    }
}
