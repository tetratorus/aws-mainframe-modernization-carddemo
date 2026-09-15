# CardDemo Migration Plan

Playbook: *Legacy Reverse-Engineering → Migration Planning & Pilot Module Migration* (`!legacy_migration_plan`)
Legacy source: `tetratorus/aws-mainframe-modernization-carddemo` (cloned to `~/repos/carddemo`, treated read-only). All paths below are relative to `app/`.

## 1. Scope (restated)

Reverse-engineer the CardDemo COBOL/CICS/VSAM credit-card application, catalogue its business rules and validations, propose a target architecture, and pick one pilot module to migrate end-to-end. **No target stack was specified** — options are proposed in §5 and need a decision before the pilot starts.

Proposed scope boundary: the **base application** (17 CICS screens + 2 core batch jobs + statement/report batch, all VSAM). The three optional modules (IMS/DB2/MQ authorizations, DB2 transaction-type maintenance, MQ account extraction) are inventoried but proposed as **phase 2** because they depend on middleware (IMS, MQ, DB2) that the base app does not.

## 2. Estate map

Totals: 29 base COBOL programs (~19.5k LOC), 12 optional-module programs (~9.5k LOC), 30 copybooks, 17 BMS mapsets (+4 optional), 38 JCL + 2 procs, 2 assembler utilities, sample data in both EBCDIC and ASCII (`data/`).

### 2.1 Online (CICS) — base

| Tran | Program | LOC | Function | Data touched | Risk / notes |
|---|---|---|---|---|---|
| CC00 | `cbl/COSGN00C.cbl` | 260 | Sign-on | USRSEC | Plaintext password compare |
| CM00 | `cbl/COMEN01C.cbl` | 308 | Main menu (11 options, `cpy/COMEN02Y.cpy`) | — | Role gate |
| CA00 | `cbl/COADM01C.cbl` | 288 | Admin menu (`cpy/COADM02Y.cpy`) | — | |
| CAVW | `cbl/COACTVWC.cbl` | 941 | Account view | ACCTDAT, CXACAIX, CUSTDAT | |
| CAUP | `cbl/COACTUPC.cbl` | 4236 | Account + customer update | ACCTDAT, CUSTDAT, CXACAIX | Largest program; ~20 field validators; optimistic locking |
| CCLI | `cbl/COCRDLIC.cbl` | 1459 | Card list (paged, filter by acct/card) | CARDDAT, CARDAIX | Pagination state in COMMAREA |
| CCDL | `cbl/COCRDSLC.cbl` | 887 | Card detail | CARDDAT | |
| CCUP | `cbl/COCRDUPC.cbl` | 1560 | Card update | CARDDAT | Optimistic locking |
| CT00 | `cbl/COTRN00C.cbl` | 699 | Transaction list (paged) | TRANSACT | |
| CT01 | `cbl/COTRN01C.cbl` | 330 | Transaction detail | TRANSACT | |
| CT02 | `cbl/COTRN02C.cbl` | 783 | Transaction add | TRANSACT, CXACAIX, CCXREF | Calls `CSUTLDTC` date validator |
| CR00 | `cbl/CORPT00C.cbl` | 649 | Transaction report request | TDQ→JES internal reader | Generates JCL text at runtime (lines 81–127) |
| CB00 | `cbl/COBIL00C.cbl` | 572 | Bill payment | ACCTDAT, CXACAIX, TRANSACT | Read-modify-write across 2 files |
| CU00 | `cbl/COUSR00C.cbl` | 695 | User list (paged) | USRSEC | Admin |
| CU01 | `cbl/COUSR01C.cbl` | 299 | Add user | USRSEC | Admin |
| CU02 | `cbl/COUSR02C.cbl` | 414 | Update user | USRSEC | Admin |
| CU03 | `cbl/COUSR03C.cbl` | 359 | Delete user | USRSEC | Admin |

### 2.2 Batch — base

