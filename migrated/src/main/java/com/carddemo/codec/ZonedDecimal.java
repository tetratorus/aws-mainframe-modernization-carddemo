package com.carddemo.codec;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Codec for COBOL {@code PIC S9(n)V9(m)} zoned-decimal fields as they appear in the CardDemo ASCII
 * data files: digits with the sign overpunched into the last character using the EBCDIC convention
 * translated to ASCII ({@code '{'},{@code 'A'..'I'} = +0..+9; {@code '}'},{@code 'J'..'R'} =
 * -0..-9). Plain trailing digits are accepted on decode and treated as positive so that unsigned
 * {@code PIC 9} fields can share the same path.
 */
public final class ZonedDecimal {

  private static final String POSITIVE = "{ABCDEFGHI";
  private static final String NEGATIVE = "}JKLMNOPQR";

  private ZonedDecimal() {}

  /**
   * Decodes a zoned-decimal field.
   *
   * @param text the raw field bytes, length = number of digits
   * @param scale number of implied decimal places ({@code V99} = 2)
   */
  public static BigDecimal decode(String text, int scale) {
    if (text.isEmpty()) {
      throw new IllegalArgumentException("empty zoned decimal field");
    }
    char last = text.charAt(text.length() - 1);
    boolean negative = false;
    int lastDigit;
    int idx;
    if ((idx = POSITIVE.indexOf(last)) >= 0) {
      lastDigit = idx;
    } else if ((idx = NEGATIVE.indexOf(last)) >= 0) {
      lastDigit = idx;
      negative = true;
    } else if (Character.isDigit(last)) {
      lastDigit = last - '0';
    } else {
      throw new IllegalArgumentException("invalid zoned decimal sign nibble: '" + last + "'");
    }
    String lead = text.substring(0, text.length() - 1);
    for (int i = 0; i < lead.length(); i++) {
      if (!Character.isDigit(lead.charAt(i))) {
        throw new IllegalArgumentException("non-numeric zoned decimal field: '" + text + "'");
      }
    }
    BigInteger unscaled = new BigInteger(lead + lastDigit);
    if (negative) {
      unscaled = unscaled.negate();
    }
    return new BigDecimal(unscaled, scale);
  }

  /**
   * Encodes a value as a signed zoned-decimal field with EBCDIC-style overpunch.
   *
   * @param value value to encode; extra fractional digits are truncated (COBOL MOVE semantics)
   * @param digits total number of digits (integer + fraction)
   * @param scale number of fractional digits
   */
  public static String encodeSigned(BigDecimal value, int digits, int scale) {
    BigInteger unscaled = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
    boolean negative = unscaled.signum() < 0;
    String body = padOrTruncate(unscaled.abs().toString(), digits);
    int lastDigit = body.charAt(digits - 1) - '0';
    char sign = (negative ? NEGATIVE : POSITIVE).charAt(lastDigit);
    return body.substring(0, digits - 1) + sign;
  }

  /** Encodes a non-negative integer as an unsigned {@code PIC 9(n)} field. */
  public static String encodeUnsigned(long value, int digits) {
    if (value < 0) {
      throw new IllegalArgumentException("unsigned field cannot hold " + value);
    }
    return padOrTruncate(Long.toString(value), digits);
  }

  /** Decodes an unsigned {@code PIC 9(n)} field. */
  public static long decodeUnsigned(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isDigit(text.charAt(i))) {
        throw new IllegalArgumentException("non-numeric unsigned field: '" + text + "'");
      }
    }
    return Long.parseLong(text);
  }

  /** COBOL MOVE to a numeric field: left-truncate high-order digits, zero-fill on the left. */
  private static String padOrTruncate(String digitsText, int digits) {
    if (digitsText.length() > digits) {
      return digitsText.substring(digitsText.length() - digits);
    }
    return "0".repeat(digits - digitsText.length()) + digitsText;
  }
}
