package com.carddemo.store;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite replacement for the CardDemo VSAM KSDS clusters. One table per copybook; the KSDS record
 * key becomes the primary key so that COBOL {@code READ ... INVALID KEY} / {@code WRITE} / {@code
 * REWRITE} semantics map onto primary-key lookups.
 */
public final class CardDemoDatabase implements AutoCloseable {

  private static final String[] SCHEMA = {
    """
    CREATE TABLE IF NOT EXISTS account (
      acct_id            INTEGER PRIMARY KEY,
      active_status      TEXT    NOT NULL,
      curr_bal           TEXT    NOT NULL,
      credit_limit       TEXT    NOT NULL,
      cash_credit_limit  TEXT    NOT NULL,
      open_date          TEXT    NOT NULL,
      expiration_date    TEXT    NOT NULL,
      reissue_date       TEXT    NOT NULL,
      curr_cyc_credit    TEXT    NOT NULL,
      curr_cyc_debit     TEXT    NOT NULL,
      addr_zip           TEXT    NOT NULL,
      group_id           TEXT    NOT NULL,
      filler             TEXT    NOT NULL
    )""",
    """
    CREATE TABLE IF NOT EXISTS card_xref (
      card_num  TEXT    PRIMARY KEY,
      cust_id   INTEGER NOT NULL,
      acct_id   INTEGER NOT NULL,
      filler    TEXT    NOT NULL
    )""",
    """
    CREATE TABLE IF NOT EXISTS tran_cat_bal (
      acct_id  INTEGER NOT NULL,
      type_cd  TEXT    NOT NULL,
      cat_cd   INTEGER NOT NULL,
      balance  TEXT    NOT NULL,
      filler   TEXT    NOT NULL,
      PRIMARY KEY (acct_id, type_cd, cat_cd)
    )""",
    """
    CREATE TABLE IF NOT EXISTS transaction_ (
      seq            INTEGER PRIMARY KEY AUTOINCREMENT,
      tran_id        TEXT    NOT NULL UNIQUE,
      type_cd        TEXT    NOT NULL,
      cat_cd         INTEGER NOT NULL,
      source         TEXT    NOT NULL,
      description    TEXT    NOT NULL,
      amount         TEXT    NOT NULL,
      merchant_id    INTEGER NOT NULL,
      merchant_name  TEXT    NOT NULL,
      merchant_city  TEXT    NOT NULL,
      merchant_zip   TEXT    NOT NULL,
      card_num       TEXT    NOT NULL,
      orig_ts        TEXT    NOT NULL,
      proc_ts        TEXT    NOT NULL,
      filler         TEXT    NOT NULL
    )"""
  };

  private final Connection connection;

  private CardDemoDatabase(Connection connection) {
    this.connection = connection;
  }

  /** Opens (creating if necessary) the database file and ensures the schema exists. */
  public static CardDemoDatabase open(Path file) throws SQLException {
    return open("jdbc:sqlite:" + file.toAbsolutePath());
  }

  /** Opens an in-memory database; useful for tests. */
  public static CardDemoDatabase inMemory() throws SQLException {
    return open("jdbc:sqlite::memory:");
  }

  private static CardDemoDatabase open(String url) throws SQLException {
    Connection c = DriverManager.getConnection(url);
    c.setAutoCommit(false);
    try (Statement st = c.createStatement()) {
      for (String ddl : SCHEMA) {
        st.execute(ddl);
      }
    }
    c.commit();
    return new CardDemoDatabase(c);
  }

  public Connection connection() {
    return connection;
  }

  public AccountRepository accounts() {
    return new AccountRepository(connection);
  }

  public CardXrefRepository cardXrefs() {
    return new CardXrefRepository(connection);
  }

  public TranCatBalRepository tranCatBals() {
    return new TranCatBalRepository(connection);
  }

  public TransactionRepository transactions() {
    return new TransactionRepository(connection);
  }

  public void commit() throws SQLException {
    connection.commit();
  }

  public void rollback() throws SQLException {
    connection.rollback();
  }

  @Override
  public void close() throws SQLException {
    connection.close();
  }
}
