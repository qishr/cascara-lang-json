package io.github.qishr.cascara.lang.json.token;

import java.nio.charset.StandardCharsets;

import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import jdk.incubator.vector.*;

public final class JsonSourceByteBuffer implements JsonSimdCapableBuffer, LexemeProvider {

    public final byte[] raw; // UTF‑8 bytes
    private int offset = 0;  // byte offset
    private int line = 1;
    private int column = 1;

    private int windowStartOffset;
    private int windowStartLine;
    private int windowStartColumn;

    public JsonSourceByteBuffer(byte[] raw) {
        this.raw = (raw != null) ? raw : new byte[0];
    }

    @Override
    public String slice(int startOffset, int endOffset) {
        byte[] part = java.util.Arrays.copyOfRange(raw, startOffset, endOffset);
        return new String(part, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------
    // UTF‑8 decoding
    // ------------------------------------------------------------

    private char decodeUtf8(int pos) {
        int b = raw[pos] & 0xFF;

        if (b < 0x80) {
            return (char) b;
        }

        // 2‑byte UTF‑8
        if ((b >> 5) == 0x6) {
            int b2 = raw[pos + 1] & 0x3F;
            return (char) (((b & 0x1F) << 6) | b2);
        }

        // 3‑byte UTF‑8
        if ((b >> 4) == 0xE) {
            int b2 = raw[pos + 1] & 0x3F;
            int b3 = raw[pos + 2] & 0x3F;
            return (char) (((b & 0x0F) << 12) | (b2 << 6) | b3);
        }

        // JSON does not allow 4‑byte UTF‑8 → replacement char
        return '\uFFFD';
    }

    // ------------------------------------------------------------
    // Core navigation
    // ------------------------------------------------------------

    @Override
    public boolean isAtEnd() {
        return offset >= raw.length;
    }

    @Override
    public char peek() {
        return isAtEnd() ? '\0' : decodeUtf8(offset);
    }

    @Override
    public char peekNext() {
        int pos = offset + 1;
        return (pos >= raw.length) ? '\0' : decodeUtf8(pos);
    }

    @Override
    public char peekAhead(int steps) {
        int pos = offset + steps;
        return (pos >= raw.length) ? '\0' : decodeUtf8(pos);
    }

    @Override
    public char advance() {
        if (isAtEnd()) return '\0';

        char c = decodeUtf8(offset);
        offset++;

        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }

        return c;
    }

    @Override
    public void backup() {
        if (offset == windowStartOffset) {
            throw new IllegalStateException("Cannot backup past token window start");
        }

        offset--;

        char c = decodeUtf8(offset);

        if (c == '\n') {
            line--;
            column = 1;
        } else {
            column--;
        }
    }

    // ------------------------------------------------------------
    // Offset + length
    // ------------------------------------------------------------

    @Override
    public int offset() {
        return offset;
    }

    @Override
    public void setOffset(int newOffset) {
        // O(1) — do NOT recompute line/column
        this.offset = newOffset;
    }

    @Override
    public int length() {
        return raw.length;
    }

    @Override
    public char charAt(int index) {
        return decodeUtf8(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new String(raw, start, end - start, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------
    // Token window
    // ------------------------------------------------------------

    @Override
    public void startTokenWindow() {
        windowStartOffset = offset;
        windowStartLine   = line;
        windowStartColumn = column;
    }

    @Override
    public String getTokenWindowLexeme() {
        return new String(raw, windowStartOffset, offset - windowStartOffset, StandardCharsets.UTF_8);
    }

    @Override
    public int windowStartOffset() {
        return windowStartOffset;
    }

    @Override
    public int windowStartLine() {
        return windowStartLine;
    }

    @Override
    public int windowStartColumn() {
        return windowStartColumn;
    }

    // ------------------------------------------------------------
    // SIMD whitespace
    // ------------------------------------------------------------

    /// JSON Strict
    @Override
    public void skipWhitespaceSimd() {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_128;
        int len = raw.length;

        while (offset < len) {
            int remaining = len - offset;

            if (remaining < S.length()) {
                // scalar tail
                while (offset < len) {
                    byte b = raw[offset];

                    // TODO: FSM table
                    // There are likely other places we need to check

                    if (b != ' ' && b != '\t' && b != '\n' && b != '\r') {
                        return;
                    }
                    advance();
                }
                return;
            }

            ByteVector vec = ByteVector.fromArray(S, raw, offset);

            VectorMask<Byte> isSpace = vec.compare(VectorOperators.EQ, (byte)' ');
            VectorMask<Byte> isTab   = vec.compare(VectorOperators.EQ, (byte)'\t');
            VectorMask<Byte> isNL    = vec.compare(VectorOperators.EQ, (byte)'\n');
            VectorMask<Byte> isCR    = vec.compare(VectorOperators.EQ, (byte)'\r');

            long wsMask = isSpace.toLong() | isTab.toLong() | isNL.toLong() | isCR.toLong();

            // If the very first byte is not whitespace, stop
            if ((wsMask & 1L) == 0L) {
                return;
            }

            // Compute run of whitespace from the start:
            // invert mask: bits 1 where NON‑whitespace
            long nonWsMask = ~wsMask;

            // Find first non‑whitespace bit
            int firstNon = Long.numberOfTrailingZeros(nonWsMask);

            if (firstNon >= S.length()) {
                // all bytes in this block are whitespace
                advanceBy(S.length());
            } else {
                // skip up to the first non‑whitespace
                advanceBy(firstNon);
                return;
            }
        }
    }

    // ------------------------------------------------------------
    // SIMD digits
    // ------------------------------------------------------------

    @Override
    public int scanDigitsSimd(int pos) {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_128;
        int len = raw.length;

        while (pos < len) {
            int remaining = len - pos;

            if (remaining < S.length()) {
                while (pos < len) {
                    byte b = raw[pos];
                    if (b < '0' || b > '9') {
                        return pos;
                    }
                    pos++;
                }
                return pos;
            }

            ByteVector vec = ByteVector.fromArray(S, raw, pos);

            VectorMask<Byte> lt0 = vec.compare(VectorOperators.LT, (byte)'0');
            VectorMask<Byte> gt9 = vec.compare(VectorOperators.GT, (byte)'9');

            long mask = lt0.toLong() | gt9.toLong();

            if (mask != 0L) {
                int first = Long.numberOfTrailingZeros(mask);
                return pos + first;
            }

            pos += S.length();
        }

        return pos;
    }

    // ------------------------------------------------------------
    // SIMD ASCII string scanning
    // ------------------------------------------------------------

    @Override
    public int scanStringAsciiSimd(int pos, byte quoteByte) {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_128;
        final int vecLen = S.length();

        final byte QUOTE = quoteByte;
        final byte ESC   = (byte)'\\';

        int len = raw.length;

        while (pos < len) {
            int remaining = len - pos;

            if (remaining < vecLen) {
                while (pos < len) {
                    byte b = raw[pos];
                    if (b == QUOTE || b == ESC || b < 0x20) {
                        return pos;
                    }
                    pos++;
                }
                return pos;
            }

            ByteVector vec = ByteVector.fromArray(S, raw, pos);

            VectorMask<Byte> isQuote = vec.compare(VectorOperators.EQ, QUOTE);
            VectorMask<Byte> isEsc   = vec.compare(VectorOperators.EQ, ESC);
            VectorMask<Byte> isCtrl =
                vec.compare(VectorOperators.GE, (byte)0x00)
                    .and(vec.compare(VectorOperators.LE, (byte)0x1F));

            long mask = isQuote.toLong() | isEsc.toLong() | isCtrl.toLong();




            if (mask != 0L) {
                int first = Long.numberOfTrailingZeros(mask);
                return pos + first;
            }

            // TODO: This is supposed to work, but it breaks a test.

            // // Only lane 0 matters for advancing the string scanner
            // if ((mask & 1L) != 0L) {
            //     return pos;   // lane 0 is quote, backslash, or control
            // }




            // Otherwise skip whole block
            pos += vecLen;
        }

        return pos;
    }

    // ------------------------------------------------------------
    // Utility: advanceBy
    // ------------------------------------------------------------

    public void advanceBy(int n) {
        for (int i = 0; i < n; i++) {
            advance();
        }
    }

    @Override
    public int line() {
        return line;
    }

    @Override
    public int column() {
        return column;
    }

    public int scanStructuralSimd(int pos) {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_128;
        final int len = raw.length;

        final byte LBRACE = (byte)'{';
        final byte RBRACE = (byte)'}';
        final byte LBRACK = (byte)'[';
        final byte RBRACK = (byte)']';
        final byte COMMA  = (byte)',';
        final byte COLON  = (byte)':';

        while (pos < len) {
            int remaining = len - pos;

            if (remaining < S.length()) {
                while (pos < len) {
                    byte b = raw[pos];
                    if (b == LBRACE || b == RBRACE ||
                        b == LBRACK || b == RBRACK ||
                        b == COMMA  || b == COLON) {
                        return pos;
                    }
                    pos++;
                }
                return pos;
            }

            ByteVector vec = ByteVector.fromArray(S, raw, pos);

            VectorMask<Byte> mLBrace = vec.compare(VectorOperators.EQ, LBRACE);
            VectorMask<Byte> mRBrace = vec.compare(VectorOperators.EQ, RBRACE);
            VectorMask<Byte> mLBrack = vec.compare(VectorOperators.EQ, LBRACK);
            VectorMask<Byte> mRBrack = vec.compare(VectorOperators.EQ, RBRACK);
            VectorMask<Byte> mComma  = vec.compare(VectorOperators.EQ, COMMA);
            VectorMask<Byte> mColon  = vec.compare(VectorOperators.EQ, COLON);

            long mask = mLBrace.toLong() | mRBrace.toLong()
                      | mLBrack.toLong() | mRBrack.toLong()
                      | mComma.toLong()  | mColon.toLong();

            if (mask != 0L) {
                int first = Long.numberOfTrailingZeros(mask);
                return pos + first;
            }

            pos += S.length();
        }

        return pos;
    }

    /// JSON5
    public void skipWhitespaceAndFormattingSimd() {
        final byte SPACE = (byte)' ';
        final byte TAB   = (byte)'\t';
        final byte CR    = (byte)'\r';
        final byte LF    = (byte)'\n';

        final VectorSpecies<Byte> S = ByteVector.SPECIES_128;
        int pos = offset();
        int len = raw.length;

        while (pos < len) {
            int remaining = len - pos;

            if (remaining < S.length()) {
                // scalar tail
                while (pos < len) {
                    byte b = raw[pos];
                    if (b != SPACE && b != TAB && b != CR && b != LF) {
                        this.advanceBy(pos - offset());
                        return;
                    }
                    pos++;
                }
                this.advanceBy(pos - offset());
                return;
            }

            ByteVector vec = ByteVector.fromArray(S, raw, pos);

            // mask for whitespace
            VectorMask<Byte> mSpace = vec.compare(VectorOperators.EQ, SPACE);
            VectorMask<Byte> mTab   = vec.compare(VectorOperators.EQ, TAB);
            VectorMask<Byte> mCR    = vec.compare(VectorOperators.EQ, CR);
            VectorMask<Byte> mLF    = vec.compare(VectorOperators.EQ, LF);

            long mask = mSpace.toLong() | mTab.toLong() | mCR.toLong() | mLF.toLong();

            if (mask != ~0L) {
                // found first non-whitespace byte
                int first = Long.numberOfTrailingZeros(~mask);
                int skip = first;
                this.advanceBy(skip);
                return;
            }

            pos += S.length();
        }

        this.advanceBy(pos - offset());
    }

    // ------------------------------------------------------------
    // SIMD ASCII / UTF‑8 lead-byte scanning
    // ------------------------------------------------------------

    public int scanAsciiUntilUtf8LeadSimd(int pos) {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_128;
        final int len = raw.length;

        // ASCII:        0x00–0x7F
        // UTF-8 lead:   0xC0–0xF7 (we treat any >= 0x80 as "non-ASCII" here)
        final byte ASCII_MAX = (byte)0x7F;

        while (pos < len) {
            int remaining = len - pos;

            if (remaining < S.length()) {
                while (pos < len) {
                    byte b = raw[pos];
                    if ((b & 0x80) != 0) { // non-ASCII → UTF-8 lead or continuation
                        return pos;
                    }
                    pos++;
                }
                return pos;
            }

            ByteVector vec = ByteVector.fromArray(S, raw, pos);

            // mask for non-ASCII (b >= 0x80)
            VectorMask<Byte> mNonAscii = vec.compare(VectorOperators.GT, ASCII_MAX);

            long mask = mNonAscii.toLong();

            if (mask != 0L) {
                int first = Long.numberOfTrailingZeros(mask);
                return pos + first;
            }

            pos += S.length();
        }

        return pos;
    }

    public int scanIdentifierStartSimd(int pos) {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_128;
        final int len = raw.length;

        // IDENT_START: [A-Za-z_$]
        final byte A = (byte)'A';
        final byte Z = (byte)'Z';
        final byte a = (byte)'a';
        final byte z = (byte)'z';
        final byte us = (byte)'_';
        final byte dl = (byte)'$';

        while (pos < len) {
            int remaining = len - pos;

            if (remaining < S.length()) {
                // scalar tail
                while (pos < len) {
                    byte b = raw[pos];
                    if (b < 0x80 &&
                        ((b >= A && b <= Z) ||
                         (b >= a && b <= z) ||
                         b == us || b == dl)) {
                        return pos;
                    }
                    pos++;
                }
                return -1;
            }

            ByteVector vec = ByteVector.fromArray(S, raw, pos);

            VectorMask<Byte> mAscii = vec.compare(VectorOperators.LT, (byte)0x80);

            VectorMask<Byte> mAZ =
                vec.compare(VectorOperators.GE, A)
                   .and(vec.compare(VectorOperators.LE, Z));

            VectorMask<Byte> maz =
                vec.compare(VectorOperators.GE, a)
                   .and(vec.compare(VectorOperators.LE, z));

            VectorMask<Byte> mus = vec.compare(VectorOperators.EQ, us);
            VectorMask<Byte> mdl = vec.compare(VectorOperators.EQ, dl);

            long mask =
                mAscii.toLong() &
                (mAZ.or(maz).or(mus).or(mdl)).toLong();

            if (mask != 0L) {
                int first = Long.numberOfTrailingZeros(mask);
                return pos + first;
            }

            pos += S.length();
        }

        return -1;
    }

}
