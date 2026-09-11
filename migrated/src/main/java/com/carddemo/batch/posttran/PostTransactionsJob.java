package com.carddemo.batch.posttran;

import com.carddemo.codec.FixedWidth;
import com.carddemo.io.RecordFiles;
import com.carddemo.records.RejectRecord;
import com.carddemo.records.TransactionRecord;
import com.carddemo.store.CardDemoDatabase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Port of batch program CBTRN02C ({@code app/cbl/CBTRN02C.cbl}), job step POSTTRAN ({@code
 * app/jcl/POSTTRAN.jcl}): reads the daily transaction file, validates each record against the card
 * cross-reference and account master, posts valid ones (category balance, account balances,
 * transaction master) and writes rejects with a validation trailer.
 *
 * <p>Return code: 0 when nothing was rejected, 4 when at least one record was rejected (lines
 * 229-231). Any I/O failure the legacy program treats as fatal raises {@link AbendException}
 * (U0999). As with VSAM, updates made before an abend are kept (each legacy WRITE/REWRITE is
 * immediately durable) — see DEVIATIONS.md D7.
 */
public final class PostTransactionsJob {

  /** Counters and return code displayed at end of job (lines 227-231). */
  public record Result(long transactionsProcessed, long transactionsRejected, int returnCode) {}

  private final CardDemoDatabase db;
  private final Clock clock;
  private final Consumer<String> display;

  public PostTransactionsJob(CardDemoDatabase db, Clock clock, Consumer<String> display) {
    this.db = db;
    this.clock = clock;
    this.display = display;
  }

  /**
   * Runs the job.
   *
   * @param dailyTransactions DALYTRAN input (350-byte records, one per line)
   * @param rejects DALYREJS output (430-byte records, one per line); created/replaced
   */
  public Result run(Path dailyTransactions, Path rejects) throws SQLException {
    display.accept("START OF EXECUTION OF PROGRAM CBTRN02C");

    TransactionValidator validator = new TransactionValidator(db.cardXrefs(), db.accounts());
    TransactionPoster poster =
        new TransactionPoster(db.tranCatBals(), db.accounts(), db.transactions(), display);

    long processed = 0;
    long rejected = 0;
    try {
      // OPEN OUTPUT TRANSACT-FILE (line 256) empties the transaction master — DEVIATIONS.md D1.
      db.transactions().deleteAll();

      try (Stream<String> input =
              RecordFiles.stream(
                  dailyTransactions, l -> FixedWidth.pad(l, TransactionRecord.LENGTH));
          RecordFiles.Writer rejectWriter = new RecordFiles.Writer(rejects)) {
        Iterator<String> it = input.iterator();
        while (it.hasNext()) {
          String image = it.next();
          TransactionRecord daily = TransactionRecord.parse(image);
          processed++;
          ValidationResult v = validator.validate(daily);
          if (v.isValid()) {
            poster.post(
                daily,
                v.xref().orElseThrow().acctId(),
                v.account().orElseThrow(),
                Db2Timestamp.now(clock));
          } else {
            rejected++;
            rejectWriter.write(
                new RejectRecord(image, v.failReason(), v.failReasonDesc()).format());
          }
        }
      }
    } catch (IOException | UncheckedIOException e) {
      throw new AbendException("ERROR READING DALYTRAN FILE / WRITING TO REJECTS FILE", e);
    } catch (SQLException e) {
      throw new AbendException("ERROR ACCESSING VSAM REPLACEMENT TABLES: " + e.getMessage(), e);
    } finally {
      db.commit();
    }

    display.accept(String.format("TRANSACTIONS PROCESSED :%09d", processed));
    display.accept(String.format("TRANSACTIONS REJECTED  :%09d", rejected));
    int rc = rejected > 0 ? 4 : 0;
    display.accept("END OF EXECUTION OF PROGRAM CBTRN02C");
    return new Result(processed, rejected, rc);
  }
}
