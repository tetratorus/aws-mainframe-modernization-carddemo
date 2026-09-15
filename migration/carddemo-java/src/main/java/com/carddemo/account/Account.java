package com.carddemo.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;

/** ACCOUNT-RECORD, app/cpy/CVACT01Y.cpy. */
@Entity
@Table(name = "account")
public class Account {

  @Id
  @Column(name = "acct_id")
  private long acctId;

  @Column(name = "active_status", nullable = false)
  private String activeStatus;

  @Column(name = "curr_bal", nullable = false)
  private BigDecimal currBal;

  @Column(name = "credit_limit", nullable = false)
  private BigDecimal creditLimit;

  @Column(name = "cash_credit_limit", nullable = false)
  private BigDecimal cashCreditLimit;

  @Column(name = "open_date")
  private LocalDate openDate;

  @Column(name = "expiration_date")
  private LocalDate expirationDate;

  @Column(name = "reissue_date")
  private LocalDate reissueDate;

  @Column(name = "curr_cyc_credit", nullable = false)
  private BigDecimal currCycCredit;

  @Column(name = "curr_cyc_debit", nullable = false)
  private BigDecimal currCycDebit;

  @Column(name = "addr_zip")
  private String addrZip;

  @Column(name = "group_id")
  private String groupId;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected Account() {}

  public Account(
      long acctId,
      String activeStatus,
      BigDecimal currBal,
      BigDecimal creditLimit,
      BigDecimal cashCreditLimit,
      LocalDate openDate,
      LocalDate expirationDate,
      LocalDate reissueDate,
      BigDecimal currCycCredit,
      BigDecimal currCycDebit,
      String addrZip,
      String groupId) {
    this.acctId = acctId;
    this.activeStatus = activeStatus;
    this.currBal = currBal;
    this.creditLimit = creditLimit;
    this.cashCreditLimit = cashCreditLimit;
    this.openDate = openDate;
    this.expirationDate = expirationDate;
    this.reissueDate = reissueDate;
    this.currCycCredit = currCycCredit;
    this.currCycDebit = currCycDebit;
    this.addrZip = addrZip;
    this.groupId = groupId;
  }

  public long getAcctId() {
    return acctId;
  }

  public String getActiveStatus() {
    return activeStatus;
  }

  public BigDecimal getCurrBal() {
    return currBal;
  }

  public void setCurrBal(BigDecimal currBal) {
    this.currBal = currBal;
  }

  public BigDecimal getCreditLimit() {
    return creditLimit;
  }

  public BigDecimal getCashCreditLimit() {
    return cashCreditLimit;
  }

  public LocalDate getOpenDate() {
    return openDate;
  }

  public LocalDate getExpirationDate() {
    return expirationDate;
  }

  public LocalDate getReissueDate() {
    return reissueDate;
  }

  public BigDecimal getCurrCycCredit() {
    return currCycCredit;
  }

  public BigDecimal getCurrCycDebit() {
    return currCycDebit;
  }

  public String getAddrZip() {
    return addrZip;
  }

  public String getGroupId() {
    return groupId;
  }

  public long getVersion() {
    return version;
  }
}
