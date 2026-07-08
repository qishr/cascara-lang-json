package io.github.qishr.cascara.lang.json.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.reference.ReferenceMapNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceScalarNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceSequenceNode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.json.ast.JsonMapEntryNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;

public class JsonConverterTest {
    @Test
    void test_convertString() {
        ReferenceScalarNode root = new ReferenceScalarNode("testStr");
        JsonConverter converter = new JsonConverter();
        JsonNode jsonRoot = converter.fromAst(root);
        assertInstanceOf(JsonScalarNode.class, jsonRoot);
        JsonScalarNode scalar = (JsonScalarNode)jsonRoot;
        assertEquals(QuoteStyle.DOUBLE, scalar);
        // TODO: Assert PrimitiveType
    }

    @Test
    void test_convertBoolean() {
        ReferenceScalarNode root = new ReferenceScalarNode(true);
        JsonConverter converter = new JsonConverter();
        JsonNode jsonRoot = converter.fromAst(root);
        assertInstanceOf(JsonScalarNode.class, jsonRoot);
        JsonScalarNode scalar = (JsonScalarNode)jsonRoot;
        assertEquals(QuoteStyle.PLAIN, scalar);
        // TODO: Assert PrimitiveType
    }

    @Test
    void test_convertSimpleMap() {
    }

    @Test
    void test_convertMap() {

        // TODO:
        // - MapNode.put overloads that take bool, int, long, float, etc

        // 1. Build a simple AST using the reference implementation nodes

        ReferenceMapNode root = new ReferenceMapNode()
            .put("testStr", "testStr")
            .put("testBool", new ReferenceScalarNode(true))
            .put("testSeq", new ReferenceSequenceNode()
                .add(new ReferenceScalarNode(1))
                .add(new ReferenceScalarNode(2))
                .add(new ReferenceScalarNode(3)));

        // 2. Convert it to a JSON AST

        JsonConverter converter = new JsonConverter();
        JsonNode jsonRoot = converter.fromAst(root);

        // 3. Verify / Validate

        assertInstanceOf(JsonMapNode.class, jsonRoot);
        JsonMapNode map = (JsonMapNode)jsonRoot;

        JsonNode testStr = map.get("testStr");
        assertInstanceOf(JsonScalarNode.class, testStr);
        JsonScalarNode testStrScalar = (JsonScalarNode)testStr;
        assertEquals("testStr", testStrScalar.asString());

        // Check quotes on value and key
        JsonMapEntryNode entry = map.getEntries().getFirst();
        assertInstanceOf(JsonScalarNode.class, entry.getKey());
        assertInstanceOf(JsonScalarNode.class, entry.getValue());
        JsonScalarNode key = (JsonScalarNode)entry.getKey();
        JsonScalarNode value = (JsonScalarNode)entry.getValue();
        assertEquals(QuoteStyle.DOUBLE, key.getQuoteStyle());
        assertEquals(QuoteStyle.DOUBLE, value.getQuoteStyle());

        JsonNode testBool = map.get("testBool");
        assertInstanceOf(JsonScalarNode.class, testBool);
        assertEquals(true, ((JsonScalarNode)testBool).asBoolean());

        // Check quotes on value and key
        entry = map.getEntries().get(1);
        assertInstanceOf(JsonScalarNode.class, entry.getKey());
        assertInstanceOf(JsonScalarNode.class, entry.getValue());
        key = (JsonScalarNode)entry.getKey();
        value = (JsonScalarNode)entry.getValue();
        assertEquals(QuoteStyle.DOUBLE, key.getQuoteStyle());
        assertEquals(QuoteStyle.PLAIN, value.getQuoteStyle());

        JsonNode testSeq = map.get("testSeq");
        assertInstanceOf(JsonSequenceNode.class, testSeq);
        JsonSequenceNode seq = (JsonSequenceNode)testSeq;
        assertEquals(3, seq.size());

        JsonNode item1 = seq.get(0);
        assertInstanceOf(JsonScalarNode.class, item1);
        JsonScalarNode item1scalar = (JsonScalarNode)item1;
        assertEquals(1, item1scalar.asInteger());

        JsonNode item3 = seq.get(2);
        assertInstanceOf(JsonScalarNode.class, item3);
        JsonScalarNode item3scalar = (JsonScalarNode)item3;
        assertEquals(3, item3scalar.asInteger());

        String jsonText = converter.toText(root);
        // String json = """
        //     {
        //         "status": true
        //     }
        //     """;



        // JsonNode doc = new JsonAstParser().parse(json);
        // if (doc instanceof JsonMapNode map) {
        //     if (map.get("status") instanceof JsonScalarNode status) {
        //         Object o = status.getPrimitive();
        //         assertInstanceOf(Boolean.class, o);
        //     }
        // }

    }
}
