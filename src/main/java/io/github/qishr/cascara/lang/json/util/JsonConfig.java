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

import io.github.qishr.cascara.common.diagnostic.exception.LocalizableRuntimeException;
import io.github.qishr.cascara.common.diagnostic.message.GenericMessage;
import io.github.qishr.cascara.common.annotation.Experimental;
import io.github.qishr.cascara.common.lang.util.LanguageConfig;
import io.github.qishr.cascara.common.util.Duplicable;

public class JsonConfig extends LanguageConfig<JsonConfig> implements Duplicable<JsonConfig>  {
    @Experimental
    public static final JsonConfig JSON5 = new ImmutableJsonOptions(
        new JsonConfig()
            .setIndentSize(2)
            .setCaptureComments(true)
            .setAllowComments(true)
            .setAllowHexadecimalNumbers(true)
            .setAllowJson5Numbers(true)
            .setAllowSingleQuotedStrings(true)
            .setAllowTrailingComma(true)
            .setAllowUnquotedKeys(true)
    );

    public static final JsonConfig STRICT = new ImmutableJsonOptions(
        new JsonConfig()
            .setIndentSize(2)
            .setCaptureComments(false)
            .setAllowComments(false)
            .setAllowTrailingComma(false)
    );

    private boolean allowBooleanKeys = false;
    private boolean allowComments = true;
    private boolean allowHexadecimalNumbers = false;
    private boolean allowJson5Numbers = false;
    private boolean allowSingleQuotedStrings = false;
    private boolean allowTrailingComma = false;
    private boolean allowUnicode = false;
    private boolean allowUnquotedKeys = false;
    private boolean captureComments = true;
    private boolean insertSpaces = true;
    private boolean prettyPrint = false; // TODO: remove
    private boolean useSimd = false;
    private boolean validateUnicode = false;
    private boolean trackPosition = false;

    private int depthLimit = 500;

    public JsonConfig() {}

    public JsonConfig(JsonConfig original) {
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

    protected int indentSize = 2;

    public JsonConfig setIndentSize(int size) {
        this.indentSize = size;
        return this;
    }

    public int getIndentSize() { return indentSize; }

    public int getDepthLimit() {
        return depthLimit;
    }

    public JsonConfig setDepthLimit(int limit) {
        depthLimit = limit;
        return this;
    }

    public JsonConfig setAllowBooleanKeys(boolean val) {
        this.allowBooleanKeys = val;
        return this;
    }

    public JsonConfig setAllowComments(boolean val) {
        this.allowComments = val;
        return this;
    }

    public JsonConfig setAllowHexadecimalNumbers(boolean val) {
        this.allowHexadecimalNumbers = val;
        return this;
    }
    public JsonConfig setAllowJson5Numbers(boolean val) {
        this.allowJson5Numbers = val;
        return this;
    }

    public JsonConfig setAllowSingleQuotedStrings(boolean val) {
        this.allowSingleQuotedStrings = val;
        return this;
    }

    public JsonConfig setAllowTrailingComma(boolean val) {
        this.allowTrailingComma = val;
        return this;
    }

    /// Sets whether unicode characters are allowed in scalars.
    public JsonConfig setAllowUnicode(boolean val) {
        this.allowUnicode = val;
        return this;
    }

    public JsonConfig setAllowUnquotedKeys(boolean val) {
        this.allowUnquotedKeys = val;
        return this;
    }

    public JsonConfig setCaptureComments(boolean val) {
        this.captureComments = val;
        return this;
    }

    /// Sets whether to use spaces or tabs for indentation.
    public JsonConfig setInsertSpaces(boolean val) {
        this.insertSpaces = val;
        return this;
    }

    public JsonConfig setPrettyPrint(boolean v) {
        this.prettyPrint = v;
        return this;
    }

    public JsonConfig setUseSimd(boolean v) {
        this.useSimd = v;
        return this;
    }

    public JsonConfig setValidateUnicode(boolean v) {
        this.validateUnicode = v;
        return this;
    }

    public JsonConfig setTrackPosition(boolean v) {
        this.trackPosition = v;
        return this;
    }

    @Override
    public JsonConfig duplicate() {
        return new JsonConfig(this);
    }

    public static class ImmutableJsonOptions extends JsonConfig {
        public ImmutableJsonOptions(JsonConfig options) {
            super(options);
        }

        public JsonConfig setAllowComments(boolean val) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setAllowComments");
        }

        public JsonConfig setAllowHexadecimalNumbers(boolean val) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setAllowHexadecimalNumbers");
        }
        public JsonConfig setAllowJson5Numbers(boolean val) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setAllowInfinityAndNaN");
        }

        public JsonConfig setAllowSingleQuotedStrings(boolean val) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setAllowSingleQuotedStrings");
        }

        public JsonConfig setAllowTrailingComma(boolean val) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setAllowTrailingComma");
        }

        public JsonConfig setAllowUnicode(boolean val) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setAllowComments");
        }

        public JsonConfig setAllowUnquotedKeys(boolean val) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setAllowUnquotedKeys");
        }

        public JsonConfig setCaptureComments(boolean val) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setCaptureComments");
        }

        public JsonConfig setStrict(boolean val) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setStrict");
        }

        public JsonConfig setInsertSpaces(boolean val) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setInsertSpaces");
        }

        public JsonConfig setPrettyPrint(boolean v) {
            throw new LocalizableRuntimeException(GenericMessage.UNSUPPORTED_OPERATION, "setPrettyPrint");
        }
    }
}