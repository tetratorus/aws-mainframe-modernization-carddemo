package com.carddemo.billpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/** Rules BL-3 / V-10 of migration_plan.md, source COBIL00C.cbl 154-244. */
class BillPayServiceTest {

  private static final long ACCT = 1L;
  private final AccountRepository accounts = mock(AccountRepository.class);
  private final CardXrefRepository xrefs = mock(CardXrefRepository.class);
  private final TransactionRepository transactions = mock(TransactionRepository.class);
  private final TransactionIdGenerator ids = mock(TransactionIdGenerator.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-09-15T20:00:00.123Z"), ZoneOffset.UTC);
  private final BillPayService service =
      new BillPayService(accounts, xrefs, transactions, ids, clock);

  private Account account;

  @BeforeEach
  void setUp() {
    account = account(new BigDecimal("194.00"));
    when(accounts.findForUpdate(ACCT)).thenReturn(Optional.of(account));
    when(xrefs.findFirstByAcctIdOrderByCardNumAsc(ACCT))
        .thenReturn(Optional.of(new CardXref("4111111111111111", 5L, ACCT)));
    when(ids.next()).thenReturn("0000000000000301");
  }

  private static Account account(BigDecimal balance) {
    return new Account(
        ACCT,
        "Y",
        balance,
        new BigDecimal("2020.00"),
        new BigDecimal("1020.00"),
        LocalDate.of(2014, 11, 20),
        LocalDate.of(2025, 5, 20),
        LocalDate.of(2025, 5, 20),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "A000000000",
        null);
  }

  // COBIL00C.cbl 159-164
  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void rejectsEmptyAccountId(String input) {
    assertThatThrownBy(() -> service.process(new BillPayRequest(input, null)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage("Acct ID can NOT be empty...");
    assertThatThrownBy(() -> service.process(new BillPayRequest(null, null)))
        .hasMessage("Acct ID can NOT be empty...");
  }

  @Test
  void rejectsNonNumericAccountId() {
    assertThatThrownBy(() -> service.process(new BillPayRequest("ABC", null)))
        .hasMessage("Acct ID must be Numeric...");
  }

  // COBIL00C.cbl 185-190
  @ParameterizedTest
  @ValueSource(strings = {"X", "yes", "1"})
  void rejectsInvalidConfirmValue(String confirm) {
    assertThatThrownBy(() -> service.process(new BillPayRequest("1", confirm)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage("Invalid value. Valid values are (Y/N)...")
        .extracting("field")
        .isEqualTo("CONFIRM");
    verify(transactions, never()).save(any());
  }

  // COBIL00C.cbl 359-363
  @Test
  void rejectsUnknownAccount() {
    when(accounts.findForUpdate(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.process(new BillPayRequest("99", null)))
        .hasMessage("Account ID NOT found...");
  }

  // COBIL00C.cbl 197-206
  @ParameterizedTest
  @ValueSource(strings = {"0.00", "-12.50"})
  void rejectsNonPositiveBalance(String balance) {
    when(accounts.findForUpdate(ACCT)).thenReturn(Optional.of(account(new BigDecimal(balance))));
    assertThatThrownBy(() -> service.process(new BillPayRequest("1", "Y")))
        .hasMessage("You have nothing to pay...");
    verify(transactions, never()).save(any());
  }

  // COBIL00C.cbl 182-184, 236-239
  @Test
  void blankConfirmShowsBalanceAndAsksForConfirmation() {
    BillPayResult result = service.process(new BillPayRequest("1", ""));
    assertThat(result.paid()).isFalse();
    assertThat(result.currentBalance()).isEqualByComparingTo("194.00");
    assertThat(result.message()).isEqualTo("Confirm to make a bill payment...");
    verify(transactions, never()).save(any());
    verify(accounts, never()).save(any());
  }

  // COBIL00C.cbl 178-181
  @ParameterizedTest
  @ValueSource(strings = {"N", "n"})
  void confirmNoClearsScreenWithoutReadingAccount() {
    BillPayResult result = service.process(new BillPayRequest("1", "N"));
    assertThat(result.paid()).isFalse();
    assertThat(result.currentBalance()).isNull();
    verify(accounts, never()).findForUpdate(ACCT);
  }

  // COBIL00C.cbl 210-235
  @ParameterizedTest
  @ValueSource(strings = {"Y", "y"})
  void confirmYesPaysFullBalance(String confirm) {
    BillPayResult result = service.process(new BillPayRequest("00000000001", confirm));

    assertThat(result.paid()).isTrue();
    assertThat(result.tranId()).isEqualTo("0000000000000301");
    assertThat(result.currentBalance()).isEqualByComparingTo("0.00");
    assertThat(account.getCurrBal()).isEqualByComparingTo("0.00");

    ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
    verify(transactions).save(saved.capture());
    Transaction t = saved.getValue();
    assertThat(t.getTranId()).isEqualTo("0000000000000301");
    assertThat(t.getTypeCd()).isEqualTo("02");
    assertThat(t.getCatCd()).isEqualTo(2);
    assertThat(t.getSource()).isEqualTo("POS TERM");
    assertThat(t.getDescription()).isEqualTo("BILL PAYMENT - ONLINE");
    assertThat(t.getAmount()).isEqualByComparingTo("194.00");
    assertThat(t.getCardNum()).isEqualTo("4111111111111111");
    assertThat(t.getMerchantId()).isEqualTo(999_999_999L);
    assertThat(t.getMerchantName()).isEqualTo("BILL PAYMENT");
    assertThat(t.getMerchantCity()).isEqualTo("N/A");
    assertThat(t.getMerchantZip()).isEqualTo("N/A");
    // GET-CURRENT-TIMESTAMP zeroes the microseconds (line 266); ORIG-TS = PROC-TS.
    LocalDateTime expected = LocalDateTime.of(2026, 9, 15, 20, 0, 0);
    assertThat(t.getOrigTs()).isEqualTo(expected);
    assertThat(t.getProcTs()).isEqualTo(expected);
    verify(accounts).save(account);
  }
}
