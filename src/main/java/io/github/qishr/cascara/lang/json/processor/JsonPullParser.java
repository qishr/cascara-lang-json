package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.PullParser;
import io.github.qishr.cascara.common.lang.streaming.StreamingEvent;
import io.github.qishr.cascara.lang.json.internal.JsonStreamEngine;

import java.io.InputStream;
import java.util.NoSuchElementException;

public class JsonPullParser extends AbstractJsonProcessor<JsonPullParser> implements PullParser {
    private JsonStreamEngine engine;
    private final InputStream input;

    /// Default constructor for SPI.
    public JsonPullParser() {
        this.input = null;
    }

    public JsonPullParser(InputStream input) {
        this.input = input;
    }

    @Override
    protected JsonPullParser self() {
        return this;
    }

    private void ensureEngine() {
        if (engine == null) {
            this.engine = new JsonStreamEngine(input, getOptions(), getReporter());
        }
    }

    @Override
    public boolean hasNext() {
        try {
            ensureEngine();
            return engine.hasNextEvent();
        } catch (ParserException e) {
            throw new RuntimeException("Error scanning for next JSON streaming event", e);
        }
    }

    @Override
    public StreamingEvent next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more JSON streaming events available.");
        }
        return engine.nextEvent(); // ParserException is unchecked
    }

    @Override
    public void close() throws Exception {
        if (input != null) {
            input.close();
        }
    }
}
