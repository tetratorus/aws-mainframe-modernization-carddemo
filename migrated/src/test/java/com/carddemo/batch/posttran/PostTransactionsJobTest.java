package com.carddemo.batch.posttran;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carddemo.records.AccountRecord;
import com.carddemo.records.CardXrefRecord;
import com.carddemo.records.RejectRecord;
import com.carddemo.records.TranCatBalRecord;
import com.carddemo.records.TransactionRecord;
import com.carddemo.store.CardDemoDatabase;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Business-rule tests for the CBTRN02C port; mirrors parity/edge/gen_edge_data.sh. */
class PostTransactionsJobTest {

  static final Clock CLOCK = Clock.fixed(Instant.parse("2024-03-05T14:15:16.170Z"), ZoneOffset.UTC);
  static final String TS = "2024-03-05-14.15.16.170000";

  @TempDir Path tmp;
  CardDemoDatabase db;
  List<String> displayed = new ArrayList<>();

  @BeforeEach
  void openDb() throws SQLException {
    db = CardDemoDatabase.inMemory();
  }

  @AfterEach
  void closeDb() throws SQLException {
    db.close();
  }

  // ---- fixtures -------------------------------------------------------------

  static AccountRecord acct(
      long id, String limit, String expiry, String cycCredit, String cycDebit) {
    return new AccountRecord(
        id,
        "Y",
        new BigDecimal("500.00"),
        new BigDecimal(limit),
        new BigDecimal("0.00"),
        "2020-01-01",
        expiry,
        "2025-01-01",
        new BigDecimal(cycCredit),
        new BigDecimal(cycDebit),
        "10001",
        "A0000000",
        "");
  }

  static TransactionRecord tran(String id, String amount, String card, String origTs) {
    return new TransactionRecord(
        id,
        "01",
        1,
        "POS TERM",
        "desc",
        new BigDecimal(amount),
        1,
        "m",
        "c",
        "z",
        card,
        origTs,
        "",
        "");
  }

  void seed(List<AccountRecord> accts, List<CardXrefRecord> xrefs, List<TranCatBalRecord> cats)
      throws SQLException {
    for (AccountRecord a : accts) db.accounts().insert(a);
    for (CardXrefRecord x : xrefs) db.cardXrefs().insert(x);
    for (TranCatBalRecord c : cats) db.tranCatBals().insert(c);
    db.commit();
  }

  PostTransactionsJob.Result run(TransactionRecord... daily) throws IOException, SQLException {
    Path in = tmp.resolve("dailytran.txt");
    Files.write(in, Stream.of(daily).map(TransactionRecord::format).toList());
    Path rej = tmp.resolve("rejects.txt");
    return new PostTransactionsJob(db, CLOCK, displayed::add).run(in, rej);
  }

  List<RejectRecord> rejects() throws IOException {
    Path rej = tmp.resolve("rejects.txt");
    return Files.readAllLines(rej).stream().map(RejectRecord::parse).toList();
  }

  static final String CARD1 = "4000000000000001";
  static final String CARD2 = "4000000000000002";
  static final String CARD3 = "4000000000000003";

  void seedStandard() throws SQLException {
    seed(
        List.of(
            acct(1, "1000.00", "2030-12-31", "0.00", "0.00"),
            acct(2, "100.00", "2020-01-01", "0.00", "0.00")),
        List.of(
            new CardXrefRecord(CARD1, 1, 1, ""),
            new CardXrefRecord(CARD2, 2, 2, ""),
            new CardXrefRecord(CARD3, 3, 3, "")),
        List.of());
  }

  // ---- validation -------------------------------------------------------------

