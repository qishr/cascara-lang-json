package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;
import io.github.qishr.cascara.lang.json.util.JsonOptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JsonTokenizerTest {
    private JsonTokenizer tokenizer;

    @BeforeEach
    void setup() {
        tokenizer = new JsonTokenizer()
            .setReporter(new StandardReporter().setLevel(Level.TRACE))
            .setOptions(JsonOptions.JSON5.duplicate().setCaptureComments(true));
    }

    @Test
    void testBasicTokens() {
        String input = "{ \"key\": 123, unquoted: true }";
        List<JsonToken> tokens = tokenizer.tokenize(input);

        // Expected: {, STRING, :, NUMBER, ,, IDENTIFIER, :, BOOLEAN, }, EOF
        assertEquals(10, tokens.size());
        assertEquals(JsonTokenType.LEFT_BRACE, tokens.get(0).getType());
        assertEquals("key", tokens.get(1).getContent());
        assertEquals("123", tokens.get(3).getContent());
        assertEquals(JsonTokenType.IDENTIFIER, tokens.get(5).getType());
        assertEquals("unquoted", tokens.get(5).getContent());
        assertEquals(JsonTokenType.EOF, tokens.get(9).getType());
    }

    @Test
    void testCoordinates() {
        String input = "\n  \"line2\"";
        List<JsonToken> tokens = tokenizer.tokenize(input);

        JsonToken stringTok = tokens.get(0);
        assertEquals(2, stringTok.getStartLine());
        assertEquals(3, stringTok.getStartColumn()); // Account for 2 spaces
    }

    @Test
    void testJson5NumericTokens() {
        // JSON5 allows:
        // 1. Leading plus signs (+)
        // 2. Leading/trailing decimal points (.5, 1.)
        // 3. Hexadecimal (0x123)
        String input = "[ +.5, 0x123, 1. ]";

        // JsonTokenizer tokenizer = new JsonTokenizer();

        List<JsonToken> tokens = tokenizer.tokenize(input);

        // We expect: [ (LEFT_BRACKET), NUMBER, (COMMA), NUMBER, (COMMA), NUMBER, ] (RIGHT_BRACKET)

        // 1. Check +.5
        assertEquals(JsonTokenType.NUMBER, tokens.get(1).getType(), "Expected +.5 to be a NUMBER");
        assertEquals("+.5", tokens.get(1).getLexeme());

        // 2. Check 0x123
        assertEquals(JsonTokenType.NUMBER, tokens.get(3).getType(), "Expected 0x123 to be a NUMBER");
        assertEquals("0x123", tokens.get(3).getLexeme());

        // 3. Check 1.
        assertEquals(JsonTokenType.NUMBER, tokens.get(5).getType(), "Expected 1. to be a NUMBER");
        assertEquals("1.", tokens.get(5).getLexeme());
    }

    @Test
    void testCommentValueStripping() {
        String input = "// Line comment\n/* Block\ncomment */";

        List<JsonToken> tokens = tokenizer.tokenize(input);

        // tokens.get(0) is the // Line comment
        // Lexeme should be the raw source
        assertEquals("// Line comment", tokens.get(0).getLexeme());
        // Value should be JUST the text (failing here)
        assertEquals(" Line comment", tokens.get(0).getContent(),
            "Single-line comment value should not include slashes");

        // tokens.get(1) is the /* Block comment */
        assertEquals("/* Block\ncomment */", tokens.get(1).getLexeme());
        assertEquals(" Block\ncomment ", tokens.get(1).getContent(),
            "Multi-line comment value should not include /* or */");
    }

    @Test
    void testCommentValueStripping2() {
        String input = "// Line comment";

        List<JsonToken> tokens = tokenizer.tokenize(input);

        JsonToken comment = tokens.get(0);

        assertEquals("// Line comment", comment.getLexeme());

        assertEquals(" Line comment", comment.getContent(),
            "The token 'value' should have markers stripped, while 'lexeme' keeps them.");
    }
}