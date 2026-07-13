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

        JsonMapNode root = (JsonMapNode) parser.parse(input);

        // 1. Get the Entry so we can see the Key

        // JsonMapEntryNode entry = root.getEntry(new JsonScalarNode(0, 0, PrimitiveType.STRING, "\"port\"", "port", QuoteStyle.DOUBLE, false, null));
        // JsonMapEntryNode entry = root.getEntry(new JsonScalarNode("port", QuoteStyle.DOUBLE, false, null));
        JsonMapEntryNode entry = root.getEntry("port");

        assertNotNull(entry, "Entry for 'port' should exist");

        String keyNode = entry.getKey();
        JsonNode valueNode = entry.getValue();

        // 2. Verify Value logic still works
        assertEquals(8080, ((JsonScalarNode) valueNode).asInteger());

        // TODO: comments
        // 3. Verify Comment is on the KEY (High-Fidelity alignment)
        // assertFalse(keyNode.getComments().isEmpty(), "Comment should be attached to the KEY node");
        // assertEquals("// This is a comment", keyNode.getComments().get(0).getLexeme());
    }

    @Test
    void testJson5UnquotedKeys() {
        String input = "{ user: \"admin\" }";

        parser.setOptions(JsonOptions.JSON5);

        JsonMapNode root = (JsonMapNode) parser.parse(input);

        JsonMapEntryNode entry = root.getEntries().get(0);
        String keyNode = entry.getKey();

        assertEquals("user", keyNode);
        // assertEquals(QuoteStyle.PLAIN, keyNode.getQuoteStyle());
    }

    @Test
    void testNestedSequence() {
        String input = "[1, [2, 3]]";
        JsonNode doc = parser.parse(input);
        assertTrue(doc instanceof JsonSequenceNode);

        JsonSequenceNode root = (JsonSequenceNode) doc;
        assertEquals(2, root.size());
        assertTrue(root.get(1) instanceof JsonSequenceNode);
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

        JsonMapNode root = (JsonMapNode) parser.parse(input);

        JsonMapEntryNode unquotedEntry = root.getEntry("unquoted");
        // TODO: key comments -> entry comments?
        // assertFalse(unquotedEntry.getKey().getComments().isEmpty());

        // 1. Verify Header Comment (Should attach to the Root Object)
        // assertFalse(root.getComments().isEmpty());
        // assertEquals("// Header comment", root.getComments().get(0).getText());

        // 2. Verify Nested Object & Unquoted Key
        JsonMapNode nested = root.getMap("nested");
        JsonMapEntryNode entry = nested.getEntry("key");
        assertNotNull(entry);

        // 3. Verify Inline Comment (Clings to the node that follows it or the entry)
        // In our current logic, it will buffer and attach to "array"
        JsonMapEntryNode arrayEntry = nested.getEntry("array");

        // TODO: Key comments
        // assertFalse(arrayEntry.getKey().getComments().isEmpty());
        // assertEquals(" Inline comment", arrayEntry.getKey().getComments().get(0).asString());

        // 4. Verify Trailing Comma didn't break the Sequence
        JsonSequenceNode array = (JsonSequenceNode) nested.get("array");
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
        assertTrue(doc instanceof JsonScalarNode);
        assertEquals("Hello World", ((JsonScalarNode)doc).asString());
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

        JsonMapNode root = (JsonMapNode) parser.parse(input);

        JsonMapNode level1 = root.getMap("level1");
        // JsonMapNode level2 = level1.getMap("level2");

        assertTrue(level1.get("level2") instanceof JsonSequenceNode);
        JsonSequenceNode seq = level1.getSequence("level2");
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
        JsonSequenceNode seq = (JsonSequenceNode) parser.parse(input);

        assertEquals(0.5, ((JsonScalarNode)seq.get(0)).asDouble());
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

        JsonMapNode root = (JsonMapNode) parser.parse(input);

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
        JsonMapNode rootMap = (JsonMapNode)root;

        // 2. Get the value for "matrix"
        JsonNode matrixNode = rootMap.get("matrix");
        assertTrue(matrixNode instanceof JsonSequenceNode);

        // 3. Now we are at the outer sequence: [[1, 2], [3, 4]]
        JsonSequenceNode outer = (JsonSequenceNode) matrixNode;
        assertEquals(2, outer.size());

        // 4. Get the first inner sequence: [1, 2]
        assertTrue(outer.get(0) instanceof JsonSequenceNode);
        JsonSequenceNode inner = (JsonSequenceNode) outer.get(0);
        assertEquals(2, inner.size());

        // 5. Verify a leaf value
        assertEquals("1", inner.get(0).asString());
    }

    @Test
    void test_stringContaining_quotes() {
        String text = "{\"name\": \"one \\\"two\\\" three\"}";
        JsonAstParser parser = new JsonAstParser();
        JsonMapNode json = (JsonMapNode)parser.parse(text);
        String name = json.getString("name");
        assertEquals("one \"two\" three", name);
    }

    @Test
    void test_array_justMinus() {
        String text = "[-]";
        assertThrows(Exception.class, () -> parser.parse(text), "Should have failed");
    }

    @Test
    void test_array_commaAfterClose() {
        String text = "[\"\"],";
        assertThrows(Exception.class, () -> parser.parse(text), "Should have failed");
    }

    @Test
    void test_string_backslash() {
        String text = "[\"\\00\"]";
        assertThrows(Exception.class, () -> parser.parse(text), "Should have failed");
    }

    @Test void test_i_object_key_lone_2nd_surrogate() {
        assertThrows(Exception.class, () -> parser.parse("{\"\\uDFAA\":0}"));
    }

    @Test void test_i_string_1st_surrogate_but_2nd_missing() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uDADA\"]"));
    }

    @Test void test_i_string_1st_valid_surrogate_2nd_invalid() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uD888\\u1234\"]"));
    }

    @Test void test_i_string_UTF16LE_with_BOM() {
        assertThrows(Exception.class, () -> parser.parse("\"\uFEFF\""));
    }

    @Test void test_i_string_UTF8_invalid_sequence() {
        assertThrows(Exception.class, () -> parser.parse("[\"E697A5D188FA\"]"));
    }

    @Test void test_i_string_UTF8_surrogate_U_D800() {
        assertThrows(Exception.class, () -> parser.parse("[\"EDA080\"]"));
    }

    @Test void test_i_string_incomplete_surrogate_and_escape_valid() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uD800\n\"]"));
    }

    @Test void test_i_string_incomplete_surrogate_pair() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uDd1ea\"]"));
    }

    @Test void test_i_string_incomplete_surrogates_escape_valid() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uD800\\uD800\n\"]"));
    }

    @Test void test_i_string_invalid_lonely_surrogate() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\ud800\"]"));
    }

    @Test void test_i_string_invalid_surrogate() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\ud800abc\"]"));
    }

    @Test void test_i_string_invalid_utf8() {
        assertThrows(Exception.class, () -> parser.parse("[\"FF\"]"));
    }

    @Test void test_i_string_inverted_surrogates_U_1D11E() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uDd1e\\uD834\"]"));
    }

    @Test void test_i_string_iso_latin_1() {
        assertThrows(Exception.class, () -> parser.parse("[\"E9\"]"));
    }

    @Test void test_i_string_lone_second_surrogate() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uDFAA\"]"));
    }

    @Test void test_i_string_lone_utf8_continuation_byte() {
        assertThrows(Exception.class, () -> parser.parse("[\"81\"]"));
    }

    @Test void test_i_string_not_in_unicode_range() {
        assertThrows(Exception.class, () -> parser.parse("[\"F4BFBFBF\"]"));
    }

    @Test void test_i_string_overlong_sequence_2_bytes() {
        assertThrows(Exception.class, () -> parser.parse("[\"C0AF\"]"));
    }

    @Test void test_i_string_overlong_sequence_6_bytes() {
        assertThrows(Exception.class, () -> parser.parse("[\"FC83BFBFBFBF\"]"));
    }

    @Test void test_i_string_overlong_sequence_6_bytes_null() {
        assertThrows(Exception.class, () -> parser.parse("[\"FC8080808080\"]"));
    }

    @Test void test_i_string_truncated_utf8() {
        assertThrows(Exception.class, () -> parser.parse("[\"E0FF\"]"));
    }

    @Test void test_i_string_utf16BE_no_BOM() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\u0000\\u00E9\"]"));
    }

    @Test void test_i_string_utf16LE_no_BOM() {
        assertThrows(Exception.class, () -> parser.parse("[\"\\uE900\"]"));
    }
}