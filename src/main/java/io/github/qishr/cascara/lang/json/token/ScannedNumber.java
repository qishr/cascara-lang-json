package io.github.qishr.cascara.lang.json.token;

public final class ScannedNumber {
    public final double value;
    public final boolean isInteger;
    public final boolean isHex;

    public ScannedNumber(double value, boolean isInteger, boolean isHex) {
        this.value = value;
        this.isInteger = isInteger;
        this.isHex = isHex;
    }
}
