package com.carddemo.store;

import com.carddemo.records.TransactionRecord;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Table {@code transaction_} — replacement for KSDS TRANSACT (key TRAN-ID). */
public final class TransactionRepository {

  private static final String COLUMNS =
      "tran_id, type_cd, cat_cd, source, description, amount, merchant_id, merchant_name,"
          + " merchant_city, merchant_zip, card_num, orig_ts, proc_ts, filler";

  private final Connection c;

  TransactionRepository(Connection c) {
    this.c = c;
  }

  /** {@code WRITE}: fails on duplicate TRAN-ID. */
  public void insert(TransactionRecord t) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO transaction_ (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
      int i = 1;
      ps.setString(i++, t.tranId());
      ps.setString(i++, t.typeCd());
      ps.setInt(i++, t.catCd());
      ps.setString(i++, t.source());
      ps.setString(i++, t.description());
      ps.setString(i++, t.amount().toPlainString());
      ps.setLong(i++, t.merchantId());
      ps.setString(i++, t.merchantName());
      ps.setString(i++, t.merchantCity());
      ps.setString(i++, t.merchantZip());
      ps.setString(i++, t.cardNum());
      ps.setString(i++, t.origTs());
      ps.setString(i++, t.procTs());
      ps.setString(i, t.filler());
      ps.executeUpdate();
    }
  }

  /** All records in TRAN-ID (KSDS key) order. */
  public List<TransactionRecord> all() throws SQLException {
    List<TransactionRecord> out = new ArrayList<>();
    try (PreparedStatement ps =
            c.prepareStatement("SELECT " + COLUMNS + " FROM transaction_ ORDER BY tran_id");
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        out.add(map(rs));
      }
    }
    return out;
  }

  public void deleteAll() throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("DELETE FROM transaction_")) {
      ps.executeUpdate();
    }
  }

  private static TransactionRecord map(ResultSet rs) throws SQLException {
    return new TransactionRecord(
        rs.getString("tran_id"),
        rs.getString("type_cd"),
        rs.getInt("cat_cd"),
        rs.getString("source"),
        rs.getString("description"),
        new BigDecimal(rs.getString("amount")),
        rs.getLong("merchant_id"),
        rs.getString("merchant_name"),
        rs.getString("merchant_city"),
        rs.getString("merchant_zip"),
        rs.getString("card_num"),
        rs.getString("orig_ts"),
        rs.getString("proc_ts"),
        rs.getString("filler"));
  }
}
