package com.carddemo.records;

import com.carddemo.codec.FixedWidth;
import java.math.BigDecimal;

/**
 * ACCOUNT-RECORD, legacy copybook {@code app/cpy/CVACT01Y.cpy} (300 bytes, KSDS ACCTDATA keyed on
 * ACCT-ID).
 */
public record AccountRecord(
    long acctId,
    String activeStatus,
    BigDecimal currBal,
    BigDecimal creditLimit,
    BigDecimal cashCreditLimit,
    String openDate,
    String expirationDate,
    String reissueDate,
    BigDecimal currCycCredit,
    BigDecimal currCycDebit,
    String addrZip,
    String groupId,
    String filler) {

  public static final int LENGTH = 300;

  public static AccountRecord parse(String image) {
    FixedWidth.Reader r = new FixedWidth.Reader(image, LENGTH);
    return new AccountRecord(
        r.unsigned(11),
        r.alnum(1),
        r.signed(12, 2),
        r.signed(12, 2),
        r.signed(12, 2),
        r.alnum(10),
        r.alnum(10),
        r.alnum(10),
        r.signed(12, 2),
        r.signed(12, 2),
        r.alnum(10),
        r.alnum(10),
        r.alnum(178));
  }

  public String format() {
    return new FixedWidth.Writer()
        .unsigned(acctId, 11)
        .alnum(activeStatus, 1)
        .signed(currBal, 12, 2)
        .signed(creditLimit, 12, 2)
        .signed(cashCreditLimit, 12, 2)
        .alnum(openDate, 10)
        .alnum(expirationDate, 10)
        .alnum(reissueDate, 10)
        .signed(currCycCredit, 12, 2)
        .signed(currCycDebit, 12, 2)
        .alnum(addrZip, 10)
        .alnum(groupId, 10)
        .alnum(filler, 178)
        .build(LENGTH);
  }

  /** Returns a copy with updated balance fields (all other fields preserved byte-for-byte). */
  public AccountRecord withBalances(
      BigDecimal newCurrBal, BigDecimal newCycCredit, BigDecimal newCycDebit) {
    return new AccountRecord(
        acctId,
        activeStatus,
        newCurrBal,
        creditLimit,
        cashCreditLimit,
        openDate,
        expirationDate,
        reissueDate,
        newCycCredit,
        newCycDebit,
        addrZip,
        groupId,
        filler);
  }
}
