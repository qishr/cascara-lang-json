package io.github.qishr.cascara.lang.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.*;
import java.util.stream.Stream;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.processor.JsonEmitter;
import io.github.qishr.cascara.lang.json.processor.JsonAstParser;

class JsonTestSuite {

    private JsonOptions options;
    private JsonAstParser parser;
    private Reporter reporter;

    @BeforeEach
    void init() {
        reporter = new StandardReporter().setLevel(Level.TRACE);
        // options = new JsonOptions();
        parser = new JsonAstParser()
            // .setOptions(options)
            .setReporter(reporter);
    }

    @ParameterizedTest(name = "Validating JSON: {0}")
    @MethodSource("getValidFiles")
    void testValidFiles(String fileName, String content) {
        assertDoesNotThrow(() -> parser.parse(content), "Should have parsed: " + fileName);
    }

    @ParameterizedTest(name = "Invalidating JSON: {0}")
    @MethodSource("getInvalidFiles")
    void testInvalidFiles(String fileName, String content) {
        assertThrows(Exception.class, () -> parser.parse(content), "Should have failed: " + fileName);
    }

    static Stream<Arguments> getValidFiles() throws Exception {
        return scanFolder("src/test/resources/json-suite/valid");
    }

    static Stream<Arguments> getInvalidFiles() throws Exception {
        return scanFolder("src/test/resources/json-suite/invalid");
    }

    private static Stream<Arguments> scanFolder(String pathStr) throws Exception {
        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) return Stream.empty();
        return Files.walk(path)
                .filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".json5"))
                .map(p -> {
                    try { return Arguments.of(p.getFileName().toString(), Files.readString(p)); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
    }

    @ParameterizedTest(name = "Round Trip JSON Stability: {0}")
    @MethodSource("getValidFiles")
    void testRoundTripStability(String fileName, String content) throws Exception {
        // Parse original content (supports either JsonMapNode or JsonSequenceNode roots)
        JsonNode doc = parser.parse(content);

        JsonOptions testOptions = new JsonOptions().setPrettyPrint(true);
        JsonEmitter emitter = new JsonEmitter();
        emitter.setOptions(testOptions);

        // 1. First Emit
        String emitted = emitter.emit(doc);

        // 2. Re-parse emitted content
        JsonNode reParsedDoc = parser.parse(emitted);

        // 3. Second Emit
        String secondEmit = emitter.emit(reParsedDoc);

        if (!emitted.equals(secondEmit)) {
            fail(generateDiffMessage(fileName, emitted, secondEmit));
        }
    }

    private String generateDiffMessage(String fileName, String expected, String actual) {
        String[] expLines = expected.split("\n");
        String[] actLines = actual.split("\n");
        StringBuilder diff = new StringBuilder("\nDiff failure in " + fileName + ":\n");

        int max = Math.max(expLines.length, actLines.length);
        for (int i = 0; i < max; i++) {
            String e = i < expLines.length ? expLines[i] : "<EOF>";
            String a = i < actLines.length ? actLines[i] : "<EOF>";

            if (!e.equals(a)) {
                diff.append(String.format("Line %d:\n  Exp: [%s]\n  Act: [%s]\n", i + 1, e, a));
            }
        }
        return diff.toString();
    }
}