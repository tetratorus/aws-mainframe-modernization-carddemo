package com.carddemo.records;

import com.carddemo.codec.FixedWidth;
import java.math.BigDecimal;

/**
 * TRAN-CAT-BAL-RECORD, legacy copybook {@code app/cpy/CVTRA01Y.cpy} (50 bytes, KSDS TCATBALF keyed
 * on TRAN-CAT-KEY = acct id + type code + category code).
 */
public record TranCatBalRecord(
    long acctId, String typeCd, int catCd, BigDecimal balance, String filler) {

  public static final int LENGTH = 50;

  public static TranCatBalRecord parse(String image) {
    FixedWidth.Reader r = new FixedWidth.Reader(image, LENGTH);
    return new TranCatBalRecord(
        r.unsigned(11), r.alnum(2), (int) r.unsigned(4), r.signed(11, 2), r.alnum(22));
  }

  public String format() {
    return new FixedWidth.Writer()
        .unsigned(acctId, 11)
        .alnum(typeCd, 2)
        .unsigned(catCd, 4)
        .signed(balance, 11, 2)
        .alnum(filler, 22)
        .build(LENGTH);
  }

  /**
   * Equivalent of {@code INITIALIZE TRAN-CAT-BAL-RECORD} followed by setting the key. INITIALIZE
   * does not touch FILLER, so the caller supplies the filler bytes the record area already held.
   */
  public static TranCatBalRecord initialized(long acctId, String typeCd, int catCd, String filler) {
    return new TranCatBalRecord(acctId, typeCd, catCd, BigDecimal.ZERO, filler);
  }

  public TranCatBalRecord withBalance(BigDecimal newBalance) {
    return new TranCatBalRecord(acctId, typeCd, catCd, newBalance, filler);
  }
}
