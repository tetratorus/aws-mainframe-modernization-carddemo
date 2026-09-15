package com.carddemo.billpay;

import com.carddemo.account.Account;
import com.carddemo.account.AccountRepository;
import com.carddemo.common.BusinessRuleException;
import com.carddemo.transaction.Transaction;
import com.carddemo.transaction.TransactionIdGenerator;
import com.carddemo.transaction.TransactionRepository;
import com.carddemo.xref.CardXref;
import com.carddemo.xref.CardXrefRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Bill payment, transaction CB00: COBIL00C.cbl PROCESS-ENTER-KEY (lines 154-244). */
@Service
public class BillPayService {

  static final String MSG_ACCT_EMPTY = "Acct ID can NOT be empty...";
  static final String MSG_ACCT_NOT_NUMERIC = "Acct ID must be Numeric...";
  static final String MSG_INVALID_CONFIRM = "Invalid value. Valid values are (Y/N)...";
  static final String MSG_ACCT_NOT_FOUND = "Account ID NOT found...";
  static final String MSG_NOTHING_TO_PAY = "You have nothing to pay...";
  static final String MSG_CONFIRM = "Confirm to make a bill payment...";
  static final String MSG_PAID = "Payment successful. Your Transaction ID is %s.";

  static final String TRAN_TYPE_CD = "02";
  static final int TRAN_CAT_CD = 2;
  static final String TRAN_SOURCE = "POS TERM";
  static final String TRAN_DESC = "BILL PAYMENT - ONLINE";
  static final long MERCHANT_ID = 999_999_999L;
  static final String MERCHANT_NAME = "BILL PAYMENT";
  static final String MERCHANT_CITY = "N/A";
  static final String MERCHANT_ZIP = "N/A";

  private final AccountRepository accounts;
  private final CardXrefRepository xrefs;
  private final TransactionRepository transactions;
  private final TransactionIdGenerator tranIds;
  private final Clock clock;

  public BillPayService(
      AccountRepository accounts,
      CardXrefRepository xrefs,
      TransactionRepository transactions,
      TransactionIdGenerator tranIds,
      Clock clock) {
    this.accounts = accounts;
    this.xrefs = xrefs;
    this.transactions = transactions;
    this.tranIds = tranIds;
    this.clock = clock;
  }

  enum Confirm {
    YES,
    NO,
    BLANK
  }

  /** COBIL00C.cbl 173-191. */
  static Confirm parseConfirm(String confirm) {
    if (confirm == null || confirm.isBlank()) {
      return Confirm.BLANK;
    }
    return switch (confirm.strip()) {
      case "Y", "y" -> Confirm.YES;
      case "N", "n" -> Confirm.NO;
      default -> throw new BusinessRuleException(MSG_INVALID_CONFIRM, "CONFIRM");
    };
  }

  /** COBIL00C.cbl 159-164 (mandatory) plus MOVE to PIC 9(11) ACCT-ID (line 170). */
  static long parseAccountId(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      throw new BusinessRuleException(MSG_ACCT_EMPTY, "ACTIDIN");
    }
    String s = accountId.strip();
    if (s.length() > 11 || !s.chars().allMatch(Character::isDigit)) {
      throw new BusinessRuleException(MSG_ACCT_NOT_NUMERIC, "ACTIDIN");
    }
    return Long.parseLong(s);
  }

  @Transactional(isolation = Isolation.SERIALIZABLE)
  public BillPayResult process(BillPayRequest request) {
    long acctId = parseAccountId(request.accountId());
    Confirm confirm = parseConfirm(request.confirm());

    if (confirm == Confirm.NO) {
      // COBIL00C.cbl 178-181: CLEAR-CURRENT-SCREEN, nothing read or written.
      return new BillPayResult(acctId, null, false, null, null);
    }

    // READ-ACCTDAT-FILE with UPDATE (343-372).
    Account account =
        accounts
            .findForUpdate(acctId)
            .orElseThrow(() -> new BusinessRuleException(MSG_ACCT_NOT_FOUND, "ACTIDIN"));

    // 197-206.
    if (account.getCurrBal().signum() <= 0) {
      throw new BusinessRuleException(MSG_NOTHING_TO_PAY, "ACTIDIN");
    }

    if (confirm == Confirm.BLANK) {
      // 236-239: balance shown, awaiting confirmation.
      return new BillPayResult(acctId, account.getCurrBal(), false, null, MSG_CONFIRM);
    }

    // 210-235: pay the full current balance.
    CardXref xref =
        xrefs
            .findFirstByAcctIdOrderByCardNumAsc(acctId)
            .orElseThrow(() -> new BusinessRuleException(MSG_ACCT_NOT_FOUND, "ACTIDIN"));

    BigDecimal amount = account.getCurrBal();
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    String tranId = tranIds.next();
    transactions.save(
        new Transaction(
            tranId,
            TRAN_TYPE_CD,
            TRAN_CAT_CD,
            TRAN_SOURCE,
            TRAN_DESC,
            amount,
            MERCHANT_ID,
            MERCHANT_NAME,
            MERCHANT_CITY,
            MERCHANT_ZIP,
            xref.getCardNum(),
            now,
            now));
    account.setCurrBal(account.getCurrBal().subtract(amount));
    accounts.save(account);

    return new BillPayResult(
        acctId, account.getCurrBal(), true, tranId, String.format(MSG_PAID, tranId));
  }
}
