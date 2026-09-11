# CardDemo Legacy → Modern Migration Plan

Repo: `tetratorus/aws-mainframe-modernization-carddemo` (local: `/home/ubuntu/repos/carddemo`)
Playbook: Legacy Reverse-Engineering → Migration Planning & Pilot Module Migration
Status: **PLANNING — awaiting approval before any migration code is written.**
Legacy code is treated as read-only reference; nothing under `app/` will be modified.

---

## 1. Estate map

### 1.1 Size

| Area | Files | Lines | Notes |
|---|---|---|---|
| `app/cbl` (COBOL programs) | 31 | 20,650 | 17 CICS online, 13 batch, 1 callable utility |
| `app/cpy` (record/commarea copybooks) | 30 | 2,786 | VSAM record layouts, commarea, menu tables, lookup tables |
| `app/cpy-bms` (BMS symbolic maps) | 17 | — | Generated screen structures |
| `app/bms` (BMS map source) | 17 | 4,472 | 3270 screen definitions |
| `app/jcl` | 38 | 2,429 | File define/load jobs, batch jobs, GDG setup |
| `app/proc`, `app/csd`, `app/scheduler` | 6 | 1,281 | Procs, CICS resource defs, Control‑M schedule |
| `app/data/ASCII`, `app/data/EBCDIC` | 23 | 631 lines / ~450 KB | **Sample data for every VSAM file** (50 accounts, 50 cards, 300 daily transactions, 10 users) |

The README describes optional Db2 / IMS DB / MQ extensions (`COPAUS0C`, `COTRTLIC`, `COTRTUPC`, etc.). **Those programs, DDL and IMS artifacts are not present in this checkout** (`app/ddl`, `app/ims` do not exist), so they are out of scope. The menu tables still reference them (`app/cpy/COMEN02Y.cpy` option 11, `app/cpy/COADM02Y.cpy` options 5–6) and `COMEN01C` handles their absence at runtime (`app/cbl/COMEN01C.cbl:147-168`).

### 1.2 Module inventory

**Online (CICS + BMS). Pattern: pseudo‑conversational screen programs sharing `CARDDEMO-COMMAREA` (`app/cpy/COCOM01Y.cpy`).**

| Tran | Program | LOC | Purpose | Data touched | Risk / notes |
|---|---|---|---|---|---|
| CC00 | `COSGN00C` | 260 | Sign‑on, routes admin vs user | USRSEC (read) | Plain‑text password compare (`COSGN00C.cbl:223`) |
| CM00 | `COMEN01C` | 308 | User main menu, option auth | — | Menu table in `COMEN02Y.cpy` |
| CA00 | `COADM01C` | 288 | Admin menu | — | Menu table in `COADM02Y.cpy` |
| CAVW | `COACTVWC` | 941 | Account view | ACCTDAT, CUSTDAT, CXACAIX | Read‑only |
| CAUP | `COACTUPC` | **4,236** | Account + customer update | ACCTDAT, CUSTDAT, CXACAIX | Largest program; ~20 field edit routines (SSN, phone, state/zip, FICO, dates) |
| CCLI | `COCRDLIC` | 1,459 | Card list (paged browse) | CARDDAT, CARDAIX | Paging state machine; admin sees all, user filtered by account |
| CCDL | `COCRDSLC` | 887 | Card detail view | CARDDAT | Read‑only |
| CCUP | `COCRDUPC` | 1,560 | Card update | CARDDAT | Name/status/expiry edits; optimistic‑lock compare |
| CT00 | `COTRN00C` | 699 | Transaction list (paged) | TRANSACT | Paging |
| CT01 | `COTRN01C` | 330 | Transaction view | TRANSACT | Read‑only |
| CT02 | `COTRN02C` | 783 | Transaction add | TRANSACT, CXACAIX, CCXREF | Heavy field validation; calls `CSUTLDTC` |
| CR00 | `CORPT00C` | 649 | Submit report JCL via TDQ→INTRDR | TRANSACT (via batch) | Mainframe‑only mechanism (job submission) |
| CB00 | `COBIL00C` | 572 | Bill payment (pay balance in full) | ACCTDAT, CXACAIX, TRANSACT | Writes a transaction + updates balance |
| CU00 | `COUSR00C` | 695 | User list (paged) | USRSEC | Admin only |
| CU01 | `COUSR01C` | 299 | User add | USRSEC | Admin only |
| CU02 | `COUSR02C` | 414 | User update | USRSEC | Admin only |
| CU03 | `COUSR03C` | 359 | User delete | USRSEC | Admin only |

