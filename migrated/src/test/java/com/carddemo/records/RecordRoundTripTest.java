package com.carddemo.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carddemo.codec.FixedWidth;
import com.carddemo.io.RecordFiles;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Every sample record must parse and re-format to the identical byte image. */
class RecordRoundTripTest {

  static final Path DATA = Path.of("..", "app", "data", "ASCII");

  private static <T> void roundTrip(
      String file, int length, Function<String, T> parse, Function<T, String> format)
      throws IOException {
    Path p = DATA.resolve(file);
    assertTrue(Files.exists(p), p + " missing");
    List<String> images = RecordFiles.readAll(p, l -> FixedWidth.pad(l.replace("\r", ""), length));
    assertTrue(images.size() > 0);
    for (String image : images) {
      assertEquals(image, format.apply(parse.apply(image)));
    }
  }

  @Test
  void accountRecords() throws IOException {
    roundTrip("acctdata.txt", AccountRecord.LENGTH, AccountRecord::parse, AccountRecord::format);
  }

  @Test
  void cardXrefRecords() throws IOException {
    roundTrip("cardxref.txt", CardXrefRecord.LENGTH, CardXrefRecord::parse, CardXrefRecord::format);
  }

  @Test
  void tranCatBalRecords() throws IOException {
    roundTrip(
        "tcatbal.txt", TranCatBalRecord.LENGTH, TranCatBalRecord::parse, TranCatBalRecord::format);
  }

  @Test
  void dailyTransactionRecords() throws IOException {
    roundTrip(
        "dailytran.txt",
        TransactionRecord.LENGTH,
        TransactionRecord::parse,
        TransactionRecord::format);
  }

  @Test
  void accountFieldsDecodeWithScale() throws IOException {
    String first = RecordFiles.readAll(DATA.resolve("acctdata.txt"), Function.identity()).get(0);
    AccountRecord a = AccountRecord.parse(first);
    assertEquals(2, a.currBal().scale());
    assertEquals(2, a.creditLimit().scale());
    assertEquals(10, a.expirationDate().length());
  }

  @Test
  void postedTransactionCopiesFieldsAndSetsProcTs() {
    TransactionRecord daily =
        new TransactionRecord(
            "T1",
            "01",
            1,
            "SRC",
            "desc",
            new BigDecimal("-12.34"),
            5,
            "m",
            "c",
            "z",
            "4000",
            "2024-01-01 00:00:00.000000",
            "old",
            "F");
    TransactionRecord posted = daily.posted("2024-01-02-00.00.00.000000");
    assertEquals("2024-01-02-00.00.00.000000", posted.procTs());
    assertEquals(daily.amount(), posted.amount());
    assertEquals(daily.cardNum(), posted.cardNum());
    assertEquals("", posted.filler().trim());
    assertEquals(TransactionRecord.LENGTH, posted.format().length());
  }

  @Test
  void rejectRecordLayoutIs430Bytes() {
    String tran = "X".repeat(350);
    String image = new RejectRecord(tran, 102, "OVERLIMIT TRANSACTION").format();
    assertEquals(RejectRecord.LENGTH, image.length());
    assertEquals(tran, image.substring(0, 350));
    assertEquals("0102", image.substring(350, 354));
    assertEquals("OVERLIMIT TRANSACTION", image.substring(354).trim());
    RejectRecord back = RejectRecord.parse(image);
    assertEquals(102, back.failReason());
  }
}
