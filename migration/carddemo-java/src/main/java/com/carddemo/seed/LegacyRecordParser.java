package com.carddemo.seed;

import com.carddemo.account.Account;
import com.carddemo.common.CobolFormats;
import com.carddemo.transaction.Transaction;
import com.carddemo.xref.CardXref;

/** Fixed-width parsers for the ASCII sample files in app/data/ASCII. */
public final class LegacyRecordParser {

  private LegacyRecordParser() {}

  /** acctdata.txt, layout app/cpy/CVACT01Y.cpy (300 bytes). */
  public static Account account(String line) {
    return new Account(
        Long.parseLong(line.substring(0, 11)),
        line.substring(11, 12),
        CobolFormats.zonedDecimal(line.substring(12, 24), 2),
        CobolFormats.zonedDecimal(line.substring(24, 36), 2),
        CobolFormats.zonedDecimal(line.substring(36, 48), 2),
        CobolFormats.date10(line.substring(48, 58)),
        CobolFormats.date10(line.substring(58, 68)),
        CobolFormats.date10(line.substring(68, 78)),
        CobolFormats.zonedDecimal(line.substring(78, 90), 2),
        CobolFormats.zonedDecimal(line.substring(90, 102), 2),
        CobolFormats.trimmed(line.substring(102, 112)),
        CobolFormats.trimmed(line.substring(112, 122)));
  }

  /** cardxref.txt, layout app/cpy/CVACT03Y.cpy. */
  public static CardXref cardXref(String line) {
    return new CardXref(
        line.substring(0, 16),
        Long.parseLong(line.substring(16, 25)),
        Long.parseLong(line.substring(25, 36)));
  }

  /** dailytran.txt (loads TRANSACT, see app/jcl/TRANFILE.jcl), layout app/cpy/CVTRA05Y.cpy. */
  public static Transaction transaction(String line) {
    return new Transaction(
        line.substring(0, 16),
        line.substring(16, 18),
        Integer.parseInt(line.substring(18, 22)),
        line.substring(22, 32).stripTrailing(),
        line.substring(32, 132).stripTrailing(),
        CobolFormats.zonedDecimal(line.substring(132, 143), 2),
        Long.parseLong(line.substring(143, 152)),
        CobolFormats.trimmed(line.substring(152, 202)),
        CobolFormats.trimmed(line.substring(202, 252)),
        CobolFormats.trimmed(line.substring(252, 262)),
        line.substring(262, 278),
        CobolFormats.timestamp26(line.substring(278, 304)),
        CobolFormats.timestamp26(line.substring(304, 330)));
  }
}
