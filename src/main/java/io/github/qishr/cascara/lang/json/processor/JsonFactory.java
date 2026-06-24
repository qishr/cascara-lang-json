package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.lang.processor.AstFactory;
import io.github.qishr.cascara.common.lang.type.Primitive;
import io.github.qishr.cascara.lang.json.ast.JsonMapEntryNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;

public class JsonFactory implements AstFactory<JsonNode,JsonScalarNode,JsonSequenceNode,JsonMapNode,JsonMapEntryNode> {

    @Override
    public JsonScalarNode createScalarNode(Object primitiveValue) {
        // TODO: Should this parameter be called:
        // - `primitiveValue` (used in AstNode), or
        // - `jvmInstance` (used in Serializer) ?
        // Check coding standards and update if need be.
        return new JsonScalarNode(primitiveValue);
    }

    @Override
    public JsonScalarNode createScalarNode(Primitive primitive) {
        return JsonScalarNode.fromPrimitive(primitive);
    }

    @Override
    public JsonSequenceNode createSequenceNode() {
        return new JsonSequenceNode();
    }

    @Override
    public JsonMapNode createMapNode() {
        return new JsonMapNode();
    }

    public JsonScalarNode createScalarNode(Object key, QuoteStyle quoteStyle) {
        return new JsonScalarNode(key, quoteStyle);
    }
}
