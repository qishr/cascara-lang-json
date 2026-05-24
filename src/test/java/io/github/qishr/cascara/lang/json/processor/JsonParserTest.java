package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.SimpleReporter;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.lang.json.JsonDocument;
import io.github.qishr.cascara.lang.json.ast.*;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonParserTest {
    private final JsonParser parser = new JsonParser().setReporter(new SimpleReporter().setLevel(Level.TRACE));;
    private final URI testUri = URI.create("test://file.json");

    @Test
    void testParseObjectWithComments() {
        String input = """
            {
              // This is a comment
              "port": 8080
            }
            """;

        JsonDocument doc = parser.parse(input, testUri);
        JsonMapNode root = (JsonMapNode) doc.getRoot();

        // 1. Get the Entry so we can see the Key
        JsonMapEntryNode entry = root.getEntry(new JsonScalarNode(0, 0, null, "\"port\"", "port", QuoteStyle.DOUBLE));
        assertNotNull(entry, "Entry for 'port' should exist");

        JsonNode keyNode = entry.getKey();
        JsonNode valueNode = entry.getValue();

        // 2. Verify Value logic still works
        assertEquals(8080, ((JsonScalarNode) valueNode).getInteger());

        // 3. Verify Comment is on the KEY (High-Fidelity alignment)
        assertFalse(keyNode.getComments().isEmpty(), "Comment should be attached to the KEY node");
        assertEquals("// This is a comment", keyNode.getComments().get(0).getRawValue());
    }

    @Test
    void testJson5UnquotedKeys() {
        String input = "{ user: \"admin\" }";
        JsonDocument doc = parser.parse(input, testUri);
        JsonMapNode root = (JsonMapNode) doc.getRoot();

        JsonMapEntryNode entry = root.getEntries().get(0);
        JsonScalarNode keyNode = (JsonScalarNode) entry.getKey();

        assertEquals("user", keyNode.getString());
        assertEquals(QuoteStyle.PLAIN, keyNode.getQuoteStyle());
    }

    @Test
    void testNestedSequence() {
        String input = "[1, [2, 3]]";
        JsonDocument doc = parser.parse(input, testUri);
        assertTrue(doc.getRoot() instanceof JsonSequenceNode);

        JsonSequenceNode root = (JsonSequenceNode) doc.getRoot();
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

        JsonDocument doc = parser.parse(input, testUri);
        JsonMapNode root = (JsonMapNode) doc.getRoot();

        JsonMapEntryNode unquotedEntry = root.getEntry("unquoted");
        assertFalse(unquotedEntry.getKey().getComments().isEmpty());

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
        assertFalse(arrayEntry.getKey().getComments().isEmpty());
        assertEquals(" Inline comment", arrayEntry.getKey().getComments().get(0).getString());

        // 4. Verify Trailing Comma didn't break the Sequence
        JsonSequenceNode array = (JsonSequenceNode) nested.get("array");
        assertEquals(2, array.size());
    }

    @Test
    void testEmptyStructures() {
        assertNotNull(parser.parse("{}", testUri).getRoot());
        assertNotNull(parser.parse("[]", testUri).getRoot());
    }

    @Test
    void testStandaloneScalar() {
        // A single string is a valid JSON document
        JsonDocument doc = parser.parse("\"Hello World\"", testUri);
        assertTrue(doc.getRoot() instanceof JsonScalarNode);
        assertEquals("Hello World", ((JsonScalarNode)doc.getRoot()).getString());
    }

    @Test
    void testMalformedJson() {
        // Missing closing brace
        String input = "{ \"key\": \"value\" ";
        // This should not throw an exception, but the Reporter should have errors
        parser.parse(input, testUri);
        // Assuming your reporter has a way to check error counts:
        // assertTrue(reporter.hasErrors());
    }

    @Test
    void testCommentTextStripping() {
        String input = "// This is a line comment\n/* This is a block comment */ { }";
        JsonDocument doc = parser.parse(input, testUri);
        JsonNode root = doc.getRoot();

        // 1. Use the interface type for the list
        List<CommentAstNode> comments = root.getComments();
        assertEquals(2, comments.size());

        // 2. Access via the interface (or cast individual elements if necessary)
        CommentAstNode lineComment = comments.get(0);
        CommentAstNode blockComment = comments.get(1);

        // Assuming getText() is on CommentAstNode
        assertEquals(" This is a line comment", lineComment.getString());
        assertEquals(" This is a block comment ", blockComment.getString());

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

        JsonDocument doc = parser.parse(input, testUri);
        JsonMapNode root = (JsonMapNode) doc.getRoot();

        JsonMapNode level1 = root.getMap("level1");
        JsonMapNode level2 = level1.getMap("level2"); // Wait, level2 is a sequence!

        // This is a great test because it catches if we use the wrong getter
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
        JsonDocument doc = parser.parse(input, testUri);
        JsonNode root = doc.getRoot();

        CommentAstNode comment = root.getComments().get(0);

        // The comment starts on Line 1
        assertEquals(1, comment.getStartLine());
        // The text should preserve the internal structure
        assertTrue(comment.getString().contains("Line 2"));
    }

    @Test
    void testJson5NumericVariations() {
        // JSON5 allows: +.5, -.5, 0x123, 1.
        String input = "[ +.5, 0x123, 1. ]";
        JsonDocument doc = parser.parse(input, testUri);
        JsonSequenceNode seq = (JsonSequenceNode) doc.getRoot();

        assertEquals(0.5, ((JsonScalarNode)seq.get(0)).getDouble());
        // Note: If your current scanNumber doesn't handle 'x',
        // this is where we'll find out!
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

        JsonDocument doc = parser.parse(input, testUri);
        JsonMapNode root = (JsonMapNode) doc.getRoot();

        // 1. Check // style
        CommentAstNode lineComment = root.getEntry("a").getKey().getComments().get(0);
        assertFalse(lineComment.isMultiLine(), "Double-slash should not be multi-line");

        // 2. Check /* */ single line
        CommentAstNode blockSingle = root.getEntry("b").getKey().getComments().get(0);
        assertTrue(blockSingle.isMultiLine(), "Block markers should count as isMultiLine regardless of line count");

        // 3. Check /* */ actual multi-line
        CommentAstNode blockMulti = root.getEntry("c").getKey().getComments().get(0);
        assertTrue(blockMulti.isMultiLine(), "Actual multi-line blocks should be true");
    }
}