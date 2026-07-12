package io.github.qishr.cascara.lang.json.util;

import java.io.*;
import java.nio.charset.StandardCharsets;

import io.github.qishr.cascara.lang.json.processor.JsonAstParser;

public class ProfilingHarness {

    public static void main(String[] args) throws Exception {
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

        JsonOptions options = new JsonOptions()
            .setUseSimd(true)
            .setAllowUnicode(false)
            .setAllowComments(false)
            .setCaptureComments(false)
            .setAllowSingleQuotedStrings(false);


        for (int i = 0; i < 500_000; i++) {
            JsonAstParser parser = new JsonAstParser().setOptions(options);
            parser.parse(content);
        }
    }
}
