package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;
import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.common.lang.processor.AstConverter;
import io.github.qishr.cascara.lang.json.JsonDocument;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;

public class JsonConverter extends AbstractJsonProcessor<JsonConverter> implements AstConverter<JsonNode> {
    @Override protected JsonConverter self() { return this; }

    @Override
    public ContentType getContentType() {
        return JsonParser.contentType;
    }

    public String toText(AstNode ast) {
        JsonNode jsonNode = fromAst(ast);
        JsonEmitter emitter = new JsonEmitter();
        return emitter.emit(jsonNode);
    }

    public JsonNode fromAst(AstNode ast) {
        if (ast instanceof StructuredDocument astDoc) {
            JsonDocument yamlDoc = new JsonDocument(fromAst(astDoc.getRoot()));
            return yamlDoc;
        } else if (ast instanceof MapAstNode astMap) {
            JsonMapNode map = new JsonMapNode();
            for (Object entry : astMap.getEntries()) {
                if (entry instanceof MapEntryAstNode astMapEntry) {
                    AstNode astKey = astMapEntry.getKey();
                    AstNode astValue = astMapEntry.getValue();
                    if (astKey instanceof ScalarAstNode) {
                        if (fromAst(astKey) instanceof JsonScalarNode jsonKey) {
                            JsonNode yamlValue = fromAst(astValue);
                            map.put(jsonKey, yamlValue);
                        }
                    }
                }
            }
            return map;
        } else if (ast instanceof SequenceAstNode astSeq) {
            JsonSequenceNode sequence = new JsonSequenceNode();
            for (Object element : astSeq.getElements()) {
                if (element instanceof AstNode astElement) {
                    sequence.add(fromAst(astElement));
                }
            }
            return sequence;
        } else if (ast instanceof ScalarAstNode astScalar) {
            JsonScalarNode scalar = new JsonScalarNode();
            scalar.setPrimitiveValue(astScalar.getPrimitiveValue());
            scalar.setValue(astScalar.getString());
            Object value = scalar.getPrimitiveValue();
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
