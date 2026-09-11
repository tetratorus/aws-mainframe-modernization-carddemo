package com.carddemo.store;

import com.carddemo.records.AccountRecord;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Table {@code account} — replacement for KSDS ACCTDATA (key ACCT-ID). */
public final class AccountRepository {

  private static final String COLUMNS =
      "acct_id, active_status, curr_bal, credit_limit, cash_credit_limit, open_date,"
          + " expiration_date, reissue_date, curr_cyc_credit, curr_cyc_debit, addr_zip, group_id,"
          + " filler";

  private final Connection c;

  AccountRepository(Connection c) {
    this.c = c;
  }

  /** {@code READ ACCOUNT-FILE} by key; empty = INVALID KEY. */
  public Optional<AccountRecord> find(long acctId) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement("SELECT " + COLUMNS + " FROM account WHERE acct_id = ?")) {
      ps.setLong(1, acctId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(map(rs)) : Optional.empty();
      }
    }
  }

  /** {@code WRITE}: fails on duplicate key. */
  public void insert(AccountRecord a) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO account (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
      bind(ps, a);
      ps.executeUpdate();
    }
  }

  /** {@code REWRITE}: returns false when no record with that key exists (INVALID KEY). */
  public boolean rewrite(AccountRecord a) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "UPDATE account SET active_status=?, curr_bal=?, credit_limit=?,"
                + " cash_credit_limit=?, open_date=?, expiration_date=?, reissue_date=?,"
                + " curr_cyc_credit=?, curr_cyc_debit=?, addr_zip=?, group_id=?, filler=?"
                + " WHERE acct_id=?")) {
      int i = 1;
      ps.setString(i++, a.activeStatus());
      ps.setString(i++, a.currBal().toPlainString());
      ps.setString(i++, a.creditLimit().toPlainString());
      ps.setString(i++, a.cashCreditLimit().toPlainString());
      ps.setString(i++, a.openDate());
      ps.setString(i++, a.expirationDate());
      ps.setString(i++, a.reissueDate());
      ps.setString(i++, a.currCycCredit().toPlainString());
      ps.setString(i++, a.currCycDebit().toPlainString());
      ps.setString(i++, a.addrZip());
      ps.setString(i++, a.groupId());
      ps.setString(i++, a.filler());
      ps.setLong(i, a.acctId());
      return ps.executeUpdate() == 1;
    }
  }

  /** All records in key order (sequential read of the KSDS). */
  public List<AccountRecord> all() throws SQLException {
    List<AccountRecord> out = new ArrayList<>();
    try (PreparedStatement ps =
            c.prepareStatement("SELECT " + COLUMNS + " FROM account ORDER BY acct_id");
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        out.add(map(rs));
      }
    }
    return out;
  }

  public void deleteAll() throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("DELETE FROM account")) {
      ps.executeUpdate();
    }
  }

  private static void bind(PreparedStatement ps, AccountRecord a) throws SQLException {
    int i = 1;
    ps.setLong(i++, a.acctId());
    ps.setString(i++, a.activeStatus());
    ps.setString(i++, a.currBal().toPlainString());
    ps.setString(i++, a.creditLimit().toPlainString());
    ps.setString(i++, a.cashCreditLimit().toPlainString());
    ps.setString(i++, a.openDate());
    ps.setString(i++, a.expirationDate());
    ps.setString(i++, a.reissueDate());
    ps.setString(i++, a.currCycCredit().toPlainString());
    ps.setString(i++, a.currCycDebit().toPlainString());
    ps.setString(i++, a.addrZip());
    ps.setString(i++, a.groupId());
    ps.setString(i, a.filler());
  }

  private static AccountRecord map(ResultSet rs) throws SQLException {
    return new AccountRecord(
        rs.getLong("acct_id"),
        rs.getString("active_status"),
        new BigDecimal(rs.getString("curr_bal")),
        new BigDecimal(rs.getString("credit_limit")),
        new BigDecimal(rs.getString("cash_credit_limit")),
        rs.getString("open_date"),
        rs.getString("expiration_date"),
        rs.getString("reissue_date"),
        new BigDecimal(rs.getString("curr_cyc_credit")),
        new BigDecimal(rs.getString("curr_cyc_debit")),
        rs.getString("addr_zip"),
        rs.getString("group_id"),
        rs.getString("filler"));
  }
}
