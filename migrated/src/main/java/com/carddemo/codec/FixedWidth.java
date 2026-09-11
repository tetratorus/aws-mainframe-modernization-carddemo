package com.carddemo.codec;

import java.math.BigDecimal;

/** Helpers for reading and writing fixed-width COBOL record images. */
public final class FixedWidth {

  private FixedWidth() {}

  /** Sequential reader over a fixed-width record image. */
  public static final class Reader {
    private final String image;
    private int pos;

    public Reader(String image, int expectedLength) {
      this.image = pad(image, expectedLength);
    }

    /** {@code PIC X(n)}. */
    public String alnum(int width) {
      String s = image.substring(pos, pos + width);
      pos += width;
      return s;
    }

    /** {@code PIC 9(n)}. */
    public long unsigned(int width) {
      return ZonedDecimal.decodeUnsigned(alnum(width));
    }

    /** {@code PIC S9(n)V9(m)} where {@code width = n + m}. */
    public BigDecimal signed(int width, int scale) {
      return ZonedDecimal.decode(alnum(width), scale);
    }

    public int position() {
      return pos;
    }
  }

  /** Sequential writer producing a fixed-width record image. */
  public static final class Writer {
    private final StringBuilder out = new StringBuilder();

    /** {@code PIC X(n)}: space-padded on the right, truncated on the right. */
    public Writer alnum(String value, int width) {
      out.append(pad(value == null ? "" : value, width));
      return this;
    }

    /** {@code PIC 9(n)}. */
    public Writer unsigned(long value, int width) {
      out.append(ZonedDecimal.encodeUnsigned(value, width));
      return this;
    }

    /** {@code PIC S9(n)V9(m)}. */
    public Writer signed(BigDecimal value, int width, int scale) {
      out.append(ZonedDecimal.encodeSigned(value, width, scale));
      return this;
    }

    public String build(int expectedLength) {
      if (out.length() != expectedLength) {
        throw new IllegalStateException(
            "record image is " + out.length() + " bytes, expected " + expectedLength);
      }
      return out.toString();
    }
  }

  /** Space-pads (or truncates) {@code s} to exactly {@code width} characters. */
  public static String pad(String s, int width) {
    if (s.length() == width) {
      return s;
    }
    if (s.length() > width) {
      return s.substring(0, width);
    }
    return s + " ".repeat(width - s.length());
  }
}
