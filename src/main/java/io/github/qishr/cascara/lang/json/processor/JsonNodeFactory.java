package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.lang.ast.AstNodeFactory;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.type.Primitive;
import io.github.qishr.cascara.lang.json.ast.JsonMapEntryNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;

public class JsonNodeFactory implements AstNodeFactory<JsonNode,JsonScalarNode,JsonSequenceNode,JsonMapNode,JsonMapEntryNode> {

    @Override
    public JsonScalarNode createScalarNode(Object primitiveValue) {
        System.out.println("createScalarNode " + primitiveValue);
        // TODO: Should this parameter be called:
        // - `primitiveValue` (used in AstNode), or
        // - `jvmInstance` (used in Serializer) ?
        // Check coding standards and update if need be.
        return new JsonScalarNode(primitiveValue);
    }

    @Override
    public JsonScalarNode createScalarNode(Object key, QuoteStyle quoteStyle) {
        System.out.println("createScalarNode " + key + " " + quoteStyle);
        return new JsonScalarNode(key, quoteStyle);
    }

    @Override
    public JsonScalarNode createScalarNode(Primitive primitive) {
        System.out.println("createScalarNode primitive: " + primitive);
        return JsonScalarNode.fromPrimitive(primitive);
    }

    @Override
    public JsonScalarNode createScalarKeyNode(Object key) {
        System.out.println("createScalarKeyNode " + key);
        return new JsonScalarNode(key, true);
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
