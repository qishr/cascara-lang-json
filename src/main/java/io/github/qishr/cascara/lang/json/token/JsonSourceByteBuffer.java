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


package io.github.qishr.cascara.lang.json.token;

import java.nio.charset.StandardCharsets;

import io.github.qishr.cascara.common.lang.util.LexemeProvider;
import io.github.qishr.cascara.lang.json.util.JsonOptions;
import jdk.incubator.vector.*;

public final class JsonSourceByteBuffer implements JsonSimdCapableBuffer, LexemeProvider {
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;

    private static final ByteVector ZERO = ByteVector.broadcast(ByteVector.SPECIES_128, (byte)'0');
    private static final ByteVector NINE = ByteVector.broadcast(ByteVector.SPECIES_128, (byte)'9');

    public static final boolean VECTOR_AVAILABLE = isVectorApiAvailable();

    private static final int[] DIGIT_WEIGHTS = {
        10000000, 1000000, 100000, 10000,
        1000, 100, 10, 1
    };

    private static final IntVector WEIGHTS =
        IntVector.fromArray(IntVector.SPECIES_256, DIGIT_WEIGHTS, 0);

    private final boolean strictAsciiMode;
    private final boolean trackPosition;

    public final byte[] raw; // UTF‑8 bytes
    private int offset = 0;  // byte offset
    private int line = 1;
    private int column = 1;

    private int windowStartOffset;
    private int windowStartLine;
    private int windowStartColumn;

    public JsonSourceByteBuffer(byte[] raw, JsonOptions options) {
        this.raw = (raw != null) ? raw : new byte[0];
        this.strictAsciiMode = !options.allowUnicode() && !options.validateUnicode();
        this.trackPosition = options.trackPosition();
    }

    @Override
    public String slice(int startOffset, int endOffset) {
        byte[] part = java.util.Arrays.copyOfRange(raw, startOffset, endOffset);
        return new String(part, StandardCharsets.UTF_8);
    }

    //
    // UTF‑8 decoding
    //

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

    //
    // Core navigation
    //

    @Override
    public boolean isAtEnd() {
        return offset >= raw.length;
    }

    // TODO: Add to interface or just replace the String buffer with this byte[] based one
    public byte peekByte() {
        return raw[offset];
    }

