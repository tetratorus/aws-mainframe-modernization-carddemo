package com.carddemo.batch.posttran;

import com.carddemo.codec.CobolNumeric;
import com.carddemo.records.AccountRecord;
import com.carddemo.records.TranCatBalRecord;
import com.carddemo.records.TransactionRecord;
import com.carddemo.store.AccountRepository;
import com.carddemo.store.TranCatBalRepository;
import com.carddemo.store.TransactionRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Port of 2000-POST-TRANSACTION and its sub-paragraphs 2700/2800/2900 ({@code
 * app/cbl/CBTRN02C.cbl:424-579}).
 *
 * <p>Order of side effects is preserved: category balance, then account balances, then the posted
 * transaction record.
 */
public final class TransactionPoster {

  /** Reason code set (but never reported) when the account REWRITE fails; see DEVIATIONS.md D2. */
  public static final int REASON_ACCOUNT_REWRITE_FAILED = 109;

  private final TranCatBalRepository tranCatBals;
  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final Consumer<String> display;

  /**
   * FILLER bytes of the working-storage TRAN-CAT-BAL-RECORD. {@code INITIALIZE} leaves FILLER
   * untouched, so a newly created category-balance record carries the filler of the last record
   * successfully read (spaces before any read). Kept for byte parity — DEVIATIONS.md D8.
   */
  private String tranCatBalFiller = "";

  public TransactionPoster(
      TranCatBalRepository tranCatBals,
      AccountRepository accounts,
      TransactionRepository transactions,
      Consumer<String> display) {
    this.tranCatBals = tranCatBals;
    this.accounts = accounts;
    this.transactions = transactions;
    this.display = display;
  }

  /**
   * Posts a validated transaction.
   *
   * @param daily the input daily transaction
   * @param acctId account id resolved through the card cross-reference
   * @param account the account record as read during validation
   * @param processingTimestamp DB2-format timestamp for TRAN-PROC-TS
   */
  public void post(
      TransactionRecord daily, long acctId, AccountRecord account, String processingTimestamp)
      throws SQLException {
    TransactionRecord posted = daily.posted(processingTimestamp);
    updateTranCatBal(daily, acctId);
    updateAccount(daily, account);
    transactions.insert(posted);
  }

  /** 2700-UPDATE-TCATBAL (lines 467-542): create-or-add the per-category balance. */
  private void updateTranCatBal(TransactionRecord daily, long acctId) throws SQLException {
    Optional<TranCatBalRecord> existing = tranCatBals.find(acctId, daily.typeCd(), daily.catCd());
    if (existing.isEmpty()) {
      display.accept(
          "TCATBAL record not found for key : "
              + String.format("%011d%s%04d", acctId, daily.typeCd(), daily.catCd())
              + ".. Creating.");
      TranCatBalRecord created =
          TranCatBalRecord.initialized(acctId, daily.typeCd(), daily.catCd(), tranCatBalFiller);
      tranCatBals.insert(created.withBalance(addToCatBal(created.balance(), daily.amount())));
    } else {
      TranCatBalRecord cur = existing.get();
      tranCatBalFiller = cur.filler();
      if (!tranCatBals.rewrite(cur.withBalance(addToCatBal(cur.balance(), daily.amount())))) {
        throw new AbendException("ERROR REWRITING TRANSACTION BALANCE FILE");
      }
    }
  }

  /** 2800-UPDATE-ACCOUNT-REC (lines 545-560). */
  private void updateAccount(TransactionRecord daily, AccountRecord account) throws SQLException {
    BigDecimal amt = daily.amount();
    BigDecimal currBal = addToAcctField(account.currBal(), amt);
    BigDecimal cycCredit = account.currCycCredit();
    BigDecimal cycDebit = account.currCycDebit();
    if (amt.signum() >= 0) {
      cycCredit = addToAcctField(cycCredit, amt);
    } else {
      cycDebit = addToAcctField(cycDebit, amt);
    }
    if (!accounts.rewrite(account.withBalances(currBal, cycCredit, cycDebit))) {
      display.accept(
          "ACCOUNT REWRITE FAILED, REASON "
              + REASON_ACCOUNT_REWRITE_FAILED
              + " (not rejected, legacy behaviour)");
    }
  }

  /** {@code ADD ... TO TRAN-CAT-BAL} — target is {@code PIC S9(09)V99}. */
  private static BigDecimal addToCatBal(BigDecimal current, BigDecimal amount) {
    return CobolNumeric.store(current.add(amount), 9, 2);
  }

  /** {@code ADD ... TO ACCT-*} — targets are {@code PIC S9(10)V99}. */
  private static BigDecimal addToAcctField(BigDecimal current, BigDecimal amount) {
    return CobolNumeric.store(current.add(amount), 10, 2);
  }
}
