package com.carddemo.codec;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Arithmetic-result semantics of storing into a COBOL {@code PIC S9(n)V9(m)} field. */
public final class CobolNumeric {

  private CobolNumeric() {}

  /**
   * Stores {@code value} into a field with {@code intDigits} integer digits and {@code scale}
   * fractional digits the way an IBM COBOL {@code COMPUTE}/{@code ADD} without {@code ON SIZE
   * ERROR} does: fractional digits beyond {@code scale} are truncated and high-order digits that do
   * not fit are silently dropped (the result keeps its sign).
   */
  public static BigDecimal store(BigDecimal value, int intDigits, int scale) {
    BigDecimal truncated = value.setScale(scale, RoundingMode.DOWN);
    BigDecimal modulus = BigDecimal.TEN.pow(intDigits);
    BigDecimal abs = truncated.abs();
    if (abs.compareTo(modulus) < 0) {
      return truncated;
    }
    BigDecimal wrapped = abs.remainder(modulus);
    return truncated.signum() < 0 ? wrapped.negate() : wrapped;
  }
}