**Batch (JCL → COBOL). Pattern: sequential/KSDS file processing.**

| Job (JCL) | Program | LOC | Purpose | Inputs → Outputs |
|---|---|---|---|---|
| `POSTTRAN.jcl` | `CBTRN02C` | 731 | **Validate + post daily transactions** | DALYTRAN, CARDXREF, ACCTDATA, TCATBALF → TRANSACT, DALYREJS(+1), ACCTDATA, TCATBALF |
| `INTCALC.jcl` | `CBACT04C` | 652 | Monthly interest calc, resets cycle totals | TCATBALF, CARDXREF, DISCGRP, ACCTDATA → TRANSACT, ACCTDATA |
| `TRANREPT.jcl` | `CBTRN03C` | 649 | Transaction detail report w/ page/account/grand totals | TRANSACT, CARDXREF, TRANTYPE, TRANCATG, DATEPARM → REPTFILE |
| `CREASTMT.JCL` | `CBSTM03A` + `CBSTM03B` | 924+230 | Account statements (text + HTML) | XREF, CUSTDAT, ACCTDAT, TRNXFILE → STMTFILE, HTMLFILE |
| `COMBTRAN.jcl` | `CBTRN01C` | 494 | Older/simpler tran posting (lookup + display only) | DALYTRAN, XREF, ACCT… → console | Likely superseded by `CBTRN02C` (dead/legacy‑of‑legacy) |
| `READACCT/READCARD/READXREF/READCUST.jcl` | `CBACT01C/02C/03C`, `CBCUS01C` | 430/178/178/178 | Dump‑and‑print utilities | KSDS → SYSOUT |
| `CBEXPORT.jcl` / `CBIMPORT.jcl` | `CBEXPORT` / `CBIMPORT` | 582/487 | Branch migration export/import | multi‑file → flat, flat → multi‑file |
| `WAITSTEP.jcl` | `COBSWAIT` | 41 | Sleep utility | — |
| ~25 other JCL | (IDCAMS/SORT only) | — | Define/load VSAM, GDG bases, backups, FTP, TXT2PDF | Infra, not business logic |

**Shared / callable**

| Unit | Purpose | Used by |
|---|---|---|
| `CSUTLDTC.cbl` (157) | Date validation via LE `CEEDAYS`; returns severity + msg number (2513 = "date before 1582/after 9999" tolerated) | `COTRN02C`, `COACTUPC`, `CORPT00C` |
| `CSLKPCDY.cpy` | Lookup tables: valid US state codes, state+zip2 combos, phone area codes | `COACTUPC` |
| `CSUTLDWY/CSUTLDPY.cpy` | Inline date edit working storage / paragraphs | `COACTUPC`, `CORPT00C` |
| `CSMSG01Y/02Y.cpy`, `COTTL01Y.cpy`, `CSDAT01Y.cpy` | Common messages, titles, date formatting | all online |
| `COCOM01Y.cpy` | Commarea (session state: user id/type, from/to program, selected acct/card, page context) | all online |

### 1.3 Data stores (VSAM KSDS unless noted) → record copybooks

