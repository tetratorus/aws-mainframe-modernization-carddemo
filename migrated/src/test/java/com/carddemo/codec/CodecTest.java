package com.carddemo.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CodecTest {

  @Test
  void decodesEbcdicStyleOverpunch() {
    assertEquals(new BigDecimal("504.77"), ZonedDecimal.decode("0000005047G", 2));
    assertEquals(new BigDecimal("-919.00"), ZonedDecimal.decode("0000009190}", 2));
    assertEquals(new BigDecimal("0.00"), ZonedDecimal.decode("0000000000{", 2));
    assertEquals(new BigDecimal("0.00"), ZonedDecimal.decode("0000000000}", 2));
    assertEquals(new BigDecimal("12.34"), ZonedDecimal.decode("1234", 2));
  }

  @Test
  void encodesOverpunchWithZeroPadding() {
    assertEquals("0000005047G", ZonedDecimal.encodeSigned(new BigDecimal("504.77"), 11, 2));
    assertEquals("0000009190}", ZonedDecimal.encodeSigned(new BigDecimal("-919.00"), 11, 2));
    assertEquals("00000000000{", ZonedDecimal.encodeSigned(BigDecimal.ZERO, 12, 2));
    assertEquals("0000000000R", ZonedDecimal.encodeSigned(new BigDecimal("-0.09"), 11, 2));
  }

  @Test
  void roundTripsEverySignNibble() {
    for (int d = 0; d < 10; d++) {
      BigDecimal pos = new BigDecimal("1." + d + "0").setScale(2);
      BigDecimal neg = pos.negate();
      assertEquals(pos, ZonedDecimal.decode(ZonedDecimal.encodeSigned(pos, 5, 2), 2));
      assertEquals(neg, ZonedDecimal.decode(ZonedDecimal.encodeSigned(neg, 5, 2), 2));
    }
  }

  @Test
  void rejectsGarbage() {
    assertThrows(IllegalArgumentException.class, () -> ZonedDecimal.decode("00x0A", 2));
    assertThrows(IllegalArgumentException.class, () -> ZonedDecimal.decode("0000Z", 2));
    assertThrows(IllegalArgumentException.class, () -> ZonedDecimal.decodeUnsigned("12 4"));
    assertThrows(IllegalArgumentException.class, () -> ZonedDecimal.encodeUnsigned(-1, 4));
  }

  @Test
  void unsignedFieldsAreLeftZeroPadded() {
    assertEquals("00042", ZonedDecimal.encodeUnsigned(42, 5));
    assertEquals(42, ZonedDecimal.decodeUnsigned("00042"));
  }

  @Test
  void cobolStoreTruncatesFractionAndWrapsHighOrderDigits() {
    // PIC S9(09)V99
    assertEquals(new BigDecimal("1.23"), CobolNumeric.store(new BigDecimal("1.239"), 9, 2));
    assertEquals(
        new BigDecimal("999999999.99"), CobolNumeric.store(new BigDecimal("999999999.99"), 9, 2));
    assertEquals(
        new BigDecimal("0.99"), CobolNumeric.store(new BigDecimal("10000000000.99"), 9, 2));
    assertEquals(
        new BigDecimal("-0.50"), CobolNumeric.store(new BigDecimal("-1000000000.50"), 9, 2));
    // PIC S9(10)V99
    assertEquals(
        new BigDecimal("0.99"), CobolNumeric.store(new BigDecimal("10000000000.99"), 10, 2));
    assertEquals(
        new BigDecimal("9999999999.99"),
        CobolNumeric.store(new BigDecimal("9999999999.99"), 10, 2));
  }

  @Test
  void fixedWidthReaderWriterRoundTrip() {
    String image =
        new FixedWidth.Writer()
            .alnum("AB", 4)
            .unsigned(7, 3)
            .signed(new BigDecimal("-1.50"), 6, 2)
            .build(13);
    assertEquals("AB  00700015}", image);
    FixedWidth.Reader r = new FixedWidth.Reader(image, 13);
    assertEquals("AB  ", r.alnum(4));
    assertEquals(7, r.unsigned(3));
    assertEquals(new BigDecimal("-1.50"), r.signed(6, 2));
    assertEquals(13, r.position());
  }

  @Test
  void fixedWidthWriterRejectsWrongLength() {
    assertThrows(IllegalStateException.class, () -> new FixedWidth.Writer().alnum("X", 3).build(4));
  }

  @Test
  void fixedWidthReaderSpacePadsShortImages() {
    FixedWidth.Reader r = new FixedWidth.Reader("short", 10);
    assertEquals("short     ", r.alnum(10));
  }

  @Test
  void padExtendsShortLinesAndKeepsExactOnes() {
    assertEquals("ab   ", FixedWidth.pad("ab", 5));
    assertEquals("abcde", FixedWidth.pad("abcde", 5));
  }
}
