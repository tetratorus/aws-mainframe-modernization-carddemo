package com.carddemo.records;

import com.carddemo.codec.FixedWidth;

/**
 * REJECT-RECORD written to DALYREJS (legacy {@code CBTRN02C.cbl:176-182}): the 350-byte input
 * transaction image followed by an 80-byte validation trailer (4-digit reason code + 76-char
 * description).
 */
public record RejectRecord(String tranImage, int failReason, String failReasonDesc) {

  public static final int LENGTH = 430;

  public static RejectRecord parse(String image) {
    FixedWidth.Reader r = new FixedWidth.Reader(image, LENGTH);
    return new RejectRecord(r.alnum(350), (int) r.unsigned(4), r.alnum(76));
  }

  public String format() {
    return new FixedWidth.Writer()
        .alnum(tranImage, 350)
        .unsigned(failReason, 4)
        .alnum(failReasonDesc, 76)
        .build(LENGTH);
  }
}