| DD / dataset | Copybook | Key | Record | Notes |
|---|---|---|---|---|
| ACCTDATA | `CVACT01Y.cpy` `ACCOUNT-RECORD` | `ACCT-ID` 9(11) | 300 | balances, limits, dates, group id |
| CARDDATA (+ AIX CARDAIX by acct) | `CVACT02Y.cpy` `CARD-RECORD` | `CARD-NUM` X(16) | 150 | CVV, embossed name, expiry, status |
| CARDXREF / CCXREF (+ AIX CXACAIX by acct) | `CVACT03Y.cpy` `CARD-XREF-RECORD` | `XREF-CARD-NUM` X(16) | 50 | card→cust→acct |
| CUSTDATA | `CVCUS01Y.cpy` `CUSTOMER-RECORD` | `CUST-ID` 9(9) | 500 | PII: SSN, DOB, govt id, FICO |
| TRANSACT | `CVTRA05Y.cpy` `TRAN-RECORD` | `TRAN-ID` X(16) | 350 | amounts, merchant, orig/proc ts |
| DALYTRAN (PS, sequential) | `CVTRA06Y.cpy` `DALYTRAN-RECORD` | — | 350 | input to posting |
| DALYREJS (GDG, PS) | `CBTRN02C.cbl:176-182` | — | 430 | 350 tran + 4 reason + 76 desc |
| TCATBALF | `CVTRA01Y.cpy` `TRAN-CAT-BAL-RECORD` | acct+type+cat | 50 | per‑category cycle balance |
| DISCGRP | `CVTRA02Y.cpy` `DIS-GROUP-RECORD` | group+type+cat | 50 | interest rate |
| TRANTYPE / TRANCATG | `CVTRA03Y.cpy` / `CVTRA04Y.cpy` | type / type+cat | 60 / 60 | reference data |
| USRSEC | `CSUSR01Y.cpy` `SEC-USER-DATA` | `SEC-USR-ID` X(8) | 80 | **plain‑text password**, type A/U |

Sample ASCII data exists for all of these in `app/data/ASCII/*.txt`; numeric fields use COBOL zoned‑decimal **sign overpunch** in the last digit (e.g. amount `0000005047G` → `G` = +7 → +504.77; `0000009190}` → `}` = −0 → −919.00). A decoder is required in the target.

---

## 2. Business rules & validation catalog (evidence‑cited)

Legend: **BR** business rule, **VAL** input validation, **AUTH** authorization, **ERR** error handling. Each row states how it will be verified post‑migration (U = unit test, E = e2e test, P = parity run vs legacy).

### 2.1 Batch posting — `CBTRN02C` (POSTTRAN)  ← proposed pilot

| ID | Rule | Source | Verify |
|---|---|---|---|
| BR‑P1 | Read DALYTRAN sequentially; status `10` = EOF; any other non‑`00` abends (`CEE3ABD` 999) | `CBTRN02C.cbl:345-368`, `:707-711` | U, P |
| BR‑P2 | Each record: reset reason to 0 → validate → post or reject; counters for processed & rejected | `:202-219` | U, P |
| VAL‑P1 | Card must exist in CARDXREF else reason **100** `INVALID CARD NUMBER FOUND`; account lookup skipped | `:370-392` | U, P |
| VAL‑P2 | Account (from xref) must exist in ACCTDATA else reason **101** `ACCOUNT RECORD NOT FOUND` | `:393-399` | U, P |
| VAL‑P3 | Over‑limit: `temp = CYC-CREDIT − CYC-DEBIT + amt`; reject **102** `OVERLIMIT TRANSACTION` if `CREDIT-LIMIT < temp` | `:403-413` | U, P |
| VAL‑P4 | Expiry: reject **103** `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` if `ACCT-EXPIRAION-DATE < ORIG-TS(1:10)` (string compare of `YYYY-MM-DD`) | `:414-420` | U, P |
| BR‑P3 | **Last‑wins**: if both 102 and 103 fail, reason reported is 103 (103 overwrites 102) | `:407-420` | U, P |
| BR‑P4 | Post: copy all DALYTRAN fields to TRAN‑RECORD; `TRAN-PROC-TS` = now in DB2 format `YYYY-MM-DD-HH.MM.SS.mm0000` | `:424-438`, `:692-705` | U (format), P (masked) |
| BR‑P5 | TCATBAL upsert: key acct+type+cat; not found (`23`) → create with balance=amt; found → `balance += amt` | `:467-542` | U, P |
| BR‑P6 | Account update: `CURR-BAL += amt`; `amt >= 0` → `CYC-CREDIT += amt` else `CYC-DEBIT += amt` (negative added to debit) | `:545-552` | U, P |
| BR‑P7 | Transaction written to TRANSACT keyed by `DALYTRAN-ID` (no re‑numbering) | `:562-579` | U, P |
| BR‑P8 | Reject record = 350‑byte original + 80‑byte trailer (`9(04)` reason + `X(76)` desc) | `:176-182`, `:446-465` | U, P |
| BR‑P9 | `RETURN-CODE = 4` if any rejects, else 0 | `:229-231` | U, E |
| ERR‑P1 | Every file open/read/write/close failure → display status → abend 999 | `:236-343`, `:582-690`, `:714-725` | U |

