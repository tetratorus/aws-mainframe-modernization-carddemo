package com.carddemo.billpay;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Transaction CB00 (screen COBIL00). One POST models one ENTER on the legacy screen. */
@RestController
@RequestMapping("/api/bill-payments")
public class BillPayController {

  private final BillPayService billPayService;

  public BillPayController(BillPayService billPayService) {
    this.billPayService = billPayService;
  }

  @PostMapping
  public BillPayResult process(@RequestBody BillPayRequest request) {
    return billPayService.process(request);
  }
}
