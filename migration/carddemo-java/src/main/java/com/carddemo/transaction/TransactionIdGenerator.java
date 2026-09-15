package com.carddemo.transaction;

import java.math.BigInteger;
import org.springframework.stereotype.Component;

/**
 * Legacy TRAN-ID allocation: STARTBR from HIGH-VALUES, READPREV to get the highest key, then +1
 * into a PIC 9(16) (COBIL00C.cbl 212-219, COTRN02C.cbl 433-459). Allocation is serialised with a
 * transaction-scoped advisory lock, so the caller must be inside a READ COMMITTED transaction that
 * also inserts the row before committing.
 */
@Component
public class TransactionIdGenerator {

  static final long LOCK_KEY = 0x54524149_44L; // "TRANID"

  private final TransactionRepository transactions;

  public TransactionIdGenerator(TransactionRepository transactions) {
    this.transactions = transactions;
  }

  public String next() {
    transactions.lockTranIdAllocation(LOCK_KEY);
    String last =
        transactions.findFirstByOrderByTranIdDesc().map(Transaction::getTranId).orElse("0");
    return format(new BigInteger(last.strip()).add(BigInteger.ONE));
  }

  static String format(BigInteger value) {
    String s = value.toString();
    if (s.length() > 16) {
      // PIC 9(16) truncates high-order digits on overflow.
      s = s.substring(s.length() - 16);
    }
    return "0".repeat(16 - s.length()) + s;
  }
}
