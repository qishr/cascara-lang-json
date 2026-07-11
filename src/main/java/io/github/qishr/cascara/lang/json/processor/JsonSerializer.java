package io.github.qishr.cascara.lang.json.processor;

import java.io.InputStream;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.common.lang.processor.AbstractSerializer;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.lang.json.ast.JsonMapEntryNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonNodeFactory;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;
import io.github.qishr.cascara.lang.json.util.JsonOptions;

/// Standard implementation for JSON serialization.
public class JsonSerializer extends AbstractSerializer<JsonSerializer,JsonNode,JsonScalarNode,JsonSequenceNode,JsonMapNode,JsonMapEntryNode,String> {

    private JsonAstParser parser;
    private JsonOptions options = new JsonOptions();
    private Reporter reporter = new NoOpReporter();

    public JsonSerializer() {
        // super(AbstractJsonProcessor.JSON_CONTENT_TYPE_STRING, new JsonNodeFactory(), new JsonPrimitiveDelegate());

        // TODO: Don't create a JsonPrimitiveDescriptor with defult options.
        // AbstractSerializer needs its setOptions to work.
        // This constructor is for SPI and cannot take a parameter.
        super(AbstractJsonProcessor.JSON_CONTENT_TYPE_STRING, new JsonNodeFactory(), new JsonOptions());
    }

    @Override
    public JsonSerializer self() {
        return this;
    }

    @Override
    protected String serializeKey(Object key) {
        if (key instanceof String s) {
            return s;
        }
        return String.valueOf(key);
    }

    @Override
    public ContentType getContentType() {
        return AbstractJsonProcessor.JSON_CONTENT_TYPE;
    }

    /// {@inheritDoc}
    @Override
    public JsonSerializer setReporter(Reporter reporter) {
        this.reporter = reporter;
        return this;
    }

    /// {@inheritDoc}
    @Override
    public JsonSerializer setOptions(LanguageOptions<?> options) {
        this.options = (JsonOptions) options;
        return this;
    }

    @Override
    public JsonSerializer setParser(AstParser<JsonNode,?> parser) {
        if (!(parser instanceof JsonAstParser JsonAstParser)) {
            throw new SerializerException(GenericDiagnosticCode.ERROR, "Parser must be a JsonAstParser");
        }
        this.parser = JsonAstParser;
        return this;
    }

    @Override
    public String toText(Object jvmInstance) {
        JsonNode ast = toAst(jvmInstance);
        return new JsonEmitter().setOptions(options).emit(ast);
    }

    @Override
    public <C> C fromText(String text, Class<C> jvmType) {
        JsonNode ast = getParser().parse(text);
        return fromAst(ast, jvmType);
    }

    @Override
    public <C> C fromStream(InputStream is, Class<C> jvmType) {
        JsonNode ast = getParser().parse(is);
        return fromAst(ast, jvmType);
    }

    @Override
    public JsonNode toAst(Object jvmInstance) {
        return serialize(jvmInstance);
    }

    @Override
    public <C> C fromAst(JsonNode astNode, Class<C> jvmType) {
        return (C) deserialize(astNode, jvmType);
    }

    private JsonAstParser getParser() {
        if (parser == null) {
            parser = new JsonAstParser();
            parser.setReporter(reporter);
        }
        return parser;
    }
}