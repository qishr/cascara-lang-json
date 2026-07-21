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

import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.diagnostic.code.LangDiagnosticCode;
import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.common.lang.processor.AstConverter;
import io.github.qishr.cascara.lang.json.ast.JsonMapNode;
import io.github.qishr.cascara.lang.json.ast.JsonNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonSequenceNode;
import io.github.qishr.cascara.lang.json.exception.JsonConverterException;

public class JsonConverter extends AbstractJsonProcessor<JsonConverter> implements AstConverter<JsonNode> {
    @Override protected JsonConverter self() { return this; }

    @Override
    public ContentType getContentType() {
        return JsonAstParser.JSON_CONTENT_TYPE;
    }

    public String toText(AstNode ast) {
        JsonNode jsonNode = fromAst(ast);
        JsonEmitter emitter = new JsonEmitter();
        return emitter.emit(jsonNode);
    }

    @Nullable
    public JsonNode fromAst(AstNode from) {
        if (from == null) return null;
        if (from instanceof MapAstNode fromMap) {
            JsonMapNode map = new JsonMapNode();
            for (Object entry : fromMap.getEntries()) {
                if (entry instanceof MapEntryAstNode fromMapEntry) {
                    Object astKey = fromMapEntry.getKey();
                    AstNode astValue = fromMapEntry.getValue();
                    if (astKey instanceof ScalarAstNode astScalar) {
                        String jsonKey = astScalar.asString();
                        JsonNode jsonValue = fromAst(astValue);
                        map.put(jsonKey, jsonValue);
                    }
                }
            }
            return map;
        } else if (from instanceof SequenceAstNode astSeq) {
            JsonSequenceNode sequence = new JsonSequenceNode();
            for (Object element : astSeq.getElements()) {
                if (element instanceof AstNode astElement) {
                    sequence.add(fromAst(astElement));
                }
            }
            return sequence;
        } else if (from instanceof ScalarAstNode astScalar) {
            // System.out.println("Debug: SCALAR: " + astScalar.asString());
            return new JsonScalarNode(astScalar.getPrimitive(), false);
        } else {
            throw new JsonConverterException(
                LangDiagnosticCode.UNKNOWN_NODE_TYPE,
                from.getClass().getSimpleName()
            );
        }
    }
}
