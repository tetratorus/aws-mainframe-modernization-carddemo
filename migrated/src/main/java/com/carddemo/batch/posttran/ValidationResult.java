package com.carddemo.batch.posttran;

import com.carddemo.records.AccountRecord;
import com.carddemo.records.CardXrefRecord;
import java.util.Optional;

/**
 * Outcome of 1500-VALIDATE-TRAN ({@code CBTRN02C.cbl:370-378}). {@code failReason == 0} means the
 * transaction is valid; otherwise the reason/description pair forms the 80-byte validation trailer
 * of the reject record. The looked-up cross-reference and account are carried forward so that
 * posting can reuse them exactly as the COBOL program reuses the working-storage copies.
 */
public record ValidationResult(
    int failReason,
    String failReasonDesc,
    Optional<CardXrefRecord> xref,
    Optional<AccountRecord> account) {

  public static final int REASON_OK = 0;
  public static final int REASON_INVALID_CARD = 100;
  public static final int REASON_ACCOUNT_NOT_FOUND = 101;
  public static final int REASON_OVERLIMIT = 102;
  public static final int REASON_AFTER_EXPIRATION = 103;

  public static final String DESC_INVALID_CARD = "INVALID CARD NUMBER FOUND";
  public static final String DESC_ACCOUNT_NOT_FOUND = "ACCOUNT RECORD NOT FOUND";
  public static final String DESC_OVERLIMIT = "OVERLIMIT TRANSACTION";
  public static final String DESC_AFTER_EXPIRATION = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";

  public boolean isValid() {
    return failReason == REASON_OK;
  }
}