**Preserve‑vs‑fix decision points (default = preserve, documented):**
- D1 `TRANSACT-FILE` is opened `OUTPUT` (`:256`) — on a real KSDS this replaces content each run; JCL uses `DISP=SHR` on the existing cluster. Preserve semantics as "posting run produces the day's TRANSACT contents" or treat as append? **Ask.**
- D2 Account `REWRITE INVALID KEY` sets reason 109 (`:554-559`) but the record was already counted as posted and no reject is written. Preserve (log) or fix (reject)?
- D3 `WS-TEMP-BAL` is `S9(09)V99` (`:187`) while account fields are `S9(10)V99` — silent truncation possible at ≥ 1 billion. Preserve exact COBOL truncation or use full precision?
- D4 Overlimit formula uses `credit − debit + amt`, not `CURR-BAL + amt`. Preserve (it is the encoded business rule).
- D5 `1400-COMPUTE-FEES` in INTCALC is a stub (`CBACT04C.cbl:518-520`). Not pilot scope; note only.

### 2.2 Interest calculation — `CBACT04C` (INTCALC)

| ID | Rule | Source |
|---|---|---|
| BR‑I1 | Group TCATBAL by account; on account change, write accumulated interest to account and zero cycle credit/debit | `CBACT04C.cbl:188-222`, `:350-370` |
| BR‑I2 | Rate lookup by (acct group, type, cat); missing (`23`) → fall back to group `DEFAULT` | `:415-460` |
| BR‑I3 | `monthly_int = bal × rate / 1200`; skipped if rate = 0 | `:214-217`, `:462-470` |
| BR‑I4 | Interest tran: id = `PARM-DATE + 6‑digit seq`, type `01`, cat `05`, source `System`, desc `Int. for a/c <id>`, card from xref, orig=proc=now | `:473-498` |
| BR‑I5 | Requires run‑date PARM | `:176-180` |

### 2.3 Sign‑on & authorization

| ID | Rule | Source |
|---|---|---|
| VAL‑S1 | User ID and password required (`Please enter User ID ...` / `Please enter Password ...`) | `COSGN00C.cbl:117-130` |
| BR‑S1 | Both upper‑cased before lookup/compare | `:132-136` |
| AUTH‑S1 | User found & `SEC-USR-PWD = pwd` → type `A` → `COADM01C`, else `COMEN01C`; wrong pwd → `Wrong Password. Try again ...`; NOTFND(13) → `User not found. Try again ...` | `:221-257` |
| AUTH‑M1 | Menu option must be numeric, 1..count; regular user (`U`) blocked from `'A'` options: `No access - Admin Only option... ` | `COMEN01C.cbl:117-143`, `COMEN02Y.cpy` |
| AUTH‑C1 | Card list: admin sees all cards; user restricted to own account context | `COCRDLIC.cbl` (commarea `CDEMO-USRTYP-ADMIN`) |

### 2.4 User management (`COUSR01C/02C/03C`)

| ID | Rule | Source |
|---|---|---|
| VAL‑U1 | Add: first name, last name, user id, password, type all mandatory (`... can NOT be empty...`) | `COUSR01C.cbl:117-151` |
| BR‑U1 | Write keyed by user id; DUPKEY/DUPREC → `User ID already exist...`; success → `User <id> has been added ...` | `:238-274` |
| BR‑U2 | Update/Delete: fetch by id, `Please modify to update ...` if unchanged, PF5 to commit (`Press PF5 key to ...`) | `COUSR02C.cbl`, `COUSR03C.cbl` |

