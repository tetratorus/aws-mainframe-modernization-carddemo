package com.carddemo.records;

import com.carddemo.codec.FixedWidth;

/**
 * CARD-XREF-RECORD, legacy copybook {@code app/cpy/CVACT03Y.cpy} (50 bytes, KSDS CARDXREF keyed on
 * XREF-CARD-NUM).
 */
public record CardXrefRecord(String cardNum, long custId, long acctId, String filler) {

  public static final int LENGTH = 50;

  public static CardXrefRecord parse(String image) {
    FixedWidth.Reader r = new FixedWidth.Reader(image, LENGTH);
    return new CardXrefRecord(r.alnum(16), r.unsigned(9), r.unsigned(11), r.alnum(14));
  }

  public String format() {
    return new FixedWidth.Writer()
        .alnum(cardNum, 16)
        .unsigned(custId, 9)
        .unsigned(acctId, 11)
        .alnum(filler, 14)
        .build(LENGTH);
  }
}
