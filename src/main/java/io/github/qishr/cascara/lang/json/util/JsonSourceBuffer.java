package io.github.qishr.cascara.lang.json.util;

import io.github.qishr.cascara.common.lang.util.SourceBuffer;
import io.github.qishr.cascara.common.lang.util.SourceBufferOptions;

public interface JsonSourceBuffer extends SourceBuffer {
    JsonSourceBuffer setOptions(SourceBufferOptions options) ;

    void skipWhitespaceSimd();
    byte peekByte();
    byte advanceByte();
    char advanceWithTracking();
    void backupWithTracking();
    int scanStructuralSimd(int pos);
    int scanAsciiUntilUtf8LeadSimd(int pos);
    int scanIdentifierStartSimd(int pos);
    void advanceBy(int n);
    int parseEightDigitsSIMD(byte[] raw, int offset);
    boolean isEightDigitsSIMD(byte[] raw, int offset, int limit);
    boolean isEightDigits(byte[] raw, int offset, int limit);
    int parseEightDigits(byte[] raw, int offset) ;
    boolean matchKeywordByte(byte[] raw, int offset, String kw);
    boolean matchKeywordByte(byte[] raw, int offset, String kw, int relativeOffset) ;
    int scanDigitsSimd(int pos) ;

    int scanStringAsciiSimd(int pos, byte quoteByte);

    /// Skips whitespace and comments. Suitable for JSON5.
    void skipWhitespaceAndFormattingSimd();

    void scanIdentifierSimd();

    byte[] getBytes();
}
