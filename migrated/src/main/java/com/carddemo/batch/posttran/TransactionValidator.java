package com.carddemo.batch.posttran;

import com.carddemo.codec.CobolNumeric;
import com.carddemo.records.AccountRecord;
import com.carddemo.records.CardXrefRecord;
import com.carddemo.records.TransactionRecord;
import com.carddemo.store.AccountRepository;
import com.carddemo.store.CardXrefRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Port of 1500-VALIDATE-TRAN / 1500-A-LOOKUP-XREF / 1500-B-LOOKUP-ACCT ({@code
 * app/cbl/CBTRN02C.cbl:370-422}).
 *
 * <p>Rules, in legacy order:
 *
 * <ol>
 *   <li>100 — card number not in CARDXREF (1500-A, lines 380-390).
 *   <li>101 — account from the cross-reference not in ACCTDATA (1500-B, lines 396-400).
 *   <li>102 — over limit: {@code CREDIT-LIMIT < CYC-CREDIT - CYC-DEBIT + AMT} (lines 403-412).
 *   <li>103 — {@code ACCT-EXPIRAION-DATE < ORIG-TS(1:10)} (lines 413-420).
 * </ol>
 *
 * <p>Rules 102 and 103 are both evaluated when the account is found; a 103 failure overwrites a 102
 * failure (last-wins, preserved from the legacy code — see DEVIATIONS.md D6). The over-limit
 * intermediate {@code WS-TEMP-BAL} is a {@code PIC S9(09)V99} field, one integer digit narrower
 * than the account balances, so its high-order digit truncation is reproduced (D3).
 */
public final class TransactionValidator {

  private final CardXrefRepository xrefs;
  private final AccountRepository accounts;

  public TransactionValidator(CardXrefRepository xrefs, AccountRepository accounts) {
    this.xrefs = xrefs;
    this.accounts = accounts;
  }

  public ValidationResult validate(TransactionRecord daily) throws SQLException {
    Optional<CardXrefRecord> xref = xrefs.find(daily.cardNum());
    if (xref.isEmpty()) {
      return new ValidationResult(
          ValidationResult.REASON_INVALID_CARD,
          ValidationResult.DESC_INVALID_CARD,
          Optional.empty(),
          Optional.empty());
    }

    Optional<AccountRecord> account = accounts.find(xref.get().acctId());
    if (account.isEmpty()) {
      return new ValidationResult(
          ValidationResult.REASON_ACCOUNT_NOT_FOUND,
          ValidationResult.DESC_ACCOUNT_NOT_FOUND,
          xref,
          Optional.empty());
    }

    AccountRecord acct = account.get();
    int reason = ValidationResult.REASON_OK;
    String desc = "";

    BigDecimal tempBal =
        CobolNumeric.store(
            acct.currCycCredit().subtract(acct.currCycDebit()).add(daily.amount()), 9, 2);
    if (acct.creditLimit().compareTo(tempBal) < 0) {
      reason = ValidationResult.REASON_OVERLIMIT;
      desc = ValidationResult.DESC_OVERLIMIT;
    }

    String origDate = daily.origTs().substring(0, 10);
    if (acct.expirationDate().compareTo(origDate) < 0) {
      reason = ValidationResult.REASON_AFTER_EXPIRATION;
      desc = ValidationResult.DESC_AFTER_EXPIRATION;
    }

    return new ValidationResult(reason, desc, xref, account);
  }
}