| Job | Program | LOC | Function | Notes |
|---|---|---|---|---|
| POSTTRAN | `cbl/CBTRN02C.cbl` | 731 | Post daily transactions; write rejects; update balances | Core business logic; RC=4 if any rejects (l.229–231) |
| INTCALC | `cbl/CBACT04C.cbl` | 652 | Monthly interest per category balance; writes interest txns | PARM = run date (`jcl/INTCALC.jcl` l.22) |
| CREASTMT | `cbl/CBSTM03A.CBL` + `CBSTM03B.CBL` | 924+230 | Statement generation (text + HTML) | Uses TIOT control-block walking (l.272–283) — z/OS-specific |
| TRANREPT | `cbl/CBTRN03C.cbl` | 649 | Transaction detail report for date range | Submitted from CR00 |
| — | `cbl/CBTRN01C.cbl` | 494 | Earlier posting variant (xref/account lookup, DISPLAY only) | Likely superseded by CBTRN02C — confirm; candidate dead code |
| READACCT/READCARD/READCUST/READXREF | `CBACT01C/02C/03C`, `CBCUS01C` | 430/178/178/178 | Dump/print files | Utilities; CBACT01C calls ASM `COBDATFT` |
| CBEXPORT/CBIMPORT | `cbl/CBEXPORT.cbl`, `CBIMPORT.cbl` | 582/487 | Customer branch export/import | Uses `cpy/CVEXPORT.cpy` |
| WAITSTEP | `cbl/COBSWAIT.cbl` → `asm/MVSWAIT.asm` | 41 | Sleep utility | Infra; not migrated |
| ~30 IDCAMS/IEBGENER/SORT jobs | `jcl/*` | — | Define/load/backup VSAM files, GDGs, AIX | Replace with DB DDL + seed scripts |
| `cbl/CSUTLDTC.cbl` | 157 | Shared date validation subroutine (CEEDAYS wrapper) | Becomes a shared library function |

### 2.3 Optional modules (phase 2)

| Module | Programs | LOC | Middleware |
|---|---|---|---|
| Pending authorizations | `app-authorization-ims-db2-mq/cbl/*` (8 pgms) | ~4.3k | IMS DB, DB2, MQ |
| Transaction-type maintenance | `app-transaction-type-db2/cbl/*` (3 pgms) | ~4.0k | DB2 |
| Account extraction | `app-vsam-mq/cbl/*` (2 pgms) | ~1.1k | MQ |

### 2.4 Data stores (VSAM KSDS + AIX)

| File | Copybook | Key | Notes |
|---|---|---|---|
| ACCTDAT | `cpy/CVACT01Y.cpy` | ACCT-ID 9(11) | balances S9(10)V99, credit limits, cycle credit/debit, group id |
| CARDDAT (+CARDAIX by acct) | `cpy/CVACT02Y.cpy` | CARD-NUM X(16) | CVV, embossed name, expiry, status |
| CCXREF (+CXACAIX by acct) | `cpy/CVACT03Y.cpy` | CARD-NUM | card→cust→acct |
| CUSTDAT | `cpy/CVCUS01Y.cpy` | CUST-ID 9(9) | PII: SSN, DOB, FICO |
| TRANSACT | `cpy/CVTRA05Y.cpy` | TRAN-ID X(16) | amount S9(9)V99, timestamps X(26) |
| DALYTRAN (input) | `cpy/CVTRA06Y.cpy` | — | same layout as TRANSACT |
| TCATBALF | `cpy/CVTRA01Y.cpy` | acct+type+cat | per-category balance |
| DISCGRP | `cpy/CVTRA02Y.cpy` | group+type+cat | interest rate S9(4)V99 |
| TRANTYPE / TRANCATG | `CVTRA03Y` / `CVTRA04Y` | type / type+cat | reference data |
| USRSEC | `cpy/CSUSR01Y.cpy` | USR-ID X(8) | plaintext PWD X(8), TYPE A/U |
| Session state | `cpy/COCOM01Y.cpy` | — | CICS COMMAREA: user, role, selected acct/card, last map |

ASCII versions of all seed files exist in `data/ASCII/*.txt` (zoned-decimal sign nibbles, e.g. `{` = +0) — usable for target-side seeding and parity tests.

## 3. Business logic catalogue (base app)