    // TODO: Add to interface or just replace the String buffer with this byte[] based one
    public byte advanceByte() {
        return raw[offset++];
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
    public char peek() {
        if (isAtEnd()) return '\0';

        if (strictAsciiMode) {
            return (char)(raw[offset] & 0xFF); // raw byte → char
        }

        return decodeUtf8(offset);
    }

    //
    // Without position tracking
    //

    @Override
    public char advance() {
        if (trackPosition) return advanceWithTracking();

        if (offset >= raw.length) return '\0';

        char c;
        if (strictAsciiMode) {
            c = (char)(raw[offset] & 0xFF);
        } else {
            c = decodeUtf8(offset);
        }

        offset++;
        return c;
    }

    @Override
    public void backup() {
        if (trackPosition) {
            backupWithTracking();
            return;
        }
        offset--;
    }

    //
    // With position tracking
    //

    // @Override
    public char advanceWithTracking() {
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

    // @Override
    public void backupWithTracking() {
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

    //
    // Offset + length
    //

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

    //
    // Token window
    //

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

    @Override
    public int line() {
        return line;
    }

    @Override
    public int column() {
        return column;
    }

    //
    // SIMD whitespace
    //

    /// JSON Strict
    @Override
    public void skipWhitespaceSimd() {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_256;
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

    //
    // SIMD digits
    //

    @Override
    public int scanDigitsSimd(int pos) {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_256;
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

    @Override
    public int scanStringAsciiSimd(int pos, byte quoteByte) {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_256;
        final int vecLen = S.length();
        final byte ESC = (byte)'\\';

        final byte[] raw = this.raw;
        final int len = raw.length;

        // SIMD blocks
        while (pos + vecLen <= len) {
            ByteVector vec = ByteVector.fromArray(S, raw, pos);

            VectorMask<Byte> isQuote = vec.compare(VectorOperators.EQ, quoteByte);
            VectorMask<Byte> isBack  = vec.compare(VectorOperators.EQ, ESC);

            long mask = isQuote.or(isBack).toLong();

            if (mask != 0L) {
                int first = Long.numberOfTrailingZeros(mask);
                return pos + first;
            }

            pos += vecLen;
        }

        // Scalar tail
        while (pos < len) {
            byte b = raw[pos];
            if (b == quoteByte || b == ESC) {
                return pos;
            }
            pos++;
        }

        return pos;
    }

    public int scanStructuralSimd(int pos) {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_256;
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

        final VectorSpecies<Byte> S = ByteVector.SPECIES_256;
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

    //
    // SIMD ASCII / UTF‑8 lead-byte scanning
    //

    public int scanAsciiUntilUtf8LeadSimd(int pos) {
        final VectorSpecies<Byte> S = ByteVector.SPECIES_256;
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
        final VectorSpecies<Byte> S = ByteVector.SPECIES_256;
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

    //
    // Utility
    //

    public void advanceBy(int n) {
        for (int i = 0; i < n; i++) {
            advance();
        }
    }

    public int parseEightDigitsSIMD(byte[] raw, int offset) {
        // Load 8 bytes
        var bytes = ByteVector.fromArray(ByteVector.SPECIES_128, raw, offset);

        // Convert ASCII → numeric
        var digits = bytes.sub((byte)'0').reinterpretAsInts();

        // Multiply by weights
        var products = digits.mul(WEIGHTS);

        // Horizontal sum
        return products.reduceLanes(VectorOperators.ADD);
    }

    public static boolean isVectorApiAvailable() {
        try {
            Class.forName("jdk.incubator.vector.ByteVector");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isEightDigitsSIMD(byte[] raw, int offset, int limit) {
        // if (offset + 8 > limit) return false;
        int vecLen = SPECIES.length(); // 16

        // Ensure we can safely load a full vector
        if (offset + vecLen > limit) {
            return false;
        }

        // Load 8 bytes into a 128-bit vector
        var vec = ByteVector.fromArray(ByteVector.SPECIES_128, raw, offset);

        // vec >= '0'
        var geZero = vec.compare(VectorOperators.GE, ZERO);

        // vec <= '9'
        var leNine = vec.compare(VectorOperators.LE, NINE);

        // Combine masks
        var mask = geZero.and(leNine);

        // All lanes must be digits
        return mask.allTrue();
    }

    public boolean isEightDigits(byte[] raw, int offset, int limit) {
        if (offset + 8 > limit) return false;

        // scalar version first; you can replace with Vector API later
        for (int i = 0; i < 8; i++) {
            byte b = raw[offset + i];
            if (b < '0' || b > '9') {
                return false;
            }
        }
        return true;
    }

    public int parseEightDigits(byte[] raw, int offset) {
        int d0 = raw[offset    ] - '0';
        int d1 = raw[offset + 1] - '0';
        int d2 = raw[offset + 2] - '0';
        int d3 = raw[offset + 3] - '0';
        int d4 = raw[offset + 4] - '0';
        int d5 = raw[offset + 5] - '0';
        int d6 = raw[offset + 6] - '0';
        int d7 = raw[offset + 7] - '0';

        // 8-digit SWAR, no loops
        return
            d0 * 10000000 +
            d1 * 1000000  +
            d2 * 100000   +
            d3 * 10000    +
            d4 * 1000     +
            d5 * 100      +
            d6 * 10       +
            d7;
    }

    //
    //
    //

    public boolean matchKeywordByte(byte[] raw, int offset, String kw) {
        int n = kw.length();

        // Bounds check
        if (offset + n > raw.length) {
            return false;
        }

        // Compare raw bytes to keyword characters
        for (int i = 0; i < n; i++) {
            if (raw[offset + i] != (byte) kw.charAt(i)) {
                return false;
            }
        }

        return true;
    }

    public boolean matchKeywordByte(byte[] raw, int offset, String kw, int relativeOffset) {
        int off = offset + relativeOffset;
        int n = kw.length();

        if (off + n > raw.length) {
            return false;
        }

        for (int i = 0; i < n; i++) {
            if (raw[off + i] != (byte) kw.charAt(i)) {
                return false;
            }
        }

        return true;
    }

	@Override
	public char previous() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'previous'");
	}

}
