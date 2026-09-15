package com.carddemo.billpay;

/**
 * Screen COBIL0A input fields (app/bms/COBIL00.bms): ACTIDIN and CONFIRM. {@code confirm} is the
 * raw one-character field: blank/null = show balance, Y/y = pay, N/n = clear, anything else =
 * error.
 */
public record BillPayRequest(String accountId, String confirm) {}