| # | Rule | Source |
|---|---|---|
| BL-1 | Sign-on: user id + password upper-cased, looked up in USRSEC; password compared as plain equality; type `A` → admin menu, else main menu | `COSGN00C.cbl` 132–139, 209–236 |
| BL-2 | Menu option must be numeric, 1..11, and options flagged `A` are rejected for `U` users with "No access - Admin Only option" | `COMEN01C.cbl` 120–145; `cpy/COMEN02Y.cpy` 21–98 |
| BL-3 | **Bill payment**: full current balance is paid; blocked if balance ≤ 0 ("You have nothing to pay"); requires explicit `Y` confirm; new TRAN-ID = (last TRAN-ID) + 1 via READPREV from HIGH-VALUES; writes txn type `02` cat `2`, source `POS TERM`, merchant 999999999 "BILL PAYMENT", then `ACCT-CURR-BAL -= TRAN-AMT` | `COBIL00C.cbl` 173–244 |
| BL-4 | **Transaction add**: TRAN-ID assigned the same way (max+1); requires acct *or* card, resolves via XREF; all 11 fields mandatory; confirm `Y` before write | `COTRN02C.cbl` 196–316, 433–459 |
| BL-5 | **Posting** (per daily txn): card must exist in XREF (reject 100), account must exist (101), `credit_limit >= cyc_credit - cyc_debit + amt` else reject 102 "OVERLIMIT", `acct_expiration >= orig_ts[0:10]` else reject 103; rejected records written to DALYREJS with reason; job RC=4 if any reject | `CBTRN02C.cbl` 370–421, 229–231 |
| BL-6 | Posting side-effects: `TCATBAL += amt` (create record if missing), `ACCT-CURR-BAL += amt`, amt ≥ 0 → cyc_credit += amt else cyc_debit += amt; TRAN-PROC-TS set to now in DB2 timestamp format | `CBTRN02C.cbl` 424–442, 526–560, 692–703 |
| BL-7 | **Interest**: per TCATBAL row, rate from DISCGRP by (acct group, type, cat), fall back to group `DEFAULT` if not found (status 23); `monthly_int = bal * rate / 1200`; one txn per category (type `01` cat `05`, source `System`, desc `Int. for a/c <id>`, TRAN-ID = PARM-date + running suffix); at account break: `ACCT-CURR-BAL += total_int`, cycle credit & debit reset to 0 | `CBACT04C.cbl` 188–222, 350–356, 415–438, 462–500 |
| BL-8 | Fees: paragraph is a stub (no logic) | `CBACT04C.cbl` 518 |
| BL-9 | Account/customer update uses optimistic locking: re-read and compare before REWRITE; outcomes "Could not lock", "Record changed by some one else", "Update failed" | `COACTUPC.cbl` 2562–2643, 4109–4193; `COCRDUPC.cbl` 206–210 |
| BL-10 | Card list: admin sees all cards, user filtered to own account context; paging via COMMAREA first/last keys | `COCRDLIC.cbl` 312–325, 1239 |
| BL-11 | Report request builds a JCL stream in memory and writes it to a TDQ bound to the JES internal reader (async batch trigger) | `CORPT00C.cbl` 81–127, 238–255, 531 |
| BL-12 | Statement: per customer/account, iterate cards, sum transaction amounts, count per card; emits text + HTML | `CBSTM03A.CBL` 374–429, 819–823 |
| BL-13 | (Phase 2) Authorization: available = credit_limit − balance (IMS summary if present, else account master); decline `05` if amt > available or account unknown, else approve `00` for full amount | `app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl` 655–695 |

## 4. Validation catalogue (must be preserved)

| # | Validation | Source | Post-migration verification |
|---|---|---|---|
| V-1 | User id / password non-blank; "User not found" vs "Wrong Password" messages distinct | `COSGN00C.cbl` 117–130, 242, 249 | unit + e2e |
| V-2 | Account id: 11-digit numeric, non-zero | `COACTUPC.cbl` 1783–1820; `COCRDUPC.cbl` 190 | unit |
| V-3 | Card number: 16-digit numeric | `COCRDUPC.cbl` 194; `COTRN02C.cbl` 210–214 | unit |
| V-4 | Amount format `[+-]99999999.99` (sign, 8 digits, `.`, 2 digits), parsed with NUMVAL-C | `COTRN02C.cbl` 338–346, 385 | unit (property test on formatter/parser) |
| V-5 | Dates `YYYY-MM-DD` shape check, then semantic validity via `CSUTLDTC` (CEEDAYS) | `COTRN02C.cbl` 350–423; `cbl/CSUTLDTC.cbl` | unit |
| V-6 | Report date range: month/day/year each mandatory and valid; report type must be selected; confirm must be Y/N | `CORPT00C.cbl` 261–296, 331–374, 400–420, 438, 485–488 | unit + e2e |
| V-7 | Account update field edits: status Y/N; credit limits & balances signed 9V2; names alpha; address line 1 mandatory; US phone (area code table); SSN part1 not 000/666/900–999; state code table; FICO 300–850; state+zip prefix combo | `COACTUPC.cbl` 1429–1678, 1856–2558; lookup tables `cpy/CSLKPCDY.cpy` 30, 1013, 1073 | unit (table-driven) |
| V-8 | Card update: name alpha+spaces only; status Y/N; expiry month 1–12; expiry year valid | `COCRDUPC.cbl` 182–200 | unit |
| V-9 | User add/update: first, last, id, password, type all mandatory; duplicate id rejected | `COUSR01C.cbl` 120–144, 263; `COUSR02C.cbl` 182–206 | unit + e2e |
| V-10 | Bill pay confirm accepts Y/y/N/n only; blank = show balance first | `COBIL00C.cbl` 173–191 | unit + e2e |
| V-11 | Posting rejects 100/101/102/103 with exact reason text | `CBTRN02C.cbl` 380–421 | **parity test**: run legacy sample `data/ASCII/dailytran.txt` (300 rows) through target and diff resulting TRANSACT/DALYREJS/ACCT/TCATBAL against expected output |
| V-12 | Authorization: RBAC via user type `A`/`U` carried in session (COMMAREA) | `cpy/COCOM01Y.cpy` 26–28; `COMEN01C.cbl` 136–142 | e2e |
| V-13 | Paging boundaries: "already at top/bottom of page" | `COTRN00C.cbl` 248, 270; `COUSR00C.cbl` 251, 273 | e2e |

