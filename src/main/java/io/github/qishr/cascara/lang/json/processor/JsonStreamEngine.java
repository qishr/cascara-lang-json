package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.common.lang.streaming.EventType;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.token.Token;
import io.github.qishr.cascara.lang.json.exception.JsonDiagnosticCode;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

class JsonStreamEngine {
    private final JsonTokenizer tokenizer;
    private final Deque<Integer> indentStack = new ArrayDeque<>();
    private final Deque<EventType> contextStack = new ArrayDeque<>();

    private Token currentToken;
    private Token bufferedToken;

    private final boolean includeComments;

    private int targetDedentCount = 0;
    private boolean isDocumentEnded = false;
    private boolean rootOpened = false;
    private boolean insideExplicitKey = false;
    private boolean insideBlockScalar = false;
    private final StringBuilder blockScalarBuffer = new StringBuilder();

    JsonStreamEngine(InputStream input, Reporter reporter, boolean captureComments) {
        this.tokenizer = new JsonTokenizer().setReporter(reporter);
        this.tokenizer.open(input);
        this.indentStack.push(0);
        this.contextStack.push(EventType.START_OBJECT);
        this.includeComments = captureComments;
    }

    public JsonStreamEngine setReporter(Reporter reporter) {
        return this;
    }

    boolean hashNextEvent() {
        // If we are already done, do not claim to have events
        return !isDocumentEnded;
    }