### 2.5 Transaction add (`COTRN02C`)

| ID | Rule | Source |
|---|---|---|
| VAL‑T1 | Either account id or card number required; must be numeric; resolved through CXACAIX/CCXREF to fill the other | `COTRN02C.cbl:193-230` |
| VAL‑T2 | Type, category, source, description, amount, orig date, proc date, merchant id/name/city/zip all mandatory | `:251-320` |
| VAL‑T3 | Type & category numeric; amount format `[-+]99999999.99`; dates `YYYY-MM-DD` and valid via `CSUTLDTC` (msg 2513 tolerated); merchant id numeric | `:322-436` |
| BR‑T1 | Confirm `Y/y` required to add; `N/blank` → prompt; other → `Invalid value. Valid values are (Y/N)...` | `:169-188` |
| BR‑T2 | New tran id = (last id in TRANSACT via STARTBR/READPREV) + 1 | `:444-451` |

### 2.6 Bill payment (`COBIL00C`)

| ID | Rule | Source |
|---|---|---|
| VAL‑B1 | Account id required; confirm `Y/N` semantic as above | `COBIL00C.cbl:158-191` |
| BR‑B1 | `ACCT-CURR-BAL <= 0` → `You have nothing to pay...` | `:197-206` |
| BR‑B2 | On confirm: tran id = last+1, type `02`, cat `2`, source `POS TERM`, desc `BILL PAYMENT - ONLINE`, amt = full balance, merchant 999999999/`BILL PAYMENT`/`N/A`; then `CURR-BAL −= amt` and rewrite | `:210-235` |
| BR‑B3 | Timestamp format `YYYY-MM-DD HH:MM:SS.000000` (differs from batch DB2 format — inconsistency to preserve or normalize) | `:249-267` |

### 2.7 Account/customer update (`COACTUPC`) & card update (`COCRDUPC`) — field edit library

| ID | Rule | Source |
|---|---|---|
| VAL‑A1 | Account id: 11‑digit numeric, non‑zero | `COACTUPC.cbl:1783-1820`, `COCRDUPC.cbl:721-758` |
| VAL‑A2 | Generic edits: mandatory, Y/N, alpha‑required/optional, alphanumeric, numeric‑required, signed 9V2 | `COACTUPC.cbl:1824-2221` |
| VAL‑A3 | US phone `(aaa)bbb-cccc` parts numeric; area code in lookup | `:2225-2427`, `CSLKPCDY.cpy` |
| VAL‑A4 | SSN `xxx-xx-xxxx`; part1 not `000`, `666`, `900‑999` | `:121-123`, `:2431-2489` |
| VAL‑A5 | State code in lookup; state+zip2 combo in lookup (`Invalid zip code for state`) | `:2493-2511`, `:2536-2558`, `CSLKPCDY.cpy:1013+` |
| VAL‑A6 | FICO 300‑850 | `:848-849`, `:2514-2531` |
| VAL‑A7 | Dates (DOB, open, expiry, reissue) via `CSUTLDPY` paragraphs | `:216-275` |
| VAL‑C1 | Card: 16‑digit numeric; embossed name alpha+space only; status `Y/N`; expiry month 1‑12; year 1950‑2099 | `COCRDUPC.cbl:762-945`, `:91-99` |
| BR‑A1 | Optimistic locking: re‑read and compare old vs new before REWRITE (`9700-CHECK-CHANGE-IN-REC`) | `COACTUPC.cbl:4109-4193`, `COCRDUPC.cbl:1498-1521` |

### 2.8 Reporting (`CBTRN03C`, `CBSTM03A/B`, `CORPT00C`)

