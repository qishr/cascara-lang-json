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

import io.github.qishr.cascara.common.lang.agnostic.AgnosticMapNode;
import io.github.qishr.cascara.common.lang.agnostic.AgnosticScalarNode;
import io.github.qishr.cascara.common.lang.agnostic.AgnosticSequenceNode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.json.ast.JsonProperty;
import io.github.qishr.cascara.lang.json.ast.JsonObject;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalar;
import io.github.qishr.cascara.lang.json.ast.JsonArray;

public class JsonConverterTest {
    @Test
    void test_convertString() {
        AgnosticScalarNode root = new AgnosticScalarNode("testStr");
        JsonConverter converter = new JsonConverter();
        JsonNode jsonRoot = converter.fromAst(root);
        assertInstanceOf(JsonScalar.class, jsonRoot);
        JsonScalar scalar = (JsonScalar)jsonRoot;
        assertEquals("testStr", scalar.asString());
        assertEquals(QuoteStyle.DOUBLE, scalar.getQuoteStyle());
        // TODO: Assert PrimitiveType
    }

    @Test
    void test_convertBoolean() {
        AgnosticScalarNode root = new AgnosticScalarNode(true);
        JsonConverter converter = new JsonConverter();
        JsonNode jsonRoot = converter.fromAst(root);
        assertInstanceOf(JsonScalar.class, jsonRoot);
        JsonScalar scalar = (JsonScalar)jsonRoot;
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

        AgnosticMapNode root = new AgnosticMapNode()
            .put("testStr", "testStr")
            .put("testBool", new AgnosticScalarNode(true))
            .put("testSeq", new AgnosticSequenceNode()
                .add(new AgnosticScalarNode(1))
                .add(new AgnosticScalarNode(2))
                .add(new AgnosticScalarNode(3)));

        // 2. Convert it to a JSON AST

        JsonConverter converter = new JsonConverter();
        JsonNode jsonRoot = converter.fromAst(root);

        // 3. Verify / Validate

        assertInstanceOf(JsonObject.class, jsonRoot);
        JsonObject map = (JsonObject)jsonRoot;

        JsonNode testStr = map.get("testStr");
        assertInstanceOf(JsonScalar.class, testStr);
        JsonScalar testStrScalar = (JsonScalar)testStr;
        assertEquals("testStr", testStrScalar.asString());

        // Check quotes on value and key
        JsonProperty entry = map.getEntries().getFirst();
        assertInstanceOf(String.class, entry.getKey());
        assertInstanceOf(JsonScalar.class, entry.getValue());
        // String key = entry.getKey();
        JsonScalar value = (JsonScalar)entry.getValue();
        // assertEquals(QuoteStyle.DOUBLE, key.getQuoteStyle());
        assertEquals(QuoteStyle.DOUBLE, value.getQuoteStyle());

        JsonNode testBool = map.get("testBool");
        assertInstanceOf(JsonScalar.class, testBool);
        assertEquals(true, ((JsonScalar)testBool).asBoolean());

        // Check quotes on value and key
        entry = map.getEntries().get(1);
        assertInstanceOf(String.class, entry.getKey());
        assertInstanceOf(JsonScalar.class, entry.getValue());
        // key = entry.getKey();
        value = (JsonScalar)entry.getValue();
        // assertEquals(QuoteStyle.DOUBLE, key.getQuoteStyle());
        assertEquals(QuoteStyle.PLAIN, value.getQuoteStyle());

        JsonNode testSeq = map.get("testSeq");
        assertInstanceOf(JsonArray.class, testSeq);
        JsonArray seq = (JsonArray)testSeq;
        assertEquals(3, seq.size());

        JsonNode item1 = seq.get(0);
        assertInstanceOf(JsonScalar.class, item1);
        JsonScalar item1scalar = (JsonScalar)item1;
        assertEquals(1, item1scalar.asInteger());

        JsonNode item3 = seq.get(2);
        assertInstanceOf(JsonScalar.class, item3);
        JsonScalar item3scalar = (JsonScalar)item3;
        assertEquals(3, item3scalar.asInteger());
    }
}
