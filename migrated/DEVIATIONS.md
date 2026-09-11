# CBTRN02C → Java 17 pilot: behavioural decisions and deviations

Legacy reference: `app/cbl/CBTRN02C.cbl` (line numbers below). Default policy is
**preserve legacy behaviour byte-for-byte and document it**; the only items where
the target differs are marked *DEVIATION*. Each item is covered by a unit test
(`src/test/java`) and by the GnuCOBOL parity run (`parity/run_parity.sh`, sample
data + generated edge cases), unless stated otherwise.

| ID | Decision | Legacy evidence | Target behaviour |
|----|----------|-----------------|------------------|
| D1 | **Preserved.** `OPEN OUTPUT TRANSACT-FILE` empties the transaction master each run. | `:256` | `PostTransactionsJob` deletes all `transaction_` rows before posting. Confirm with the business whether the real job relies on a GDG/daily cluster; if appending is wanted, drop the `deleteAll()`. |
| D2 | **Preserved.** A failed account `REWRITE` sets reason 109 but the record has already been counted as posted and no reject is written. | `:554-559` | `TransactionPoster` logs `ACCOUNT REWRITE FAILED, REASON 109` and continues. Cannot occur with the SQLite backend (the row was read moments earlier), so it is not parity-tested. |
| D3 | **Preserved.** `WS-TEMP-BAL` is `PIC S9(09)V99` while the account fields it is computed from are `S9(10)V99`; the over-limit test therefore sees the high-order digit truncated. Likewise the `ADD ... TO` targets wrap at their picture width. | `:187`, `:403-412`, `:508`, `:527`, `:547-552` | `CobolNumeric.store(v, 9, 2)` / `store(v, 10, 2)` reproduce fraction truncation and high-order wrap (no `ON SIZE ERROR` in the source). Edge case `T000000000000010` in `parity/edge` proves parity with GnuCOBOL `-std=ibm`. Recommend fixing (use full precision + reject) once parity is no longer required. |
| D4 | **Preserved.** Over-limit uses `CYC-CREDIT − CYC-DEBIT + amt`, not `CURR-BAL + amt`. | `:403-405` | Same formula in `TransactionValidator`. |
| D5 | **Data format.** VSAM KSDS files are replaced by SQLite tables (`account`, `card_xref`, `tran_cat_bal`, `transaction_`); `DALYTRAN`/`DALYREJS` stay flat files, one fixed-width record per line, ISO-8859-1. Short lines in `app/data/ASCII` (e.g. `cardxref.txt` is 36 chars) are space-padded to the copybook length, CRLF is tolerated. | copybooks `CVACT01Y`, `CVACT03Y`, `CVTRA01Y`, `CVTRA05Y/06Y` | `records/*` parse/format round-trip every sample record to the identical image (`RecordRoundTripTest`). Decimal columns are stored as `TEXT` to keep exact scale-2 values. |
| D6 | **Preserved.** Rules 102 and 103 are both evaluated; when both fail the reported reason is 103 (last assignment wins). | `:407-420` | `TransactionValidator` keeps last-wins. |
| D7 | **Preserved (semantics).** Each legacy `WRITE`/`REWRITE` is immediately durable, so an abend mid-file leaves earlier postings in place. | `:582-690` | The job commits in `finally`, including on `AbendException`; `Main` exits 999. Should the business prefer all-or-nothing, replace the commit with a rollback. |
| D8 | **Preserved.** `INITIALIZE TRAN-CAT-BAL-RECORD` does not touch `FILLER`, so a newly created category-balance record carries the filler bytes of the last category record successfully `READ ... INTO` (spaces before any read). | `:474`, `:504` | `TransactionPoster.tranCatBalFiller` mirrors the working-storage record. Harmless, but recommend writing spaces once parity is no longer required. |
| D9 | ***DEVIATION.*** A daily transaction amount of negative zero (`0000000000}`) is copied byte-for-byte to `TRAN-AMT` by the legacy `MOVE`. | `:430` | `BigDecimal` has no −0; the posted amount is `+0.00` (`0000000000{`). Balances are unaffected. The parity harness folds −0 to +0 on both sides before comparing (`mask_tran`). |
| D10 | **Preserved.** Timestamp format `YYYY-MM-DD-HH.MM.SS.hh0000` — hundredths of a second from `CURRENT-DATE`, padded with four zeros. | `:692-705` | `Db2Timestamp`. Masked in the parity comparison (run time differs). |
| D11 | **Preserved.** Return code 4 when at least one record was rejected, else 0; file/database failures abend with U0999 (`CEE3ABD`). | `:227-231`, `:707-711` | `Main` exit codes: 0 / 4 / 999; usage error 2; other I/O or SQL failure outside the job 12. |
| D12 | **Preserved.** `TCATBAL record not found for key : <17-char key>.. Creating.` and the end-of-job counter lines are written to stdout in the legacy format so operational scripts that grep SYSOUT keep working. | `:476-477`, `:227-228` | Byte-identical stdout is asserted by the parity run. |

## Not in scope of the pilot

* `CBTRN01C` (pre-posting xref check) and `CBTRN03C` (reporting) — later units.
* EBCDIC input (`app/data/EBCDIC`) — the ASCII copies are used; an EBCDIC→ISO-8859-1
  transcoding step with `-fsign=EBCDIC`-style overpunch mapping would be added at load time.
* JCL/scheduler integration (`POSTTRAN.jcl` step chaining, `DISP`, GDGs).
