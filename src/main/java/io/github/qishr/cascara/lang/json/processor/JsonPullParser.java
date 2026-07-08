package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.PullParser;
import io.github.qishr.cascara.common.lang.streaming.Event;

import java.io.InputStream;
import java.util.NoSuchElementException;

public class JsonPullParser extends AbstractJsonProcessor<JsonPullParser> implements PullParser {
    private JsonStreamEngine engine;
    private final InputStream input;

    /// Default constructor for SPI.
    public JsonPullParser() {
        input = null;
    }

    public JsonPullParser(InputStream input) {
        this.input = input;
    }

    @Override protected JsonPullParser self() { return this; }

    private void ensureEngine() {
        if (engine == null) {
            this.engine = new JsonStreamEngine(input, getReporter(), getOptions().captureComments());
            this.engine.setReporter(reporter);
        }
    }

    @Override
    public boolean hasNext() {
        try {
            ensureEngine();
            return engine.hashNextEvent();
        } catch (ParserException e) {
            throw new RuntimeException("Error scanning for next streaming event", e);
        }
    }

    @Override
    public Event next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more YAML streaming events available.");
        }
        return engine.nextEvent(); // Throws ParserException, which is a RuntimeException
    }

    @Override
    public void close() throws Exception {
        if (input != null) {
            input.close();
        }
    }
}