package io.github.qishr.cascara.lang.json.processor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.util.JsonOptions;
import io.github.qishr.cascara.lang.json.util.ProfilingHarness;

public class MediumTest {
    @Test
    void mediumTest() throws IOException {
        // Load medium.json from classpath
        InputStream inputStream = ProfilingHarness.class.getResourceAsStream("/medium.json");
        if (inputStream == null) {
            throw new FileNotFoundException("medium.json not found on classpath");
        }

        // Read entire file into a String
        StringBuilder sb = new StringBuilder(4096);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        String content = sb.toString();

        JsonAstParser parser = new JsonAstParser()
            .setOptions(new JsonOptions()
                .setUseSimd(true)
                // .setAllowUnicode(true)
            )
            .setReporter(new StandardReporter().setLevel(Level.TRACE));

        JsonNode doc = parser.parse(content);
        if (doc instanceof JsonMapNode map) {
            if (map.get("status") instanceof JsonScalarNode status) {
                Object o = status.getPrimitive();
                assertInstanceOf(Boolean.class, o);
            }
        }
    }
}