| ID | Rule | Source |
|---|---|---|
| BR‑R1 | Report filtered by DATEPARM start/end; page totals, account totals on account change, grand totals | `CBTRN03C.cbl:159-212`, `CVTRA07Y.cpy` |
| BR‑R2 | Statements per xref record: customer + account header, transactions from sorted TRNXFILE, text + HTML outputs | `CBSTM03A.CBL:319-331`, `:461-486` |
| VAL‑R1 | Online report request: report type required; date parts required & valid | `CORPT00C.cbl` messages |
| BR‑R3 | Job submission by writing JCL to TDQ `JOBS` (INTRDR) — no direct equivalent off‑mainframe | `CORPT00C.cbl:462-520` |

---

## 3. Migration units & patterns

| # | Unit | Pattern | Legacy | ~LOC |
|---|---|---|---|---|
| U1 | **Daily transaction posting** | Batch job | `CBTRN02C`, `POSTTRAN.jcl`, 6 copybooks | 750 |
| U2 | Interest calculation | Batch job | `CBACT04C`, `INTCALC.jcl` | 650 |
| U3 | Transaction report | Batch/reporting | `CBTRN03C`, `TRANREPT.jcl`, `CVTRA07Y` | 700 |
| U4 | Statement generation | Batch/reporting | `CBSTM03A/B`, `CREASTMT.JCL` | 1,150 |
| U5 | Auth: sign‑on + menus | UI screen + AUTH | `COSGN00C`, `COMEN01C`, `COADM01C`, `COMEN02Y`, `COADM02Y` | 850 |
| U6 | User admin CRUD | CRUD/API + UI | `COUSR00C‑03C` | 1,770 |
| U7 | Account view/update | CRUD/API + UI (heavy validation) | `COACTVWC`, `COACTUPC`, `CSLKPCDY`, `CSUTLD*` | 5,300 |
| U8 | Card list/view/update | CRUD/API + UI | `COCRDLIC`, `COCRDSLC`, `COCRDUPC` | 3,900 |
| U9 | Transaction list/view/add | CRUD/API + UI | `COTRN00C‑02C`, `CSUTLDTC` | 1,970 |
| U10 | Bill payment | CRUD/API + UI | `COBIL00C` | 570 |
| U11 | Report request | UI → job trigger | `CORPT00C` | 650 |
| U12 | Data load / import‑export / print utilities | Batch utilities | `CBACT01‑03C`, `CBCUS01C`, `CBEXPORT/IMPORT`, `COBSWAIT`, IDCAMS JCL | 2,100 |
| — | `CBTRN01C` | Likely dead (superseded by U1) | 494 | confirm & drop |

Shared foundations needed by all units: record codecs (fixed‑width + zoned decimal), domain models per copybook, repository layer replacing VSAM KSDS/AIX, DB2‑style timestamp helper, date validation (replacing `CEEDAYS`).

---

## 4. Target stack — options

> **Decision (approved):** Option B — **Java 17**. Pilot built with Maven, SQLite (`sqlite-jdbc`) as the VSAM stand-in, JUnit 5, Spotless/google-java-format, shaded CLI jar. Spring Boot/Spring Batch and Postgres are deferred until the online tier (U5+) is started. The mapping conventions in §5 are restated for Java in `migrated/README.md`.

| | Option A (recommended for pilot) | Option B | Option C |
|---|---|---|---|
| Language | **Python 3.10+** | Java 17 + Spring Boot / Spring Batch | TypeScript / Node |
| Data | SQLite for pilot (stdlib), Postgres‑ready via SQLAlchemy later | Postgres + JPA | Postgres + Prisma |
| Online tier (later) | FastAPI + HTML/React | Spring MVC / REST + React | Express/Next |
| Quality gates | `ruff`, `mypy --strict`, `pytest` | Checkstyle/Spotless, `mvn test` | eslint, tsc, vitest |
| Fit | Fastest to make the batch pilot runnable & parity‑tested on this VM (Python 3.10 already present); decimal arithmetic via `decimal.Decimal` matches COBOL fixed‑point | Closest to typical enterprise mainframe targets (e.g. AWS Blu Age emits Java); heavier scaffolding | Not installed on this VM; would need setup |

Recommendation: **Option A** for the pilot to prove the process quickly; the playbooks produced afterwards will be language‑agnostic in structure (mapping tables, test requirements, parity method) so a switch to Java for the estate is possible. If the organisation's standard is Java, say so and I'll pilot in Option B instead.

