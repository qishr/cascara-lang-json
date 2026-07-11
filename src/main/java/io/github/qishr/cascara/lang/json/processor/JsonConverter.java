package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.common.lang.processor.AstConverter;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;

public class JsonConverter extends AbstractJsonProcessor<JsonConverter> implements AstConverter<JsonNode> {
    @Override protected JsonConverter self() { return this; }

    @Override
    public ContentType getContentType() {
        return JsonAstParser.JSON_CONTENT_TYPE;
    }

    public String toText(AstNode ast) {
        JsonNode jsonNode = fromAst(ast);
        JsonEmitter emitter = new JsonEmitter();
        return emitter.emit(jsonNode);
    }

    public JsonNode fromAst(AstNode from) {
        System.out.println("fromAst");
        if (from instanceof MapAstNode fromMap) {
            System.out.println("  map");
            JsonMapNode map = new JsonMapNode();
            for (Object entry : fromMap.getEntries()) {
                if (entry instanceof MapEntryAstNode fromMapEntry) {
                    Object astKey = fromMapEntry.getKey();
                    AstNode astValue = fromMapEntry.getValue();
                    if (astKey instanceof ScalarAstNode astScalar) {
                        String jsonKey = astScalar.asString();
                        // if (fromAst(astScalar) instanceof JsonScalarNode jsonKey) {
                            System.out.println("    scalar key " + jsonKey);
                            JsonNode jsonValue = fromAst(astValue);
                            map.put(jsonKey, jsonValue);
                        // }
                    }
                }
            }
            return map;
        } else if (from instanceof SequenceAstNode astSeq) {
            System.out.println("  seq");
            JsonSequenceNode sequence = new JsonSequenceNode();
            for (Object element : astSeq.getElements()) {
                if (element instanceof AstNode astElement) {
                    sequence.add(fromAst(astElement));
                }
            }
            return sequence;
        } else if (from instanceof ScalarAstNode astScalar) {
            System.out.println("  sca " + astScalar.asString());

            // TODO: Tests for this

            JsonScalarNode scalar = new JsonScalarNode(astScalar.getPrimitive(), false);
            // scalar.setPrimitive(astScalar.getPrimitive());
            // scalar.setRaw(astScalar.getString());
            Object value = scalar.getPrimitive();
            if (value == null
                || value instanceof Integer
                || value instanceof Double
                || value instanceof Boolean
            ) {
                scalar.setQuoteStyle(QuoteStyle.PLAIN);
            } else {
                scalar.setQuoteStyle(QuoteStyle.DOUBLE);
            }
            return scalar;


        } else {
            System.err.println("Unknown AST node");
            return null;
        }
    }
}
