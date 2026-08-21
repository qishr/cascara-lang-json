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


package io.github.qishr.cascara.lang.json.internal;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.streaming.StreamingEvent;
import io.github.qishr.cascara.lang.json.diagnostic.JsonDiagnosticCode;
import io.github.qishr.cascara.lang.json.processor.JsonTokenizer;
import io.github.qishr.cascara.lang.json.streaming.JsonStreamingEvent;
import io.github.qishr.cascara.lang.json.streaming.JsonStreamingEventType;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;
import io.github.qishr.cascara.lang.json.util.JsonOptions;

public class JsonStreamEngine {
    private final JsonTokenizer tokenizer;
    private final Deque<JsonStreamingEventType> contextStack = new ArrayDeque<>();

    private JsonToken currentToken;
    private JsonToken bufferedToken;

    private boolean rootOpened = false;
    private boolean documentEnded = false;

    public JsonStreamEngine(InputStream input, JsonOptions options, Reporter reporter) {
        this.tokenizer = new JsonTokenizer().setReporter(reporter);
        this.tokenizer.open(input);
    }

    public boolean hasNextEvent() {
        return !documentEnded;
    }

    public StreamingEvent nextEvent() throws ParserException {
        if (documentEnded) return null;

        if (!rootOpened) {
            rootOpened = true;
            return new JsonStreamingEvent(1, 1, JsonStreamingEventType.START_DOCUMENT, "");
        }

        advanceToken();

        if (currentToken == null || currentToken.getType() == JsonTokenType.EOF) {
            documentEnded = true;
            return new JsonStreamingEvent(tokenizer.getLine(), tokenizer.getColumn(),
                                          JsonStreamingEventType.END_DOCUMENT, "");
        }

        switch (currentToken.getType()) {
            case LEFT_BRACE:
                contextStack.push(JsonStreamingEventType.START_OBJECT);
                return event(JsonStreamingEventType.START_OBJECT);

            case RIGHT_BRACE:
                contextStack.pop();
                return event(JsonStreamingEventType.END_OBJECT);

            case LEFT_BRACKET:
                contextStack.push(JsonStreamingEventType.START_ARRAY);
                return event(JsonStreamingEventType.START_ARRAY);

            case RIGHT_BRACKET:
                contextStack.pop();
                return event(JsonStreamingEventType.END_ARRAY);

            case STRING:
                if (isNextTokenColon()) {
                    return event(JsonStreamingEventType.KEY, currentToken.getLexeme());
                }
                return event(JsonStreamingEventType.VALUE_SCALAR, currentToken.getLexeme());

            case NUMBER:
            case BOOLEAN:
            case NULL:
                return event(JsonStreamingEventType.VALUE_SCALAR, currentToken.getLexeme());

            case COLON:
            case COMMA:
                return nextEvent(); // skip structural separators

            default:
                throw new ParserException(currentToken, JsonDiagnosticCode.UNEXPECTED_TOKEN);
        }
    }

    private boolean isNextTokenColon() throws ParserException {
        if (bufferedToken == null) {
            bufferedToken = tokenizer.nextToken();
        }
        return bufferedToken.getType() == JsonTokenType.COLON;
    }

    private void advanceToken() throws ParserException {
        if (bufferedToken != null) {
            currentToken = bufferedToken;
            bufferedToken = null;
        } else {
            currentToken = tokenizer.nextToken();
        }
    }

    private JsonStreamingEvent event(JsonStreamingEventType type) {
        return new JsonStreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), type, "");
    }

    private JsonStreamingEvent event(JsonStreamingEventType type, String content) {
        return new JsonStreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), type, content);
    }
}
