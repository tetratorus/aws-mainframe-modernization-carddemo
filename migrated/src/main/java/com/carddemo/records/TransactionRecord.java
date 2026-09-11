package com.carddemo.records;

import com.carddemo.codec.FixedWidth;
import java.math.BigDecimal;

/**
 * TRAN-RECORD, legacy copybook {@code app/cpy/CVTRA05Y.cpy} (350 bytes, KSDS TRANSACT keyed on
 * TRAN-ID). The daily-transaction input layout DALYTRAN-RECORD ({@code app/cpy/CVTRA06Y.cpy}) is
 * byte-identical, so this type is used for both.
 */
public record TransactionRecord(
    String tranId,
    String typeCd,
    int catCd,
    String source,
    String description,
    BigDecimal amount,
    long merchantId,
    String merchantName,
    String merchantCity,
    String merchantZip,
    String cardNum,
    String origTs,
    String procTs,
    String filler) {

  public static final int LENGTH = 350;

  public static TransactionRecord parse(String image) {
    FixedWidth.Reader r = new FixedWidth.Reader(image, LENGTH);
    return new TransactionRecord(
        r.alnum(16),
        r.alnum(2),
        (int) r.unsigned(4),
        r.alnum(10),
        r.alnum(100),
        r.signed(11, 2),
        r.unsigned(9),
        r.alnum(50),
        r.alnum(50),
        r.alnum(10),
        r.alnum(16),
        r.alnum(26),
        r.alnum(26),
        r.alnum(20));
  }

  public String format() {
    return new FixedWidth.Writer()
        .alnum(tranId, 16)
        .alnum(typeCd, 2)
        .unsigned(catCd, 4)
        .alnum(source, 10)
        .alnum(description, 100)
        .signed(amount, 11, 2)
        .unsigned(merchantId, 9)
        .alnum(merchantName, 50)
        .alnum(merchantCity, 50)
        .alnum(merchantZip, 10)
        .alnum(cardNum, 16)
        .alnum(origTs, 26)
        .alnum(procTs, 26)
        .alnum(filler, 20)
        .build(LENGTH);
  }

  /**
   * Builds the posted TRAN-RECORD from a daily transaction (legacy 2000-POST-TRANSACTION, {@code
   * CBTRN02C.cbl:425-438}). Every field is copied from the input except TRAN-PROC-TS, which is set
   * to the processing timestamp; the trailing FILLER is not moved and therefore stays as
   * initialised (spaces).
   */
  public TransactionRecord posted(String processingTimestamp) {
    return new TransactionRecord(
        tranId,
        typeCd,
        catCd,
        source,
        description,
        amount,
        merchantId,
        merchantName,
        merchantCity,
        merchantZip,
        cardNum,
        origTs,
        processingTimestamp,
        "");
  }
}
