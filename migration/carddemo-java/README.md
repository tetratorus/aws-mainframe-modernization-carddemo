# carddemo-java — pilot migration (Bill Payment)

Java 21 / Spring Boot 3.5 / PostgreSQL port of the CardDemo pilot module chosen in
[`../migration_plan.md`](../migration_plan.md):

| Legacy | Target |
|---|---|
| `CC00` / `app/cbl/COSGN00C.cbl` (sign-on) | `POST /api/session`, `auth/SignonService` |
| `CB00` / `app/cbl/COBIL00C.cbl` (bill payment) | `POST /api/bill-payments`, `billpay/BillPayService` |
| `COSGN00` / `COBIL00` BMS maps | `static/index.html` + `app.js` |
| `CVACT01Y`, `CVACT03Y`, `CVTRA05Y`, `CSUSR01Y` copybooks | `db/migration/V1__pilot_schema.sql` + JPA entities |
| IDCAMS REPRO load jobs | `seed/LegacyDataLoader` (reads `app/data/ASCII/*.txt` into empty tables) |

Legacy screen messages are kept verbatim; every rule in the service classes cites its COBOL line range.

## Run

```bash
createdb carddemo   # user/password default to carddemo/carddemo, see application.yml
mvn spring-boot:run
open http://localhost:8080   # ADMIN001 / PASSWORD  or  USER0001 / PASSWORD
```

## Verify

```bash
mvn verify            # compile with -Xlint:all -Werror, unit + integration tests, spotless check
mvn verify -Pe2e      # additionally runs the Playwright browser test (BillPayE2ETest)
mvn spotless:apply    # format
```

Integration/e2e tests use database `carddemo_test` (override with `CARDDEMO_TEST_DB_URL`) and reset
it with Flyway before loading the legacy sample data, so parity assertions (account 1 balance
194.00, next TRAN-ID 0000000996722788, ...) are deterministic.

## Deliberate deviations from the COBOL (see plan §4, D-1/D-2/D-7)

- Passwords are stored bcrypt-hashed instead of clear text (`SEC-USR-PWD`).
- `TRAN-ID` is still "highest existing + 1", but allocated inside a SERIALIZABLE transaction that
  also holds a row lock on the account, so concurrent payments cannot collide.
- A non-numeric account id is rejected with `Acct ID must be Numeric...`; the COBOL moved the raw
  text into `PIC 9(11)` without a check.
