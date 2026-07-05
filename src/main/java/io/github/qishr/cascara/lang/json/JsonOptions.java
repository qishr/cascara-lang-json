package io.github.qishr.cascara.lang.json;

import io.github.qishr.cascara.common.lang.util.LanguageOptions;

public class JsonOptions extends LanguageOptions<JsonOptions> {
    public static final JsonOptions JSON5 = new JsonOptions()
            .setAllowComments(true)
            .setAllowTrailingComma(true)
            .setAllowUnquotedKeys(true);

    private boolean allowComments = false;
    private boolean allowTrailingComma = false;
    private boolean allowUnicode = true;
    private boolean allowUnquotedKeys = false;
    private boolean strict;
    protected boolean insertSpaces = true;
    private boolean prettyPrint; // TODO: I suspect the emitter always assumes prettyPrint is on

    public boolean allowComments() { return allowComments; }
    public boolean allowTrailingComma() { return allowTrailingComma; }
    public boolean allowUnicode() { return allowUnicode; }
    public boolean allowUnquotedKeys() { return allowUnquotedKeys; }
    public boolean insertSpaces() { return insertSpaces; }
    public boolean isStrict() { return strict; }
    public boolean prettyPrint() { return prettyPrint; }

    public JsonOptions setAllowComments(boolean val) {
        this.allowComments = val;
        return this;
    }

    public JsonOptions setAllowTrailingComma(boolean val) {
        this.allowTrailingComma = val;
        return this;
    }

    /// Sets whether unicode characters are allowed in scalars.
    public JsonOptions setAllowUnicode(boolean val) {
        this.allowUnicode = val;
        return this;
    }

    public JsonOptions setAllowUnquotedKeys(boolean val) {
        this.allowUnquotedKeys = val;
        return this;
    }

    public JsonOptions setStrict(boolean val) {
        this.strict = val;
        return this;
    }

    /// Sets whether to use spaces or tabs for indentation.
    public JsonOptions setInsertSpaces(boolean val) {
        this.insertSpaces = val;
        return this;
    }

    public JsonOptions setPrettyPrint(boolean v) {
        this.prettyPrint = v;
        return this;
    }
}