package io.github.qishr.cascara.lang.json.token;

public class JsonComment {
    final String lexeme;
    final String value;
    final int line;
    final int column;

    public JsonComment(String lexeme, String value, int line, int column) {
        this.lexeme = lexeme;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    public String getLexeme() { return lexeme; }
    public String getValue()  { return value; }
    public int getLine()      { return line; }
    public int getColumn()    { return column; }
}