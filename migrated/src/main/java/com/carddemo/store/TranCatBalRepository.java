package com.carddemo.store;

import com.carddemo.records.TranCatBalRecord;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Table {@code tran_cat_bal} — replacement for KSDS TCATBALF (key acct id + type + category). */
public final class TranCatBalRepository {

  private static final String COLUMNS = "acct_id, type_cd, cat_cd, balance, filler";

  private final Connection c;

  TranCatBalRepository(Connection c) {
    this.c = c;
  }

  public Optional<TranCatBalRecord> find(long acctId, String typeCd, int catCd)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT "
                + COLUMNS
                + " FROM tran_cat_bal WHERE acct_id = ? AND type_cd = ? AND cat_cd = ?")) {
      ps.setLong(1, acctId);
      ps.setString(2, typeCd);
      ps.setInt(3, catCd);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(map(rs)) : Optional.empty();
      }
    }
  }

  /** {@code WRITE}: fails on duplicate key. */
  public void insert(TranCatBalRecord t) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement("INSERT INTO tran_cat_bal (" + COLUMNS + ") VALUES (?,?,?,?,?)")) {
      ps.setLong(1, t.acctId());
      ps.setString(2, t.typeCd());
      ps.setInt(3, t.catCd());
      ps.setString(4, t.balance().toPlainString());
      ps.setString(5, t.filler());
      ps.executeUpdate();
    }
  }

  /** {@code REWRITE}: returns false when the key does not exist. */
  public boolean rewrite(TranCatBalRecord t) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "UPDATE tran_cat_bal SET balance = ?, filler = ?"
                + " WHERE acct_id = ? AND type_cd = ? AND cat_cd = ?")) {
      ps.setString(1, t.balance().toPlainString());
      ps.setString(2, t.filler());
      ps.setLong(3, t.acctId());
      ps.setString(4, t.typeCd());
      ps.setInt(5, t.catCd());
      return ps.executeUpdate() == 1;
    }
  }

  public List<TranCatBalRecord> all() throws SQLException {
    List<TranCatBalRecord> out = new ArrayList<>();
    try (PreparedStatement ps =
            c.prepareStatement(
                "SELECT " + COLUMNS + " FROM tran_cat_bal ORDER BY acct_id, type_cd, cat_cd");
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        out.add(map(rs));
      }
    }
    return out;
  }

  public void deleteAll() throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("DELETE FROM tran_cat_bal")) {
      ps.executeUpdate();
    }
  }

  private static TranCatBalRecord map(ResultSet rs) throws SQLException {
    return new TranCatBalRecord(
        rs.getLong("acct_id"),
        rs.getString("type_cd"),
        rs.getInt("cat_cd"),
        new BigDecimal(rs.getString("balance")),
        rs.getString("filler"));
  }
}