  @Test
  void validTransactionPostsAndReturnsZero() throws Exception {
    seedStandard();
    PostTransactionsJob.Result r = run(tran("T1", "123.45", CARD1, "2024-03-01 10:00:00.000000"));

    assertEquals(0, r.returnCode());
    assertEquals(1, r.transactionsProcessed());
    assertEquals(0, r.transactionsRejected());
    assertTrue(rejects().isEmpty());

    AccountRecord a = db.accounts().find(1).orElseThrow();
    assertEquals(new BigDecimal("623.45"), a.currBal());
    assertEquals(new BigDecimal("123.45"), a.currCycCredit());
    assertEquals(new BigDecimal("0.00"), a.currCycDebit());

    TranCatBalRecord c = db.tranCatBals().find(1, "01", 1).orElseThrow();
    assertEquals(new BigDecimal("123.45"), c.balance());

    List<TransactionRecord> posted = db.transactions().all();
    assertEquals(1, posted.size());
    assertEquals(TS, posted.get(0).procTs());
    assertEquals("T1", posted.get(0).tranId().trim());
    assertTrue(
        displayed.contains("TCATBAL record not found for key : 00000000001010001.. Creating."));
  }

  @Test
  void unknownCardIsReason100() throws Exception {
    seedStandard();
    PostTransactionsJob.Result r =
        run(tran("T2", "5.00", "4999999999999999", "2024-03-01 10:00:00.000000"));
    assertEquals(4, r.returnCode());
    RejectRecord rej = rejects().get(0);
    assertEquals(100, rej.failReason());
    assertEquals("INVALID CARD NUMBER FOUND", rej.failReasonDesc().trim());
    assertEquals(RejectRecord.LENGTH, rej.format().length());
    assertTrue(rej.tranImage().startsWith("T2 "));
  }

  @Test
  void missingAccountIsReason101() throws Exception {
    seedStandard();
    run(tran("T3", "5.00", CARD3, "2024-03-01 10:00:00.000000"));
    assertEquals(101, rejects().get(0).failReason());
  }

  @Test
  void overLimitIsReason102() throws Exception {
    seedStandard();
    run(tran("T6", "1000.01", CARD1, "2024-03-01 10:00:00.000000"));
    assertEquals(102, rejects().get(0).failReason());
    assertEquals(new BigDecimal("500.00"), db.accounts().find(1).orElseThrow().currBal());
    assertTrue(db.transactions().all().isEmpty());
  }

  @Test
  void exactlyAtLimitPasses() throws Exception {
    seedStandard();
    PostTransactionsJob.Result r = run(tran("T6", "1000.00", CARD1, "2024-03-01 10:00:00.000000"));
    assertEquals(0, r.returnCode());
  }

  @Test
  void expiredAccountIsReason103() throws Exception {
    seedStandard();
    run(tran("T4", "5.00", CARD2, "2024-03-01 10:00:00.000000"));
    assertEquals(103, rejects().get(0).failReason());
  }

  @Test
  void expiryOnOrigDateStillPasses() throws Exception {
    seedStandard();
    PostTransactionsJob.Result r = run(tran("T9", "5.00", CARD2, "2020-01-01 23:59:59.000000"));
    assertEquals(0, r.returnCode());
  }

  @Test
  void overLimitAndExpiredReports103LastWins() throws Exception {
    seedStandard();
    run(tran("T5", "5000.00", CARD2, "2024-03-01 10:00:00.000000"));
    assertEquals(103, rejects().get(0).failReason());
  }

  @Test
  void overLimitUsesCycleCreditMinusCycleDebitNotCurrentBalance() throws Exception {
    // D4: limit 1000, cyc credit 900, cyc debit 100 -> headroom 200 even though currBal is 500
    seed(
        List.of(acct(1, "1000.00", "2030-12-31", "900.00", "100.00")),
        List.of(new CardXrefRecord(CARD1, 1, 1, "")),
        List.of());
    assertEquals(0, run(tran("A", "200.00", CARD1, "2024-03-01 10:00:00.000000")).returnCode());
    assertEquals(4, run(tran("B", "0.01", CARD1, "2024-03-01 10:00:00.000000")).returnCode());
    assertEquals(102, rejects().get(0).failReason());
  }

