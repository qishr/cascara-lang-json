module cascara.lang.json {
    requires transitive cascara.common;

    exports io.github.qishr.cascara.lang.json;
    exports io.github.qishr.cascara.lang.json.ast;
    exports io.github.qishr.cascara.lang.json.processor;
    exports io.github.qishr.cascara.lang.json.token;

    // For testing
    opens io.github.qishr.cascara.lang.json;

    provides io.github.qishr.cascara.common.lang.processor.AstConverter
        with io.github.qishr.cascara.lang.json.processor.JsonConverter;

    provides io.github.qishr.cascara.common.lang.processor.Emitter
        with io.github.qishr.cascara.lang.json.processor.JsonEmitter;

    provides io.github.qishr.cascara.common.lang.processor.AstParser
        with io.github.qishr.cascara.lang.json.processor.JsonAstParser;

    provides io.github.qishr.cascara.common.lang.processor.Tokenizer
        with io.github.qishr.cascara.lang.json.processor.JsonTokenizer;

    provides io.github.qishr.cascara.common.lang.processor.Serializer
        with io.github.qishr.cascara.lang.json.processor.JsonSerializer;
}
