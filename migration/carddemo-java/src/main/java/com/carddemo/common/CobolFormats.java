package com.carddemo.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Parsing/formatting helpers for the fixed-width, zoned-decimal legacy record layouts. */
public final class CobolFormats {

  /**
   * TRAN-ORIG-TS / TRAN-PROC-TS: PIC X(26), 'YYYY-MM-DD HH:MM:SS.ffffff' (COBIL00C.cbl 263-266).
   */
  public static final DateTimeFormatter TIMESTAMP_26 =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

  private CobolFormats() {}

  /**
   * Parses a zoned-decimal field (PIC S9(n)V(scale)) as it appears in the ASCII sample data, where
   * the last character carries an over-punched sign: '{' = +0, 'A'..'I' = +1..+9, '}' = -0,
   * 'J'..'R' = -1..-9, or a plain digit (unsigned).
   */
  public static BigDecimal zonedDecimal(String field, int scale) {
    String digits = field.substring(0, field.length() - 1);
    char last = field.charAt(field.length() - 1);
    boolean negative = false;
    int lastDigit;
    if (last == '{') {
      lastDigit = 0;
    } else if (last == '}') {
      lastDigit = 0;
      negative = true;
    } else if (last >= 'A' && last <= 'I') {
      lastDigit = last - 'A' + 1;
    } else if (last >= 'J' && last <= 'R') {
      lastDigit = last - 'J' + 1;
      negative = true;
    } else if (Character.isDigit(last)) {
      lastDigit = last - '0';
    } else {
      throw new IllegalArgumentException("Invalid zoned decimal sign character: " + last);
    }
    BigDecimal value = new BigDecimal(digits + lastDigit).movePointLeft(scale);
    return negative ? value.negate() : value;
  }

  /** PIC X(10) 'YYYY-MM-DD'; blank -> null. */
  public static LocalDate date10(String field) {
    String s = field.strip();
    return s.isEmpty() ? null : LocalDate.parse(s);
  }

  /** PIC X(26) timestamp; blank -> null. */
  public static LocalDateTime timestamp26(String field) {
    String s = field.strip();
    return s.isEmpty() ? null : LocalDateTime.parse(s, TIMESTAMP_26);
  }

  public static String trimmed(String field) {
    String s = field.strip();
    return s.isEmpty() ? null : s;
  }
}
