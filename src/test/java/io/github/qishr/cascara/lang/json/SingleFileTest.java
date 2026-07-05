package io.github.qishr.cascara.lang.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.json.JsonOptions;
import io.github.qishr.cascara.lang.json.processor.JsonAstParser;

class SingleFileTest {

    private final JsonOptions options = new JsonOptions().setStrict(true);

    private JsonAstParser parser = new JsonAstParser()
            .setOptions(options);
            // .setReporter(new StandardReporter().setLevel(Level.TRACE));

    // @Disabled
    @Test
    void testSingleFileTest() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/medium.json");
        InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(streamReader);
        String content = reader.readAllAsString();

        for (int i = 0; i < 500000; i++) {
            parser = new JsonAstParser();
            parser.parse(content);
        }
    }
}