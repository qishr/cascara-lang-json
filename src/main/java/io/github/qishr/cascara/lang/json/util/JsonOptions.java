package io.github.qishr.cascara.lang.json.util;

import io.github.qishr.cascara.common.diagnostic.LocalizableRuntimeException;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.annotation.Experimental;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.util.Duplicable;

public class JsonOptions extends LanguageOptions<JsonOptions> implements Duplicable<JsonOptions>  {
    @Experimental
    public static final JsonOptions JSON5 = new ImmutableJsonOptions(
        new JsonOptions()
            .setAllowComments(true)
            .setAllowHexadecimalNumbers(true)
            .setAllowJson5Numbers(true)
            .setAllowSingleQuotedStrings(true)
            .setAllowTrailingComma(true)
            .setAllowUnquotedKeys(true)
    );

    public static final JsonOptions STRICT = new ImmutableJsonOptions(new JsonOptions());

    private boolean allowBooleanKeys = false;
    private boolean allowComments = false;
    private boolean allowHexadecimalNumbers = false;
    private boolean allowJson5Numbers = false;
    private boolean allowSingleQuotedStrings = false;
    private boolean allowTrailingComma = false;
    private boolean allowUnicode = false;
    private boolean allowUnquotedKeys = false;
    private boolean captureComments = false;
    private boolean insertSpaces = true;
    private boolean prettyPrint = false;
    private boolean useSimd = false;

    public JsonOptions() {}

    public JsonOptions(JsonOptions original) {
        allowBooleanKeys = original.allowBooleanKeys;
        allowComments = original.allowComments;
        allowHexadecimalNumbers = original.allowHexadecimalNumbers;
        allowJson5Numbers = original.allowJson5Numbers;
        allowSingleQuotedStrings = original.allowSingleQuotedStrings;
        allowTrailingComma = original.allowTrailingComma;
        allowUnicode = original.allowUnicode;
        allowUnquotedKeys = original.allowUnquotedKeys;
        captureComments = original.captureComments;
        insertSpaces = original.insertSpaces;
        prettyPrint = original.prettyPrint;
        useSimd = original.useSimd;
    }

    // public boolean allowBooleanKeys() { return allowBooleanKeys; }
    public boolean allowComments() { return allowComments; }
    public boolean allowHexadecimalNumbers() { return allowHexadecimalNumbers; }
    public boolean allowJson5Numbers() { return allowJson5Numbers; }
    public boolean allowSingleQuotedStrings() { return allowSingleQuotedStrings; }
    public boolean allowTrailingComma() { return allowTrailingComma; }
    public boolean allowUnicode() { return allowUnicode; }
    public boolean allowUnquotedKeys() { return allowUnquotedKeys; }
    public boolean captureComments() { return captureComments; }
    public boolean insertSpaces() { return insertSpaces; }
    public boolean prettyPrint() { return prettyPrint; }
    public boolean useSimd() { return useSimd; }

    public JsonOptions setAllowBooleanKeys(boolean val) {
        this.allowBooleanKeys = val;
        return this;
    }

    public JsonOptions setAllowComments(boolean val) {
        this.allowComments = val;
        return this;
    }

    public JsonOptions setAllowHexadecimalNumbers(boolean val) {
        this.allowHexadecimalNumbers = val;
        return this;
    }
    public JsonOptions setAllowJson5Numbers(boolean val) {
        this.allowJson5Numbers = val;
        return this;
    }

    public JsonOptions setAllowSingleQuotedStrings(boolean val) {
        this.allowSingleQuotedStrings = val;
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

    public JsonOptions setCaptureComments(boolean val) {
        this.captureComments = val;
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

    public JsonOptions setUseSimd(boolean v) {
        this.useSimd = v;
        return this;
    }

    @Override
    public JsonOptions duplicate() {
        return new JsonOptions(this);
    }

    public static class ImmutableJsonOptions extends JsonOptions {
        public ImmutableJsonOptions(JsonOptions options) {
            super(options);
        }

        public JsonOptions setAllowComments(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setAllowComments");
        }

        public JsonOptions setAllowHexadecimalNumbers(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setAllowHexadecimalNumbers");
        }
        public JsonOptions setAllowJson5Numbers(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setAllowInfinityAndNaN");
        }

        public JsonOptions setAllowSingleQuotedStrings(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setAllowSingleQuotedStrings");
        }

        public JsonOptions setAllowTrailingComma(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setAllowTrailingComma");
        }

        public JsonOptions setAllowUnicode(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setAllowComments");
        }

        public JsonOptions setAllowUnquotedKeys(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setAllowUnquotedKeys");
        }

        public JsonOptions setCaptureComments(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setCaptureComments");
        }

        public JsonOptions setStrict(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setStrict");
        }

        public JsonOptions setInsertSpaces(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setInsertSpaces");
        }

        public JsonOptions setPrettyPrint(boolean v) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setPrettyPrint");
        }
    }
}