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

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.SilentCollectingReporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.lang.json.ast.*;
import io.github.qishr.cascara.lang.json.util.JsonOptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonAstParserTest {
    private JsonAstParser parser;

    @BeforeEach
    void init() {
        parser = new JsonAstParser().setReporter(new StandardReporter().setLevel(Level.TRACE));
    }

    @Test
    void testParseObjectWithComments() {
        String input = """
            {
              // This is a comment
              "port": 8080
            }
            """;

        parser.setOptions(JsonOptions.JSON5.duplicate().setCaptureComments(true));

        JsonObject root = (JsonObject) parser.parse(input);

        // 1. Get the Entry so we can see the Key

        JsonProperty entry = root.getEntry("port");

        assertNotNull(entry, "Entry for 'port' should exist");

        JsonNode valueNode = entry.getValue();

        // 2. Verify Value logic still works
        assertEquals(8080, ((JsonScalar) valueNode).asInteger());

        // TODO: Make this work
        // // 3. Verify Comment is on the entry
        // assertFalse(entry.getComments().isEmpty(), "Comment should be attached to the KEY node");
        // assertEquals("// This is a comment", entry.getComments().get(0).getLexeme());
    }

    @Test
    void testJson5UnquotedKeys() {
        String input = "{ user: \"admin\" }";

        parser.setOptions(JsonOptions.JSON5);

        JsonObject root = (JsonObject) parser.parse(input);

        JsonProperty entry = root.getEntries().get(0);
        String keyNode = entry.getKey();

        assertEquals("user", keyNode);
        // assertEquals(QuoteStyle.PLAIN, keyNode.getQuoteStyle());
    }

    @Test
    void testNestedSequence() {
        String input = "[1, [2, 3]]";
        JsonNode doc = parser.parse(input);
        assertTrue(doc instanceof JsonArray);

        JsonArray root = (JsonArray) doc;
        assertEquals(2, root.size());
        assertTrue(root.get(1) instanceof JsonArray);
    }

    @Test
    void testComplexJson5WithMultipleComments() {
        String input = """
            // Header comment
            {
                /* Multi-line
                   block */
                "nested": {
                    key: "value", // Inline comment
                    array: [1, 2, ], // Trailing comma
                },
                unquoted: true
            }
            """;

        parser.setOptions(JsonOptions.JSON5.duplicate().setCaptureComments(true));

        JsonObject root = (JsonObject) parser.parse(input);

        // JsonProperty unquotedEntry = root.getEntry("unquoted");
        // TODO: key comments -> entry comments?
        // assertFalse(unquotedEntry.getKey().getComments().isEmpty());

        // 1. Verify Header Comment (Should attach to the Root Object)
        // assertFalse(root.getComments().isEmpty());
        // assertEquals("// Header comment", root.getComments().get(0).getText());

        // 2. Verify Nested Object & Unquoted Key
        JsonObject nested = root.getMap("nested");
        JsonProperty entry = nested.getEntry("key");
        assertNotNull(entry);

        // 3. Verify Inline Comment (Clings to the node that follows it or the entry)
        // In our current logic, it will buffer and attach to "array"
        JsonProperty arrayEntry = nested.getEntry("array");
        assertNotNull(arrayEntry);

        // TODO: Key comments
        // assertFalse(arrayEntry.getKey().getComments().isEmpty());
        // assertEquals(" Inline comment", arrayEntry.getKey().getComments().get(0).asString());

        // 4. Verify Trailing Comma didn't break the Sequence
        JsonArray array = (JsonArray) nested.get("array");
        assertEquals(2, array.size());
    }

    @Test
    void testEmptyStructures() {
        assertNotNull(parser.parse("{}"));
        assertNotNull(parser.parse("[]"));
    }

    @Test
    void testStandaloneScalar() {
        // A single string is a valid JSON document
        JsonNode doc = parser.parse("\"Hello World\"");
        assertTrue(doc instanceof JsonScalar);
        assertEquals("Hello World", ((JsonScalar)doc).asString());
    }

    @Test
    void testMalformedJson() {
        // Missing closing brace
        String input = "{ \"key\": \"value\" ";
        // This should not throw an exception, but the Reporter should have errors
        SilentCollectingReporter reporter = new SilentCollectingReporter();
        parser.setReporter(reporter);
        parser.parse(input);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void testCommentTextStripping() {
        String input = "// This is a line comment\n/* This is a block comment */ { }";

        parser.setOptions(JsonOptions.JSON5.duplicate().setCaptureComments(true));

        JsonNode root = parser.parse(input);

        // 1. Use the interface type for the list
        List<CommentAstNode> comments = root.getComments();
        assertEquals(2, comments.size());

        // 2. Access via the interface (or cast individual elements if necessary)
        CommentAstNode lineComment = comments.get(0);
        CommentAstNode blockComment = comments.get(1);

        // Assuming getText() is on CommentAstNode
        assertEquals(" This is a line comment", lineComment.asString());
        assertEquals(" This is a block comment ", blockComment.asString());

        // 3. Verify they are actually JsonNodes too
        assertTrue(lineComment instanceof JsonNode);
        assertEquals(1, lineComment.getStartLine());
    }

    @Test
    void testDeepNestingAndTrailingCommas() {
        String input = """
            {
                "level1": {
                    "level2": [
                        { "id": 1, }, // Trailing comma in map
                        { "id": 2  }
                    , ], // Trailing comma in sequence
                }
            }
            """;

        parser.setOptions(JsonOptions.JSON5);

        JsonObject root = (JsonObject) parser.parse(input);

        JsonObject level1 = root.getMap("level1");
        // JsonMapNode level2 = level1.getMap("level2");

        assertTrue(level1.get("level2") instanceof JsonArray);
        JsonArray seq = level1.getSequence("level2");
        assertEquals(2, seq.size());
    }

    @Test
    void testMultiLineCommentCoordinates() {
        String input = """
            /* Line 1
            Line 2
            */
            { "a": 1 }
            """;

        parser.setOptions(JsonOptions.JSON5.duplicate().setCaptureComments(true));

        JsonNode root = parser.parse(input);

        CommentAstNode comment = root.getComments().get(0);

        // The comment starts on Line 1
        assertEquals(1, comment.getStartLine());
        // The text should preserve the internal structure
        assertTrue(comment.asString().contains("Line 2"));
    }

    @Test
    void testJson5NumericVariations() {
        parser.setOptions(JsonOptions.JSON5.duplicate().setCaptureComments(true));

        // JSON5 allows: +.5, -.5, 0x123, 1.
        String input = "[ +.5, 0x123, 1. ]";
        JsonArray seq = (JsonArray) parser.parse(input);

        assertEquals(0.5, ((JsonScalar)seq.get(0)).asDouble());
    }

    @Test
    void testCommentMultiLineProperty() {
        String input = """
            {
                // Line comment
                "a": 1,
                /* Block comment */
                "b": 2,
                /* Multi-line
                   block */
                "c": 3
            }
            """;

        parser.setOptions(JsonOptions.JSON5.duplicate().setCaptureComments(true));

        JsonObject root = (JsonObject) parser.parse(input);
        assertNotNull(root);

        // TODO: Key comments:

        // // 1. Check // style
        // CommentAstNode lineComment = root.getEntry("a").getKey().getComments().get(0);
        // assertFalse(lineComment.isMultiLine(), "Double-slash should not be multi-line");

        // // 2. Check /* */ single line
        // CommentAstNode blockSingle = root.getEntry("b").getKey().getComments().get(0);
        // assertTrue(blockSingle.isMultiLine(), "Block markers should count as isMultiLine regardless of line count");

        // // 3. Check /* */ actual multi-line
        // CommentAstNode blockMulti = root.getEntry("c").getKey().getComments().get(0);
        // assertTrue(blockMulti.isMultiLine(), "Actual multi-line blocks should be true");
    }

    @Test
    void testNestedFlowCollectionsInBlock() throws Exception {
        String json = "{\"matrix\": [[1, 2], [3, 4]]}";

        JsonNode root = parser.parse(json);
        JsonObject rootMap = (JsonObject)root;

        // 2. Get the value for "matrix"
        JsonNode matrixNode = rootMap.get("matrix");
        assertTrue(matrixNode instanceof JsonArray);

        // 3. Now we are at the outer sequence: [[1, 2], [3, 4]]
        JsonArray outer = (JsonArray) matrixNode;
        assertEquals(2, outer.size());

        // 4. Get the first inner sequence: [1, 2]
        assertTrue(outer.get(0) instanceof JsonArray);
        JsonArray inner = (JsonArray) outer.get(0);
        assertEquals(2, inner.size());

        // 5. Verify a leaf value
        assertEquals("1", inner.get(0).asString());
    }
}