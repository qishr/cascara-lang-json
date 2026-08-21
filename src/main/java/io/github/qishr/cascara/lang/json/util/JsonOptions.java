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


package io.github.qishr.cascara.lang.json.util;

import io.github.qishr.cascara.common.diagnostic.LocalizableRuntimeException;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.annotation.Experimental;
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
    private boolean validateUnicode = false;
    private boolean trackPosition = false;

    private int depthLimit = 500;

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
        validateUnicode = original.validateUnicode;
        trackPosition = original.trackPosition;

        depthLimit = original.depthLimit;
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
    public boolean validateUnicode() { return validateUnicode; }
    public boolean trackPosition() { return trackPosition; }

    public int getDepthLimit() {
        return depthLimit;
    }

    public JsonOptions setDepthLimit(int limit) {
        depthLimit = limit;
        return this;
    }

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

    public JsonOptions setValidateUnicode(boolean v) {
        this.validateUnicode = v;
        return this;
    }

    public JsonOptions setTrackPosition(boolean v) {
        this.trackPosition = v;
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