Location for migrated code: new top‑level directory `migrated/` in this repo (legacy `app/` untouched). Alternative: separate repo — tell me if preferred.

---

## 5. Legacy → target mapping conventions

| Legacy | Target (Option A) |
|---|---|
| `app/cpy/CVxxxxY.cpy` record layout | `migrated/carddemo/records/<name>.py` — `@dataclass(frozen=True)` + fixed‑width codec (`from_bytes/to_bytes`) preserving field order & widths |
| `PIC S9(n)V99` | `decimal.Decimal` quantized to 2 dp; codec handles sign overpunch |
| `PIC X(n)` | `str`, right‑padded with spaces on encode, preserved as‑is on decode (trailing spaces matter for parity) |
| `PIC 9(n)` | `int` (zero‑padded on encode) |
| VSAM KSDS + AIX | SQLite table with PK (+ unique/secondary index for AIX); repository class `XxxRepository` with `get(key)`, `put`, `rewrite`, `browse_from(key)` |
| Sequential PS / GDG | Line‑oriented files (fixed‑width text); GDG generation = timestamped filename |
| Batch program `CBxxxxxC.cbl` | `migrated/carddemo/batch/<job>.py` with `run(paths/params) -> RunResult` and a CLI entrypoint; JCL DD names → CLI options |
| `RETURN-CODE` | process exit code |
| `DISPLAY` | `logging` (INFO) |
| `CEE3ABD` abend | raise `AbendError(code)` → exit 12 |
| CICS program `COxxxxxC.cbl` | service function (pure validation + repo calls) + thin API/UI adapter; commarea → session object |
| BMS map | HTML template / React form (later) |
| `CSUTLDTC`/`CEEDAYS` | `datetime.date.fromisoformat` wrapper reproducing the severity/2513 semantics |
| COBOL paragraph names | kept as function names in snake_case with a docstring citing `file:line` |

---

## 6. Pilot choice: **U1 — Daily transaction posting (`CBTRN02C` / POSTTRAN)**

Why:
- Small enough: 731 LOC, one program, six files, no CICS/BMS dependency.
- Representative: exercises every foundation the rest of the estate needs — copybook codecs, zoned‑decimal amounts, KSDS lookups & upserts, sequential in/out, reject handling, return codes, timestamp formatting, abend paths.
- Real business rules with numeric reason codes (100‑103) → clean unit tests.
- **Full parity is feasible**: sample DALYTRAN (300 rows), ACCTDATA, CARDXREF, TCATBALF exist in `app/data/ASCII`, and the program has no CICS calls, so it can be compiled with GnuCOBOL (installable on this VM via apt) and run against the same inputs; outputs (TRANSACT, DALYREJS, updated ACCTDATA/TCATBALF, counters, RC) compared byte‑for‑byte (timestamps masked).
- Its outputs feed U2/U3/U4, so it unlocks the batch chain.

Alternatives considered: `COBIL00C` (bill payment, 572 LOC) — good but needs a UI/CICS adapter decision first; `COUSR01C` (user add) — too trivial to generalise validations.

Pilot deliverable (as built, Java 17): `migrated/` Maven project with `carddemo.jar load | post-transactions | dump` CLI, SQLite‑backed repositories, loader for `app/data/ASCII` files, unit tests for VAL‑P1..P4/BR‑P3..P9 (32 tests), parity harness vs GnuCOBOL build of the unmodified legacy program on sample data + generated edge cases (`migrated/parity/`), and `migrated/DEVIATIONS.md` (D1‑D12; D9 is the only actual deviation — negative‑zero amounts).

Pilot outcome: byte‑identical stdout, rejects (38/300, all reason 102 on sample data), account / category / transaction masters and return code 4 against GnuCOBOL `-std=ibm`; edge set exercises 100/101/103, 102+103 last‑wins, refunds, S9(09)V99 & S9(10)V99 wrap‑around.

---

## 7. Migration order & effort (Devin sessions; assumes Option A, one engineer‑agent, no external waits)

