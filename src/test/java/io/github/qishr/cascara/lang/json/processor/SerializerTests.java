package io.github.qishr.cascara.lang.json.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;

public class SerializerTests {
    @Test
    void test_serializeStackTraceElement() {
        StackTraceElement original = new StackTraceElement("className1", "methodName1", "fileName1", 1);

        JsonSerializer serializer = new JsonSerializer();

        serializer.setReporter(
            new StandardReporter()
                .setLevel(Level.DEBUG)
                .setStackTraceEnabled(true)
        );

        String json = serializer.toString(original);

        System.out.println(json);

        StackTraceElement ste = serializer.fromString(json, StackTraceElement.class);
        assertEquals("className1", ste.getClassName());

    }
}
