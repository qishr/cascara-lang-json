package io.github.qishr.cascara.lang.json;

import io.github.qishr.cascara.common.lang.LanguageOptions;

public class JsonOptions extends LanguageOptions<JsonOptions> {
    private boolean allowUnicode = true;
    private boolean strict = false;
    protected boolean insertSpaces = true;

    /// Sets whether unicode characters are allowed in scalars.
    public JsonOptions setAllowUnicode(boolean val) {
        this.allowUnicode = val;
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

    public boolean isAllowUnicode() { return allowUnicode; }
    public boolean isStrict() { return strict; }
    public boolean isInsertSpaces() { return insertSpaces; }
}