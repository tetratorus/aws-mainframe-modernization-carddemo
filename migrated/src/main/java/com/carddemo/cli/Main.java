package com.carddemo.cli;

import com.carddemo.batch.posttran.AbendException;
import com.carddemo.batch.posttran.PostTransactionsJob;
import com.carddemo.io.RecordFiles;
import com.carddemo.records.AccountRecord;
import com.carddemo.records.CardXrefRecord;
import com.carddemo.records.TranCatBalRecord;
import com.carddemo.records.TransactionRecord;
import com.carddemo.store.CardDemoDatabase;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point.
 *
 * <pre>
 * carddemo load  --db FILE --acctdata F --cardxref F [--tcatbal F]
 * carddemo post-transactions --db FILE --dalytran F --rejects F
 * carddemo dump  --db FILE --table account|card_xref|tran_cat_bal|transaction --out F
 * </pre>
 *
 * {@code load} replaces the contents of the given tables from fixed-width record files (the IDCAMS
 * REPRO step of the legacy setup jobs); {@code dump} writes them back in the same format so that
 * the results can be compared with the legacy VSAM clusters.
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    int rc;
    try {
      rc = run(args, System.out, System.err, Clock.systemDefaultZone());
    } catch (AbendException e) {
      System.err.println("ABENDING PROGRAM: " + e.getMessage());
      if (e.getCause() != null) {
        System.err.println("  cause: " + e.getCause());
      }
      rc = AbendException.ABEND_CODE;
    } catch (UsageException e) {
      System.err.println(e.getMessage());
      rc = 2;
    } catch (IOException | SQLException e) {
      System.err.println("ERROR: " + e.getMessage());
      rc = 12;
    }
    System.exit(rc);
  }

  static int run(String[] args, PrintStream out, PrintStream err, Clock clock)
      throws IOException, SQLException {
    if (args.length == 0) {
      throw new UsageException(usage());
    }
    Map<String, String> opts = parseOptions(Arrays.copyOfRange(args, 1, args.length));
    Path dbFile = Path.of(require(opts, "db"));
    switch (args[0]) {
      case "load" -> {
        try (CardDemoDatabase db = CardDemoDatabase.open(dbFile)) {
          load(db, opts, out);
        }
        return 0;
      }
      case "post-transactions" -> {
        Path dalytran = Path.of(require(opts, "dalytran"));
        Path rejects = Path.of(require(opts, "rejects"));
        try (CardDemoDatabase db = CardDemoDatabase.open(dbFile)) {
          PostTransactionsJob.Result r =
              new PostTransactionsJob(db, clock, out::println).run(dalytran, rejects);
          return r.returnCode();
        }
      }
      case "dump" -> {
        String table = require(opts, "table");
        Path outFile = Path.of(require(opts, "out"));
        try (CardDemoDatabase db = CardDemoDatabase.open(dbFile)) {
          dump(db, table, outFile);
        }
        return 0;
      }
      default -> throw new UsageException("unknown command '" + args[0] + "'\n" + usage());
    }
  }

  private static void load(CardDemoDatabase db, Map<String, String> opts, PrintStream out)
      throws IOException, SQLException {
    boolean any = false;
    if (opts.containsKey("acctdata")) {
      any = true;
      List<AccountRecord> recs =
          RecordFiles.readAll(Path.of(opts.get("acctdata")), AccountRecord::parse);
      db.accounts().deleteAll();
      for (AccountRecord r : recs) {
        db.accounts().insert(r);
      }
      out.println("account: loaded " + recs.size() + " records");
    }
    if (opts.containsKey("cardxref")) {
      any = true;
      List<CardXrefRecord> recs =
          RecordFiles.readAll(Path.of(opts.get("cardxref")), CardXrefRecord::parse);
      db.cardXrefs().deleteAll();
      for (CardXrefRecord r : recs) {
        db.cardXrefs().insert(r);
      }
      out.println("card_xref: loaded " + recs.size() + " records");
    }
    if (opts.containsKey("tcatbal")) {
      any = true;
      List<TranCatBalRecord> recs =
          RecordFiles.readAll(Path.of(opts.get("tcatbal")), TranCatBalRecord::parse);
      db.tranCatBals().deleteAll();
      for (TranCatBalRecord r : recs) {
        db.tranCatBals().insert(r);
      }
      out.println("tran_cat_bal: loaded " + recs.size() + " records");
    }
    if (opts.containsKey("transact")) {
      any = true;
      List<TransactionRecord> recs =
          RecordFiles.readAll(Path.of(opts.get("transact")), TransactionRecord::parse);
      db.transactions().deleteAll();
      for (TransactionRecord r : recs) {
        db.transactions().insert(r);
      }
      out.println("transaction: loaded " + recs.size() + " records");
    }
    if (!any) {
      throw new UsageException(
          "load: give at least one of --acctdata, --cardxref, --tcatbal, --transact");
    }
    db.commit();
  }

  private static void dump(CardDemoDatabase db, String table, Path outFile)
      throws IOException, SQLException {
    try (RecordFiles.Writer w = new RecordFiles.Writer(outFile)) {
      switch (table) {
        case "account" -> {
          for (AccountRecord r : db.accounts().all()) {
            w.write(r.format());
          }
        }
        case "card_xref" -> {
          for (CardXrefRecord r : db.cardXrefs().all()) {
            w.write(r.format());
          }
        }
        case "tran_cat_bal" -> {
          for (TranCatBalRecord r : db.tranCatBals().all()) {
            w.write(r.format());
          }
        }
        case "transaction" -> {
          for (TransactionRecord r : db.transactions().all()) {
            w.write(r.format());
          }
        }
        default -> throw new UsageException("dump: unknown table '" + table + "'");
      }
    }
  }

  private static Map<String, String> parseOptions(String[] args) {
    Map<String, String> opts = new HashMap<>();
    for (int i = 0; i < args.length; i++) {
      if (!args[i].startsWith("--") || i + 1 >= args.length) {
        throw new UsageException("bad option '" + args[i] + "'\n" + usage());
      }
      opts.put(args[i].substring(2), args[++i]);
    }
    return opts;
  }

  private static String require(Map<String, String> opts, String key) {
    String v = opts.get(key);
    if (v == null) {
      throw new UsageException("missing --" + key + "\n" + usage());
    }
    return v;
  }

  private static String usage() {
    return """
        usage:
          carddemo load --db FILE [--acctdata F] [--cardxref F] [--tcatbal F] [--transact F]
          carddemo post-transactions --db FILE --dalytran F --rejects F
          carddemo dump --db FILE --table account|card_xref|tran_cat_bal|transaction --out F""";
  }

  static final class UsageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    UsageException(String message) {
      super(message);
    }
  }
}
