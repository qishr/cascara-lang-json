// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.json.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
        assertEquals("testStr", scalar.asString());
        assertEquals(QuoteStyle.DOUBLE, scalar.getQuoteStyle());
        // TODO: Assert PrimitiveType
    }

    @Test
    void test_convertBoolean() {
        ReferenceScalarNode root = new ReferenceScalarNode(true);
        JsonConverter converter = new JsonConverter();
        JsonNode jsonRoot = converter.fromAst(root);
        assertInstanceOf(JsonScalarNode.class, jsonRoot);
        JsonScalarNode scalar = (JsonScalarNode)jsonRoot;
        assertEquals(true, scalar.asBoolean());
        assertEquals(QuoteStyle.PLAIN, scalar.getQuoteStyle());
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
        assertInstanceOf(String.class, entry.getKey());
        assertInstanceOf(JsonScalarNode.class, entry.getValue());
        String key = entry.getKey();
        JsonScalarNode value = (JsonScalarNode)entry.getValue();
        // assertEquals(QuoteStyle.DOUBLE, key.getQuoteStyle());
        assertEquals(QuoteStyle.DOUBLE, value.getQuoteStyle());

        JsonNode testBool = map.get("testBool");
        assertInstanceOf(JsonScalarNode.class, testBool);
        assertEquals(true, ((JsonScalarNode)testBool).asBoolean());

        // Check quotes on value and key
        entry = map.getEntries().get(1);
        assertInstanceOf(String.class, entry.getKey());
        assertInstanceOf(JsonScalarNode.class, entry.getValue());
        key = entry.getKey();
        value = (JsonScalarNode)entry.getValue();
        // assertEquals(QuoteStyle.DOUBLE, key.getQuoteStyle());
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
    }
}
