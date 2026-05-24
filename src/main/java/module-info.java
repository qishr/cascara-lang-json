module cascara.lang.json {
    requires transitive cascara.common;

    exports io.github.qishr.cascara.lang.json;
    exports io.github.qishr.cascara.lang.json.ast;
    exports io.github.qishr.cascara.lang.json.exception;
    exports io.github.qishr.cascara.lang.json.processor;
    exports io.github.qishr.cascara.lang.json.token;

    provides io.github.qishr.cascara.common.lang.processor.AstConverter
        with io.github.qishr.cascara.lang.json.processor.JsonConverter;
    provides io.github.qishr.cascara.common.lang.processor.Emitter
        with io.github.qishr.cascara.lang.json.processor.JsonEmitter;
    provides io.github.qishr.cascara.common.lang.processor.Parser
        with io.github.qishr.cascara.lang.json.processor.JsonParser;
}
