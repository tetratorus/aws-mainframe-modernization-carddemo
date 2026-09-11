package com.carddemo.store;

import com.carddemo.records.CardXrefRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Table {@code card_xref} — replacement for KSDS CARDXREF (key XREF-CARD-NUM). */
public final class CardXrefRepository {

  private static final String COLUMNS = "card_num, cust_id, acct_id, filler";

  private final Connection c;

  CardXrefRepository(Connection c) {
    this.c = c;
  }

  /** {@code READ XREF-FILE} by card number; empty = INVALID KEY. */
  public Optional<CardXrefRecord> find(String cardNum) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement("SELECT " + COLUMNS + " FROM card_xref WHERE card_num = ?")) {
      ps.setString(1, cardNum);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(map(rs)) : Optional.empty();
      }
    }
  }

  public void insert(CardXrefRecord x) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement("INSERT INTO card_xref (" + COLUMNS + ") VALUES (?,?,?,?)")) {
      ps.setString(1, x.cardNum());
      ps.setLong(2, x.custId());
      ps.setLong(3, x.acctId());
      ps.setString(4, x.filler());
      ps.executeUpdate();
    }
  }

  public List<CardXrefRecord> all() throws SQLException {
    List<CardXrefRecord> out = new ArrayList<>();
    try (PreparedStatement ps =
            c.prepareStatement("SELECT " + COLUMNS + " FROM card_xref ORDER BY card_num");
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        out.add(map(rs));
      }
    }
    return out;
  }

  public void deleteAll() throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("DELETE FROM card_xref")) {
      ps.executeUpdate();
    }
  }

  private static CardXrefRecord map(ResultSet rs) throws SQLException {
    return new CardXrefRecord(
        rs.getString("card_num"),
        rs.getLong("cust_id"),
        rs.getLong("acct_id"),
        rs.getString("filler"));
  }
}
