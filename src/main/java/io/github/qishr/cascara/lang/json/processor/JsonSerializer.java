// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.json.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.common.lang.processor.AbstractSerializer;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.common.lang.type.TypeReference;
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
        // This constructor is for SPI and cannot take a parameter.
        super(AbstractJsonProcessor.JSON_CONTENT_TYPE_STRING, new JsonNodeFactory(), new JsonOptions());
    }

    @Override
    protected JsonSerializer self() {
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

    /// {@inheritDoc}
    @Override
    public JsonSerializer setParser(AstParser<JsonNode,?> parser) {
        if (!(parser instanceof JsonAstParser JsonAstParser)) {
            throw new SerializerException(GenericDiagnosticCode.ERROR, "Parser must be a JsonAstParser");
        }
        this.parser = JsonAstParser;
        return this;
    }

    /// {@inheritDoc}
    @Override
    public String toText(Object jvmInstance) {
        JsonNode ast = toAst(jvmInstance);
        return new JsonEmitter().setOptions(options).emit(ast);
    }

    /// {@inheritDoc}
    @Override
    public JsonNode toAst(Object jvmInstance) {
        return serialize(jvmInstance);
    }

    /// {@inheritDoc}
    @Override
    public void toWriter(Object jvmInstance, Writer writer) throws IOException {
        JsonNode ast = toAst(jvmInstance);
        String text = new JsonEmitter().setOptions(options).emit(ast);
        writer.write(text);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromText(String text, Class<C> jvmType) {
        JsonNode ast = getParser().parse(text);
        return fromAst(ast, jvmType);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromText(String text, TypeReference<C> typeRef) {
        JsonNode ast = getParser().parse(text);
        return fromAst(ast, typeRef);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromReader(Reader reader, Class<C> jvmType) {
        JsonNode ast = getParser().parse(reader);
        return fromAst(ast, jvmType);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromReader(Reader reader, TypeReference<C> typeRef) {
        JsonNode ast = getParser().parse(reader);
        return fromAst(ast, typeRef);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromStream(InputStream is, Class<C> jvmType) {
        JsonNode ast = getParser().parse(is);
        return fromAst(ast, jvmType);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromStream(InputStream is, TypeReference<C> typeRef) {
        JsonNode ast = getParser().parse(is);
        return fromAst(ast, typeRef);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromAst(JsonNode astNode, Class<C> jvmType) {
        return (C) deserialize(astNode, jvmType);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromAst(JsonNode astNode, TypeReference<C> typeRef) {
        return (C) deserialize(astNode, typeRef);
    }




    private JsonAstParser getParser() {
        if (parser == null) {
            parser = new JsonAstParser();
            parser.setReporter(reporter);
        }
        return parser;
    }
}