| Phase | Units | Est. | Assumptions |
|---|---|---|---|
| 0 | Foundations (codecs, models for all 12 copybooks, repo layer, data loader) — largely built during pilot | included in pilot | |
| 1 | **Pilot U1** | 1 session | GnuCOBOL parity included |
| 2 | U2 Interest, U3 Report, U4 Statements | 1–2 sessions | same batch pattern; HTML statements need golden‑file tests |
| 3 | U5 Auth + menus, U6 User CRUD, U10 Bill pay | 1 session | first online pattern; API‑first, minimal UI |
| 4 | U9 Transactions, U8 Cards | 1–2 sessions | paging state machine is the new complexity |
| 5 | U7 Account/customer update | 1–2 sessions | 4.2k LOC edit library; lookup tables from `CSLKPCDY` |
| 6 | U11 Report request, U12 utilities | 0.5–1 session | U11 becomes "enqueue job" endpoint |
| **Total** | | **~6–9 sessions** | ±30%; excludes stakeholder review cycles and any Db2/IMS/MQ modules (absent here) |

---

## 8. Testing strategy

- **Lint/type**: `ruff check`, `ruff format --check`, `mypy --strict` on `migrated/` — CI‑gated.
- **Unit**: one test module per legacy paragraph group; every VAL/BR row above has ≥1 test; codec round‑trip tests on every sample record in `app/data/ASCII`.
- **E2E**: run the job CLI on the sample data set in a temp dir; assert counters, RC, reject file line count, and selected account balances.
- **Behavioral parity**: install `gnucobol`, compile `CBTRN02C` (stub `CEE3ABD`), load ASCII sample data into GnuCOBOL indexed files via a small loader program, run both implementations on identical inputs, diff outputs with timestamp fields masked. Parity results recorded in the PR. Same harness reused for U2–U4.
- **Deviations**: any intentional difference (D1–D4 decisions, timestamp normalisation) listed in `DEVIATIONS.md` with the legacy citation.
- Online units later: API contract tests + Playwright e2e per screen, parity via scripted comparison of validation messages (the message strings above are the contract).

---

## 9. Open questions (answers change the plan)

1. **Target stack**: Option A (Python) for the pilot — OK? Or Java (B)?
2. **Decisions D1–D4** (§2.1): preserve legacy behaviour by default, or fix? Default if no answer: preserve, document.
3. **Location**: `migrated/` directory in this repo vs new repo?
4. **Parity depth**: is GnuCOBOL‑based byte parity wanted for the pilot (adds ~0.3 session), or is sample‑data e2e + documented rules sufficient?
5. `CBTRN01C` — confirm it is dead code and can be excluded.
6. Timestamp formats differ between batch (`YYYY-MM-DD-HH.MM.SS.mm0000`) and online (`YYYY-MM-DD HH:MM:SS.000000`). Normalise in target or preserve per‑source?
7. Passwords are plain text in USRSEC (`COSGN00C.cbl:223`). Migration must hash — confirm this is an accepted (compliance‑driven) deviation for U5.
8. Notion spec: an org rule requires updating the corresponding Notion spec after committing code. **No Notion integration is available in this session** (only exa, linear, slack) and no spec reference was found in the repo — flagging now; tell me where the spec lives or confirm skipping.

## 10. Risks

- Zoned‑decimal/sign‑overpunch and EBCDIC vs ASCII data: mis‑decoding silently corrupts amounts → mitigated by codec round‑trip tests on all sample rows.
- COBOL truncation/rounding semantics (`COMPUTE` without `ROUNDED`, D3) → use `Decimal` with explicit quantize and test boundary values.
- Optional Db2/IMS/MQ modules referenced by menus but absent → scope explicitly excluded; menus will show "not installed" like legacy.
- Mainframe‑only mechanisms (INTRDR job submission, GDG, CICS pseudo‑conversation) need architectural substitutes → decided per unit in later phases.
- Large `COACTUPC` (4.2k LOC) hides most validation nuance → schedule last, reuse edit library from pilot conventions.
