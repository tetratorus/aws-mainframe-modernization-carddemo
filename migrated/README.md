# CardDemo — migrated code (Java 17)

Target-stack home for the CardDemo modernization. `app/` stays as the read-only
legacy reference; everything new lives here. Plan and rule catalog:
[`../migration_plan.md`](../migration_plan.md). Behavioural decisions:
[`DEVIATIONS.md`](DEVIATIONS.md).

## Pilot: `CBTRN02C` / job step `POSTTRAN` (daily transaction posting)

| Legacy | Target |
|--------|--------|
| `CBTRN02C.cbl` main loop, validation, posting | `com.carddemo.batch.posttran.{PostTransactionsJob,TransactionValidator,TransactionPoster}` |
| `CEE3ABD` abend 999 | `AbendException` → exit code 999 |
| copybooks `CVACT01Y`, `CVACT03Y`, `CVTRA01Y`, `CVTRA05Y/06Y` | `com.carddemo.records.*` (parse/format to the identical fixed-width image) |
| zoned decimal `PIC S9(n)V99` with sign overpunch, `PIC 9(n)` | `com.carddemo.codec.{ZonedDecimal,CobolNumeric,FixedWidth}` |
| VSAM KSDS `ACCTFILE`, `XREFFILE`, `TCATBALF`, `TRANFILE` | SQLite tables via `com.carddemo.store.*` |
| `DALYTRAN`, `DALYREJS` sequential files | flat files, one record per line (`com.carddemo.io.RecordFiles`) |
| JCL `POSTTRAN` | `java -jar target/carddemo.jar post-transactions ...` |

### Build

```bash
cd migrated
mvn -B verify          # compile (-Xlint:all -Werror), unit tests, google-java-format check, shaded jar
```

### Run on the sample data

```bash
D=../app/data/ASCII
java -jar target/carddemo.jar load --db carddemo.db \
     --acctdata $D/acctdata.txt --cardxref $D/cardxref.txt --tcatbal $D/tcatbal.txt
java -jar target/carddemo.jar post-transactions --db carddemo.db \
     --dalytran $D/dailytran.txt --rejects rejects.txt      # exit 4: 38 of 300 rejected (102)
java -jar target/carddemo.jar dump --db carddemo.db --table account --out account.txt
```

### Parity with the legacy program

`parity/run_parity.sh` compiles the **unmodified** `app/cbl/CBTRN02C.cbl` with
GnuCOBOL (`-std=ibm -fsign=EBCDIC`, `CEE3ABD` stub), runs legacy and Java on the
sample data and on a generated edge-case set (`parity/edge/gen_edge_data.sh`:
reasons 100/101/103, 102+103, negative amounts, category-balance creation,
FILLER inheritance, `S9(09)V99`/`S9(10)V99` wrap-around) and diffs stdout,
rejects, account / category / transaction masters and the return code.

```bash
sudo apt-get install -y gnucobol3     # once
migrated/parity/run_parity.sh          # prints PARITY OK
```

## Layout

```
migrated/
  pom.xml                 Java 17, Maven; sqlite-jdbc, JUnit 5; spotless (google-java-format); shade
  src/main/java/com/carddemo/
    batch/posttran/       CBTRN02C port
    cli/Main.java         load | post-transactions | dump
    codec/                COBOL numeric / fixed-width codecs
    io/                   record file I/O (ISO-8859-1)
    records/              copybook record types
    store/                SQLite schema + repositories
  src/test/java/          unit tests (codecs, record round-trips, business rules)
  parity/                 GnuCOBOL parity harness
  DEVIATIONS.md           D1–D12 preserve/deviate decisions
```
