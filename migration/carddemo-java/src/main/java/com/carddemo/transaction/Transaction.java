package com.carddemo.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** TRAN-RECORD, app/cpy/CVTRA05Y.cpy. */
@Entity
@Table(name = "transaction")
public class Transaction {

  @Id
  @Column(name = "tran_id", length = 16)
  private String tranId;

  @Column(name = "type_cd", nullable = false)
  private String typeCd;

  @Column(name = "cat_cd", nullable = false)
  private int catCd;

  @Column(name = "source", nullable = false)
  private String source;

  @Column(name = "description", nullable = false)
  private String description;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Column(name = "merchant_id", nullable = false)
  private long merchantId;

  @Column(name = "merchant_name")
  private String merchantName;

  @Column(name = "merchant_city")
  private String merchantCity;

  @Column(name = "merchant_zip")
  private String merchantZip;

  @Column(name = "card_num", nullable = false)
  private String cardNum;

  @Column(name = "orig_ts")
  private LocalDateTime origTs;

  @Column(name = "proc_ts")
  private LocalDateTime procTs;

  protected Transaction() {}

  public Transaction(
      String tranId,
      String typeCd,
      int catCd,
      String source,
      String description,
      BigDecimal amount,
      long merchantId,
      String merchantName,
      String merchantCity,
      String merchantZip,
      String cardNum,
      LocalDateTime origTs,
      LocalDateTime procTs) {
    this.tranId = tranId;
    this.typeCd = typeCd;
    this.catCd = catCd;
    this.source = source;
    this.description = description;
    this.amount = amount;
    this.merchantId = merchantId;
    this.merchantName = merchantName;
    this.merchantCity = merchantCity;
    this.merchantZip = merchantZip;
    this.cardNum = cardNum;
    this.origTs = origTs;
    this.procTs = procTs;
  }

  public String getTranId() {
    return tranId;
  }

  public String getTypeCd() {
    return typeCd;
  }

  public int getCatCd() {
    return catCd;
  }

  public String getSource() {
    return source;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public long getMerchantId() {
    return merchantId;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public String getMerchantCity() {
    return merchantCity;
  }

  public String getMerchantZip() {
    return merchantZip;
  }

  public String getCardNum() {
    return cardNum;
  }

  public LocalDateTime getOrigTs() {
    return origTs;
  }

  public LocalDateTime getProcTs() {
    return procTs;
  }
}
