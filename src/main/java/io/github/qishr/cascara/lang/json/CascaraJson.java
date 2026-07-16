package io.github.qishr.cascara.lang.json;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;

import io.github.qishr.cascara.common.lang.type.TypeReference;
import io.github.qishr.cascara.common.semver.SemVer;
import io.github.qishr.cascara.common.util.JarManifest;
import io.github.qishr.cascara.lang.json.processor.JsonSerializer;
import io.github.qishr.cascara.lang.json.ast.JsonNode;

public final class CascaraJson {

    /// Shared serializer instance backing all façade operations.
    // private static final JsonSerializer SER = new JsonSerializer();

    /// Utility class — not instantiable.
    private CascaraJson() {}

    /// Create a new JSON serializer instance.
    public static JsonSerializer newSerializer() {
        return new JsonSerializer();
    }

    // ---------------------------------------------------------------------
    // READ: String
    // ---------------------------------------------------------------------

    /// Read JSON text into a JVM object of the given type.
    public static <T> T read(String text, Class<T> type) {
        return newSerializer().fromText(text, type);
    }

    /// Read JSON text using a generic type reference.
    public static <T> T read(String text, TypeReference<T> type) {
        return newSerializer().fromText(text, type);
    }

    // ---------------------------------------------------------------------
    // READ: Reader
    // ---------------------------------------------------------------------

    /// Read JSON from a Reader into a JVM object.
    public static <T> T read(Reader reader, Class<T> type) {
        return newSerializer().fromReader(reader, type);
    }

    /// Read JSON from a Reader using a generic type reference.
    public static <T> T read(Reader reader, TypeReference<T> type) {
        return newSerializer().fromReader(reader, type);
    }

    // ---------------------------------------------------------------------
    // READ: InputStream
    // ---------------------------------------------------------------------

    /// Read JSON from an InputStream into a JVM object.
    public static <T> T read(InputStream is, Class<T> type) {
        return newSerializer().fromStream(is, type);
    }

    /// Read JSON from an InputStream using a generic type reference.
    public static <T> T read(InputStream is, TypeReference<T> type) {
        return newSerializer().fromStream(is, type);
    }

    // ---------------------------------------------------------------------
    // WRITE
    // ---------------------------------------------------------------------

    /// Write a JVM object to JSON text.
    public static String write(Object value) {
        return newSerializer().toText(value);
    }

    /// Write a JVM object to a Writer as JSON.
    public static void write(Object value, Writer writer) throws IOException {
        writer.write(newSerializer().toText(value));
    }

    // ---------------------------------------------------------------------
    // AST-level access
    // ---------------------------------------------------------------------

    /// Convert a JVM object into a JSON AST node.
    public static JsonNode toAst(Object value) {
        return newSerializer().toAst(value);
    }

    /// Convert a JSON AST node into a JVM object.
    public static <T> T fromAst(JsonNode ast, Class<T> type) {
        return newSerializer().fromAst(ast, type);
    }

    /// Convert a JSON AST node using a generic type reference.
    public static <T> T fromAst(JsonNode ast, TypeReference<T> type) {
        return newSerializer().fromAst(ast, type);
    }

    /// Return the version of Cascara JSON.
    public static SemVer getVersion() {
        return JarManifest.of(CascaraJson.class).getVersion();
    }
}