    Event nextEvent() throws ParserException {
        return null;

        // if (isDocumentEnded) return null; // Or handle as appropriate

        // if (!rootOpened) {
        //     rootOpened = true;
        //     return new JsonStreamingEvent(1, 1, EventType.START_OBJECT, "");
        // }

        // if (targetDedentCount > 0) {
        //     targetDedentCount--;
        //     EventType closedContext = contextStack.pop();
        //     indentStack.pop();
        //     EventType endType = (closedContext == EventType.START_ARRAY) ? EventType.END_ARRAY : EventType.END_OBJECT;
        //     return new JsonStreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), endType, "");
        // }

        // advanceToken();

        // if (currentToken.getType() == JsonTokenType.DOCUMENT_START) {
        //     // If a document was already in progress, close it
        //     if (rootOpened) {
        //         // You might need to flush contexts here if not already done
        //         return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.END_DOCUMENT, "");
        //     }
        //     // Otherwise, this is the start of the first document
        //     rootOpened = true;
        //     return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_DOCUMENT, "");
        // }

        // if (currentToken == null || currentToken.getType() == JsonTokenType.EOF || currentToken.getType() == JsonTokenType.STREAM_END) {
        //     if (insideBlockScalar) {
        //         insideBlockScalar = false;
        //         blockScalarBuffer.append("\n");
        //         String finalContent = blockScalarBuffer.toString();
        //         blockScalarBuffer.setLength(0);
        //         return new JsonStreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), EventType.VALUE_SCALAR, finalContent);
        //     }
        //     if (contextStack.size() > 0) {
        //         targetDedentCount = contextStack.size();
        //         return nextEvent();
        //     }
        //     isDocumentEnded = true;
        //     return new JsonStreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), EventType.END_DOCUMENT, "");
        // }

        // // 1. Structural drops flush block scalars naturally
        // if (currentToken.getType() == JsonTokenType.DEDENT || currentToken.getType() == JsonTokenType.BLOCK_END) {
        //     if (insideBlockScalar) {
        //         insideBlockScalar = false;
        //         blockScalarBuffer.append("\n");
        //         String finalContent = blockScalarBuffer.toString();
        //         blockScalarBuffer.setLength(0);

        //         // Buffer this token so the layout engine executes it on the next loop turn
        //         bufferedToken = currentToken;
        //         return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.VALUE_SCALAR, finalContent);
        //     }

        //     if (indentStack.size() > 1) {
        //         indentStack.pop();
        //         EventType closedContext = contextStack.pop();
        //         EventType endType = (closedContext == EventType.START_ARRAY) ? EventType.END_ARRAY : EventType.END_OBJECT;
        //         return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), endType, "");
        //     }
        //     return nextEvent();
        // }

        // if (currentToken.getType() == JsonTokenType.INDENT) {
        //     // If we are gathering a block scalar, ignore indentation increases
        //     // as they are just deep text line content inside the literal block.
        //     if (insideBlockScalar) {
        //         return nextEvent();
        //     }

        //     int indentWidth = currentToken.getStartColumn() - 1;

        //     if (indentWidth > indentStack.peek()) {
        //         indentStack.push(indentWidth);

        //         if (isNextTokenSequenceIndicator()) {
        //             contextStack.push(EventType.START_ARRAY);
        //             return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_ARRAY, "");
        //         } else {
        //             contextStack.push(EventType.START_OBJECT);
        //             return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_OBJECT, "");
        //         }
        //     }
        //     return nextEvent();
        // }

        // // ====================================================================
        // // Flow Style Structural Interceptors
        // // ====================================================================

        // // Flow Sequences [...]
        // if (currentToken.getType() == JsonTokenType.SEQUENCE_START) {
        //     contextStack.push(EventType.START_ARRAY);
        //     // Push a sentinel placeholder to protect the block indentation tracking
        //     indentStack.push(-1);
        //     return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_ARRAY, "");
        // }

        // if (currentToken.getType() == JsonTokenType.SEQUENCE_END) {
        //     if (contextStack.peek() == EventType.START_ARRAY) {
        //         contextStack.pop();
        //         indentStack.pop();
        //         return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.END_ARRAY, "");
        //     }
        //     throw new ParserException(currentToken, JsonDiagnosticCode.UNEXPECTED_CLOSE_BRACKET);
        // }

        // // Flow Maps {...}
        // if (currentToken.getType() == JsonTokenType.MAP_START) {
        //     contextStack.push(EventType.START_OBJECT);
        //     indentStack.push(-1);
        //     return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_OBJECT, "");
        // }

        // if (currentToken.getType() == JsonTokenType.MAP_END) {
        //     if (contextStack.peek() == EventType.START_OBJECT) {
        //         contextStack.pop();
        //         indentStack.pop();
        //         return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.END_OBJECT, "");
        //     }
        //     throw new ParserException(currentToken, JsonDiagnosticCode.UNEXPECTED_CLOSE_BRACE);
        // }

        // if (currentToken.getType() == JsonTokenType.COMMA) { // ','
        //     // Comma separates elements/pairs explicitly; advance immediately
        //     return nextEvent();
        // }

        // if (currentToken.getType() == JsonTokenType.COMMENT) {
        //     if (includeComments) {
        //         return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.COMMENT, currentToken.getContent());
        //     }
        //     return nextEvent();
        // }

        // if (currentToken.getType() == JsonTokenType.KEY_INDICATOR) {
        //     insideExplicitKey = true;
        //     return nextEvent();
        // }

        // if (currentToken.getType() == JsonTokenType.SCALAR) {
        //     String value = currentToken.getContent();

        //     if (insideExplicitKey) {
        //         insideExplicitKey = false;
        //         return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.FIELD_NAME, value);
        //     }

        //     if (isNextTokenValueIndicator()) {
        //         return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.FIELD_NAME, value);
        //     }

        //     return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.VALUE_SCALAR, value);
        // }

        // if (currentToken.getType() == JsonTokenType.SEQUENCE_ENTRY_INDICATOR) {
        //     if (contextStack.peek() != EventType.START_ARRAY) {
        //         contextStack.push(EventType.START_ARRAY);
        //         indentStack.push(currentToken.getStartColumn() - 1);
        //         return new JsonStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_ARRAY, "");
        //     }
        //     return nextEvent();
        // }

        // if (currentToken.getType() == JsonTokenType.VALUE_INDICATOR
        //         || currentToken.getType() == JsonTokenType.NEWLINE
        //         || currentToken.getType() == JsonTokenType.STREAM_START) {
        //     return nextEvent();
        // }

        // throw new ParserException(currentToken, JsonDiagnosticCode.UNEXPECTED_TOKEN, currentToken.getType());
    }

    // private boolean isNextTokenValueIndicator() throws ParserException {
    //     if (bufferedToken == null) {
    //         bufferedToken = tokenizer.nextToken();
    //     }
    //     return bufferedToken != null && bufferedToken.getType() == JsonTokenType.VALUE_INDICATOR;
    // }

    // private boolean isNextTokenSequenceIndicator() throws ParserException {
    //     if (bufferedToken == null) {
    //         bufferedToken = tokenizer.nextToken();
    //     }
    //     return bufferedToken != null && bufferedToken.getType() == JsonTokenType.SEQUENCE_ENTRY_INDICATOR;
    // }

    private void advanceToken() throws ParserException {
        if (bufferedToken != null) {
            this.currentToken = bufferedToken;
            this.bufferedToken = null;
        } else {
            this.currentToken = tokenizer.nextToken();
        }
    }
}