  @Test
  void tempBalanceTruncationLetsHugeBalancePassLimitCheck() throws Exception {
    // D3: WS-TEMP-BAL is S9(09)V99 -> 9999999999.99 + 1.00 wraps to 0.99
    seed(
        List.of(acct(4, "9999999999.99", "2030-12-31", "9999999999.99", "0.00")),
        List.of(new CardXrefRecord(CARD1, 4, 4, "")),
        List.of(new TranCatBalRecord(4, "01", 1, new BigDecimal("999999999.00"), "")));
    PostTransactionsJob.Result r = run(tran("T10", "1.00", CARD1, "2024-03-01 10:00:00.000000"));
    assertEquals(0, r.returnCode());
    AccountRecord a = db.accounts().find(4).orElseThrow();
    assertEquals(new BigDecimal("0.99"), a.currCycCredit()); // S9(10)V99 wrap
    assertEquals(new BigDecimal("0.00"), db.tranCatBals().find(4, "01", 1).orElseThrow().balance());
  }

  // ---- posting -------------------------------------------------------------

  @Test
  void negativeAmountGoesToCycleDebit() throws Exception {
    seedStandard();
    run(tran("T7", "-25.00", CARD1, "2024-03-01 10:00:00.000000"));
    AccountRecord a = db.accounts().find(1).orElseThrow();
    assertEquals(new BigDecimal("475.00"), a.currBal());
    assertEquals(new BigDecimal("0.00"), a.currCycCredit());
    assertEquals(new BigDecimal("-25.00"), a.currCycDebit());
  }

  @Test
  void existingCategoryBalanceIsIncrementedAndFillerKept() throws Exception {
    seed(
        List.of(
            acct(6, "1000.00", "2030-12-31", "0.00", "0.00"),
            acct(1, "1000.00", "2030-12-31", "0.00", "0.00")),
        List.of(new CardXrefRecord(CARD1, 1, 1, ""), new CardXrefRecord(CARD2, 6, 6, "")),
        List.of(
            new TranCatBalRecord(6, "01", 1, new BigDecimal("123.45"), "FILLERFILLERFILLERFILL")));
    run(
        tran("T7", "-25.00", CARD2, "2024-03-01 10:00:00.000000"),
        tran("T8", "10.00", CARD1, "2024-03-01 10:01:00.000000"));

    TranCatBalRecord existing = db.tranCatBals().find(6, "01", 1).orElseThrow();
    assertEquals(new BigDecimal("98.45"), existing.balance());
    assertEquals("FILLERFILLERFILLERFILL", existing.filler());

    // D8: record created right after a successful read inherits that record's FILLER bytes
    TranCatBalRecord created = db.tranCatBals().find(1, "01", 1).orElseThrow();
    assertEquals(new BigDecimal("10.00"), created.balance());
    assertEquals("FILLERFILLERFILLERFILL", created.filler());
  }

  @Test
  void transactionMasterIsEmptiedAtStart() throws Exception {
    seedStandard();
    db.transactions().insert(tran("OLD", "1.00", CARD1, "2023-01-01 00:00:00.000000"));
    db.commit();
    run(tran("NEW", "1.00", CARD1, "2024-03-01 10:00:00.000000"));
    List<String> ids = db.transactions().all().stream().map(t -> t.tranId().trim()).toList();
    assertEquals(List.of("NEW"), ids);
  }

  @Test
  void countersAndDisplaysMatchLegacyFormat() throws Exception {
    seedStandard();
    PostTransactionsJob.Result r =
        run(
            tran("T1", "1.00", CARD1, "2024-03-01 10:00:00.000000"),
            tran("T2", "1.00", "4999999999999999", "2024-03-01 10:00:00.000000"),
            tran("T3", "1.00", CARD3, "2024-03-01 10:00:00.000000"));
    assertEquals(3, r.transactionsProcessed());
    assertEquals(2, r.transactionsRejected());
    assertEquals(4, r.returnCode());
    assertEquals("START OF EXECUTION OF PROGRAM CBTRN02C", displayed.get(0));
    assertTrue(displayed.contains("TRANSACTIONS PROCESSED :000000003"));
    assertTrue(displayed.contains("TRANSACTIONS REJECTED  :000000002"));
    assertEquals("END OF EXECUTION OF PROGRAM CBTRN02C", displayed.get(displayed.size() - 1));
    assertFalse(Files.readAllLines(tmp.resolve("rejects.txt")).isEmpty());
  }

  @Test
  void db2TimestampFormat() {
    assertEquals(TS, Db2Timestamp.now(CLOCK));
  }
}
