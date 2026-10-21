package io.github.qishr.cascara.lang.json.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

import io.github.qishr.cascara.common.data.TextualTable;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.common.util.TermUtils;
import io.github.qishr.cascara.lang.json.token.JsonErrorToken;
import io.github.qishr.cascara.lang.json.token.JsonToken;
import io.github.qishr.cascara.lang.json.token.JsonTokenType;

public class TestUtils {
        public static void dumpTokens(List<JsonToken> tokens) {
        dumpTokens(new PrintWriter(System.out), tokens, -1);
    }

    public static void dumpTokens(List<JsonToken> tokens, int maxColumnWidth) {
        dumpTokens(new PrintWriter(System.out), tokens, maxColumnWidth);
    }

    public static void dumpTokens(Writer writer, List<JsonToken> tokens) {
        dumpTokens(writer, tokens, -1);
    }

    public static void dumpTokens(Writer writer, List<JsonToken> tokens, int maxColumnWidth) {
        try {
			writer.write("\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // System.out.println("------------TOKENS-----------");
        TextualTable table = new TextualTable()
            .setStyle(TextualTable.Style.ROUNDED)
            .setMaxColumnWidth(maxColumnWidth)
            .setBorderColor(TermUtils.ANSI_WHITE)
            .addColumn("#")
            .addColumn("Token")
            .addColumn("Location")
            .addColumn("Lexeme")
            .addColumn("Content");

        int tokenIdent = 0;

        for (int i = 0; i < tokens.size(); i++) {
            JsonToken t = tokens.get(i);
            switch(t.getType()) {
                case ERROR:
                    JsonErrorToken et = (JsonErrorToken)t;
                    table.addRow(
                        String.format("%2d", i),
                        t.getType().toString(),
                        String.format("L:%-3d C:%-3d", t.getStartLine(), t.getStartColumn()),
                        "", et.getCode().getFormat()
                    );
                    break;
                case STRING, COMMENT:
                    table.addRow(
                        String.format("%2d", i),
                        "  ".repeat(tokenIdent) + t.getType().toString(),
                        String.format("L:%-3d C:%-3d", t.getStartLine(), t.getStartColumn()),
                        StringUtils.debugString(t.getLexeme()),
                        StringUtils.debugString(t.getContent())
                    );
                    break;
                default:
                    // if (t.getType() == YamlTokenType.DEDENT && tokenIdent > 0) {
                    //     tokenIdent--;
                    // }
                    table.addRow(
                        String.format("%2d", i),
                        "  ".repeat(tokenIdent) + t.getType().toString(),
                        String.format("L:%-3d C:%-3d", t.getStartLine(), t.getStartColumn()), "", ""
                    );
                    // if (t.getType() == JsonTokenType.INDENT) {
                    //     tokenIdent++;
                    // }
            }
        }

        try {
            table.render(writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
