-- Legacy VSAM record layouts (app/cpy) mapped to PostgreSQL.
-- PIC S9(n)V99  -> NUMERIC(n+2,2);  PIC X(10) dates -> DATE;  PIC X(26) -> TIMESTAMP(6)

-- app/cpy/CSUSR01Y.cpy (USRSEC)
CREATE TABLE sec_user (
    usr_id        VARCHAR(8)   PRIMARY KEY,
    first_name    VARCHAR(20)  NOT NULL,
    last_name     VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    usr_type      VARCHAR(1)   NOT NULL CHECK (usr_type IN ('A', 'U'))
);

-- app/cpy/CVACT01Y.cpy (ACCTDAT)
CREATE TABLE account (
    acct_id            BIGINT        PRIMARY KEY,
    active_status      VARCHAR(1)    NOT NULL,
    curr_bal           NUMERIC(12,2) NOT NULL,
    credit_limit       NUMERIC(12,2) NOT NULL,
    cash_credit_limit  NUMERIC(12,2) NOT NULL,
    open_date          DATE,
    expiration_date    DATE,
    reissue_date       DATE,
    curr_cyc_credit    NUMERIC(12,2) NOT NULL,
    curr_cyc_debit     NUMERIC(12,2) NOT NULL,
    addr_zip           VARCHAR(10),
    group_id           VARCHAR(10),
    version            BIGINT        NOT NULL DEFAULT 0
);

-- app/cpy/CVACT03Y.cpy (CCXREF, alternate index CXACAIX on acct_id)
CREATE TABLE card_xref (
    card_num  VARCHAR(16) PRIMARY KEY,
    cust_id   BIGINT      NOT NULL,
    acct_id   BIGINT      NOT NULL
);
CREATE INDEX card_xref_acct_idx ON card_xref (acct_id);

-- app/cpy/CVTRA05Y.cpy (TRANSACT)
CREATE TABLE transaction (
    tran_id        VARCHAR(16)   PRIMARY KEY,
    type_cd        VARCHAR(2)    NOT NULL,
    cat_cd         INTEGER       NOT NULL,
    source         VARCHAR(10)   NOT NULL,
    description    VARCHAR(100)  NOT NULL,
    amount         NUMERIC(11,2) NOT NULL,
    merchant_id    BIGINT        NOT NULL,
    merchant_name  VARCHAR(50),
    merchant_city  VARCHAR(50),
    merchant_zip   VARCHAR(10),
    card_num       VARCHAR(16)   NOT NULL,
    orig_ts        TIMESTAMP(6),
    proc_ts        TIMESTAMP(6)
);
