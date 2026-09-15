package com.carddemo.billpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.account.AccountRepository;
import com.carddemo.seed.LegacyDataLoader;
import com.carddemo.transaction.Transaction;
import com.carddemo.transaction.TransactionRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Behavioural parity against the legacy sample data (app/data/ASCII): account 1 carries a balance
 * of 194.00 (acctdata.txt row 1, field ACCT-CURR-BAL = 00000001940{) and the highest loaded TRAN-ID
 * comes from dailytran.txt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "carddemo.legacy-data-dir=../../app/data/ASCII")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillPayIntegrationTest {

  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;
  @Autowired AccountRepository accounts;
  @Autowired TransactionRepository transactions;

  @BeforeAll
  static void resetDatabase(@Autowired Flyway flyway, @Autowired LegacyDataLoader loader)
      throws IOException {
    flyway.clean();
    flyway.migrate();
    loader.run(new DefaultApplicationArguments());
  }

  private HttpHeaders signon(String user) {
    ResponseEntity<Map<String, Object>> res =
        rest.exchange(
            "/api/session",
            org.springframework.http.HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", user, "password", "password")),
            new org.springframework.core.ParameterizedTypeReference<>() {});
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, res.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    return headers;
  }

  private ResponseEntity<Map<String, Object>> billPay(
      HttpHeaders headers, String acct, String confirm) {
    Map<String, String> body = new java.util.HashMap<>();
    body.put("accountId", acct);
    body.put("confirm", confirm);
    return rest.exchange(
        "/api/bill-payments",
        org.springframework.http.HttpMethod.POST,
        new HttpEntity<>(body, headers),
        new org.springframework.core.ParameterizedTypeReference<>() {});
  }

  @Test
  @Order(1)
  void legacySampleDataIsLoaded() {
    assertThat(accounts.count()).isEqualTo(50);
    assertThat(transactions.count()).isEqualTo(300);
    assertThat(accounts.findById(1L).orElseThrow().getCurrBal()).isEqualByComparingTo("194.00");
    // Highest key in dailytran.txt, i.e. what READPREV from HIGH-VALUES returns.
    assertThat(transactions.findFirstByOrderByTranIdDesc().orElseThrow().getTranId())
        .isEqualTo("0000000996722787");
  }

  @Test
  @Order(2)
  void requiresSession() {
    ResponseEntity<Map<String, Object>> res = billPay(new HttpHeaders(), "1", "Y");
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @Order(3)
  void showsBalanceThenPaysAndRejectsSecondPayment() {
    HttpHeaders session = signon("user0001");

    ResponseEntity<Map<String, Object>> shown = billPay(session, "1", "");
    assertThat(shown.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(new BigDecimal(shown.getBody().get("currentBalance").toString()))
        .isEqualByComparingTo("194.00");
    assertThat(shown.getBody().get("message")).isEqualTo("Confirm to make a bill payment...");

    ResponseEntity<Map<String, Object>> paid = billPay(session, "1", "Y");
    assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(paid.getBody().get("paid")).isEqualTo(true);
    assertThat(paid.getBody().get("tranId")).isEqualTo("0000000996722788");

    Transaction t = transactions.findById("0000000996722788").orElseThrow();
    assertThat(t.getAmount()).isEqualByComparingTo("194.00");
    assertThat(t.getTypeCd()).isEqualTo("02");
    assertThat(t.getCatCd()).isEqualTo(2);
    // First XREF record for account 1 on the alternate index.
    assertThat(t.getCardNum()).isEqualTo("9680294154603697");
    assertThat(accounts.findById(1L).orElseThrow().getCurrBal()).isEqualByComparingTo("0.00");

    ResponseEntity<Map<String, Object>> again = billPay(session, "1", "Y");
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(again.getBody().get("message")).isEqualTo("You have nothing to pay...");
  }

  @Test
  @Order(4)
  void unknownAccountAndBadConfirm() {
    HttpHeaders session = signon("ADMIN001");
    assertThat(billPay(session, "99999999999", "").getBody().get("message"))
        .isEqualTo("Account ID NOT found...");
    assertThat(billPay(session, "2", "Q").getBody().get("message"))
        .isEqualTo("Invalid value. Valid values are (Y/N)...");
    assertThat(billPay(session, "", "").getBody().get("message"))
        .isEqualTo("Acct ID can NOT be empty...");
  }

  @Test
  @Order(5)
  void concurrentPaymentsOnDifferentAccountsGetDistinctTranIds() throws Exception {
    HttpHeaders session = signon("ADMIN001");
    long before = transactions.count();
    List<String> accts =
        accounts.findAll().stream()
            .filter(a -> a.getCurrBal().signum() > 0)
            .limit(8)
            .map(a -> Long.toString(a.getAcctId()))
            .toList();
    assertThat(accts).hasSizeGreaterThanOrEqualTo(4);

    ExecutorService pool = Executors.newFixedThreadPool(accts.size());
    try {
      List<Future<ResponseEntity<Map<String, Object>>>> results =
          pool.invokeAll(
              accts.stream()
                  .map(
                      a ->
                          (Callable<ResponseEntity<Map<String, Object>>>)
                              () -> billPay(session, a, "Y"))
                  .toList());
      Set<Object> ids = new HashSet<>();
      for (Future<ResponseEntity<Map<String, Object>>> f : results) {
        ResponseEntity<Map<String, Object>> res = f.get();
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        ids.add(res.getBody().get("tranId"));
      }
      assertThat(ids).hasSize(accts.size());
    } finally {
      pool.shutdownNow();
    }
    assertThat(transactions.count()).isEqualTo(before + accts.size());
  }
}
