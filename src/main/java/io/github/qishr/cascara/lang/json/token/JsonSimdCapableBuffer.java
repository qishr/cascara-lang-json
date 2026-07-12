package io.github.qishr.cascara.lang.json.token;

import io.github.qishr.cascara.common.lang.util.SimdCapableBuffer;

public interface JsonSimdCapableBuffer extends SimdCapableBuffer {

    int scanStringAsciiSimd(int pos, byte quoteByte);

    /// Skips whitespace and comments. Suitable for JSON5.
    void skipWhitespaceAndFormattingSimd();
}