Legacy behaviours to record as **decision points (preserve vs fix)**, not silently change:
- D-1 Passwords stored/compared in plaintext (`COSGN00C.cbl` 223). Recommend: hash in target; keep seed accounts ADMIN001/USER0001.
- D-2 TRAN-ID generation is "read last key + 1" (race-prone under concurrency) (`COBIL00C.cbl` 212–217). Recommend: DB sequence, same 16-digit zero-padded format.
- D-3 Bill payment records amount as a positive debit-type transaction and *subtracts* it from balance, whereas posting *adds* amounts; sign conventions differ between modules. Preserve, document.
- D-4 Interest job resets cycle credit/debit but is not idempotent if rerun. Preserve semantics, add run-guard in target.
- D-5 Overlimit check uses cycle credit − cycle debit, not current balance (`CBTRN02C.cbl` 403–407). Preserve exactly.
- D-6 `CBTRN01C` vs `CBTRN02C`: confirm CBTRN01C is dead code before dropping.

## 5. Target architecture (options — decision needed)

| | Option A (recommended) | Option B | Option C |
|---|---|---|---|
| Language | Java 21 / Spring Boot 3 | TypeScript / NestJS + Next.js | Python 3.12 / FastAPI + React |
| DB | PostgreSQL 16 (NUMERIC for COMP-3 money) | PostgreSQL 16 | PostgreSQL 16 |
| Batch | Spring Batch jobs (CLI-triggered) | Node CLI + BullMQ | CLI scripts + APScheduler |
| UI | React SPA (one route per BMS map) | Next.js | React |
| Tests | JUnit 5 + Testcontainers + Playwright | Vitest + Playwright | pytest + Playwright |

Rationale for A: strong decimal arithmetic, mature batch framework, common target for COBOL modernization. Happy to build the pilot on B or C if that matches your team.

Legacy → target mapping conventions (same for all options):
- Copybook `01` record → DB table + typed entity; `PIC S9(n)V99` → `NUMERIC(n+2,2)`; `PIC X(10)` dates → `DATE`; `X(26)` timestamps → `TIMESTAMP`.
- CICS program → REST controller + service class; BMS map → one UI page/form; COMMAREA → server session/JWT claims (user id, role, selected acct/card).
- VSAM AIX → secondary index; STARTBR/READNEXT paging → keyset pagination.
- Batch program → job class with the same paragraph structure (validate → post → write reject) so parity is auditable.
- Error messages kept verbatim so screen-level parity tests can string-match.
- JES-submitted report → async job endpoint + job table.

## 6. Migration units, patterns, and pilot

