package com.carddemo.billpay;

import java.math.BigDecimal;

/**
 * Screen COBIL0A output: current balance (CURBAL) and the informational message (ERRMSG).
 *
 * @param paid true when a payment transaction was written
 * @param tranId the new TRAN-ID when paid, else null
 * @param currentBalance ACCT-CURR-BAL after the operation
 */
public record BillPayResult(
    long accountId, BigDecimal currentBalance, boolean paid, String tranId, String message) {}
