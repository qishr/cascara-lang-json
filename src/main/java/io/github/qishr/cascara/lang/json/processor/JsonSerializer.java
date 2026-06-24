package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.LanguageOptions;
import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.common.lang.processor.AbstractSerializer;
import io.github.qishr.cascara.common.lang.processor.Parser;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.lang.json.JsonOptions;
import io.github.qishr.cascara.lang.json.JsonPrimitiveDelegate;
import io.github.qishr.cascara.lang.json.ast.JsonMapEntryNode;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;

/// Standard implementation for JSON serialization.
public class JsonSerializer extends AbstractSerializer<JsonSerializer,JsonNode,JsonScalarNode,JsonSequenceNode,JsonMapNode,JsonMapEntryNode> {

    private JsonParser parser;
    private JsonOptions options = new JsonOptions();
    private Reporter reporter = new NoOpReporter();

    public JsonSerializer() {
        super(AbstractJsonProcessor.JSON_CONTENT_TYPE_STRING, new JsonFactory(), new JsonPrimitiveDelegate());
    }

    @Override
    public JsonSerializer self() {
        return this;
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
    public JsonSerializer setParser(Parser<JsonNode,?> parser) {
        if (!(parser instanceof JsonParser jsonParser)) {
            throw new SerializerException(GenericDiagnosticCode.ERROR, "Parser must be a JsonParser");
        }
        this.parser = jsonParser;
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
    public JsonNode toAst(Object jvmInstance) {
        return serialize(jvmInstance);
    }

    @Override
    public <C> C fromAst(JsonNode astNode, Class<C> jvmType) {
        return (C) deserialize(astNode, jvmType);
    }

    private JsonParser getParser() {
        if (parser == null) {
            parser = new JsonParser();
            parser.setReporter(reporter);
        }
        return parser;
    }
}