| Pattern | Units | Approx LOC | Share |
|---|---|---|---|
| P1 Online CRUD screen (view/add/update/delete) | COSGN00C, COACTVWC, COACTUPC, COCRDSLC, COCRDUPC, COTRN01C, COTRN02C, COBIL00C, COUSR01C, COUSR02C, COUSR03C | ~10.6k | 54% |
| P2 Online paged list | COCRDLIC, COTRN00C, COUSR00C | ~2.9k | 15% |
| P3 Menu/navigation | COMEN01C, COADM01C | ~0.6k | 3% |
| P4 Batch file-processing job | CBTRN02C, CBACT04C, CBTRN03C, CBSTM03A/B, CBEXPORT, CBIMPORT | ~4.3k | 22% |
| P5 Async report submission | CORPT00C | ~0.6k | 3% |
| Bespoke / drop | CBTRN01C (dead?), CBACT01–03C, CBCUS01C (dump utilities), COBSWAIT/MVSWAIT, COBDATFT, all IDCAMS JCL | ~1.1k | 3% |
| Phase 2 (IMS/DB2/MQ) | 13 programs | ~9.5k | — |

**Recommended pilot: Bill Payment (CB00 / `COBIL00C.cbl`, 572 LOC)**
- Small (one screen, ~10 rules) but forces the full vertical slice: auth/session, account read, XREF lookup by account (AIX), transaction id generation, transaction write, balance update, confirm flow — i.e. everything a P1 screen needs.
- Its output (a TRANSACT row + updated ACCTDAT balance) is deterministic and parity-checkable against sample data.
- Prerequisite scaffold included in pilot: DB schema for ACCTDAT/CCXREF/TRANSACT/USRSEC, seed loader for `data/ASCII/*.txt`, sign-on (COSGN00C is needed to reach CB00; ~10 rules, included).

Alternative: `CBTRN02C` posting job (richest business rules, no UI, 300-row parity dataset available). Good second unit and P4 template.

## 7. Migration order and effort (rough, in Devin sessions)

| Step | Units | Est. |
|---|---|---|
| 0 | Scaffold + schema + seed loader + sign-on | 0.5 session |
| 1 | Pilot: Bill payment (P1) e2e + tests | 0.5–1 session |
| 2 | Posting batch CBTRN02C (P4 template) + parity harness | 0.5–1 |
| 3 | Menus (P3), account view, card detail, txn detail | 0.5 |
| 4 | Paged lists ×3 (P2) | 0.5–1 |
| 5 | Transaction add, user add/update/delete | 1 |
| 6 | Account update (COACTUPC, 4.2k LOC, ~20 validators) | 1–2 |
| 7 | Card update, interest calc, report request + TRANREPT, statement | 1–2 |
| 8 | Export/import, cleanup of utilities | 0.5 |
| — | Phase 2 optional modules | 2–3 (needs MQ/IMS decisions) |

Total base app: ~7–10 sessions after pilot acceptance; steps 3–8 parallelisable as child sessions once the pattern playbooks exist.

## 8. Testing strategy

- **Lint/typecheck**: per target-stack defaults (e.g. Spotless + Error Prone, or ESLint/tsc).
- **Unit**: one test class per catalogue rule (BL-*/V-*), table-driven for validators (V-7 tables from `CSLKPCDY.cpy`).
- **Integration**: DB via Testcontainers; seed from `data/ASCII/*.txt`.
- **E2E**: Playwright per screen, using the sample logins (ADMIN001 / USER0001, README).
- **Behavioural parity**: golden-file tests — run the legacy sample inputs through the target and compare against expected outputs derived from the COBOL logic (first for posting BL-5/6 and bill pay BL-3). A GnuCOBOL (`cobc`, referenced in `scripts/local_compile.sh`) build of the batch programs could generate true legacy goldens locally if desired.

## 9. Open questions / risks

1. **Target stack** — A, B, or C (or something else)?
2. Confirm pilot = Bill Payment (vs posting batch).
3. Is phase 2 (IMS/DB2/MQ modules) in scope at all, and if so what replaces MQ/IMS?
4. Decision points D-1..D-6 above (preserve vs fix).
5. Statement job uses z/OS control blocks (TIOT) — will need a clean-room rewrite, not a translation.
6. Report submission via JES internal reader has no direct analogue; proposed async job table.
7. Is CBTRN01C dead code?
8. Any existing consumers of the VSAM files/data formats outside CardDemo that constrain the schema?

## 10. Pilot status

Approved 2026-09-15: target stack A (Java 21 / Spring Boot / PostgreSQL), pilot = Bill Payment.
Implementation: [`carddemo-java/`](carddemo-java/). Additional decision point recorded during
implementation: D-7 — `COBIL00C` moves the raw ACTIDIN text into `PIC 9(11)` with no numeric check
(line 170); the target rejects non-numeric ids with "Acct ID must be Numeric..." (message borrowed
from `COTRN02C.cbl` 211).
