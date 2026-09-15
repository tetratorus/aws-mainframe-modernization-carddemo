package com.carddemo.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.seed.LegacyDataLoader;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import java.io.IOException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Browser flow CC00 sign-on -> CB00 bill payment. Run with {@code mvn verify -Pe2e}. */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "carddemo.legacy-data-dir=../../app/data/ASCII")
class BillPayE2ETest {

  @LocalServerPort int port;

  private static Playwright playwright;
  private static Browser browser;

  @BeforeAll
  static void start(@Autowired Flyway flyway, @Autowired LegacyDataLoader loader)
      throws IOException {
    flyway.clean();
    flyway.migrate();
    loader.run(new DefaultApplicationArguments());
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  @AfterAll
  static void stop() {
    browser.close();
    playwright.close();
  }

  @Test
  void signonAndPayBill() {
    Page page = browser.newPage();
    page.navigate("http://localhost:" + port + "/");

    // Sign-on validation messages (COSGN00C)
    page.click("#signon-enter");
    PlaywrightAssertions.assertThat(page.locator("#message")).hasText("Please enter User ID ...");
    page.fill("#userId", "user0001");
    page.fill("#password", "wrong");
    page.click("#signon-enter");
    PlaywrightAssertions.assertThat(page.locator("#message"))
        .hasText("Wrong Password. Try again ...");
    page.fill("#password", "password");
    page.click("#signon-enter");
    PlaywrightAssertions.assertThat(page.locator("#billpay-screen")).isVisible();
    PlaywrightAssertions.assertThat(page.locator("#tran-id")).hasText("CB00");

    // Bill payment (COBIL00C): account 2 has balance 158.00 in acctdata.txt
    page.click("#billpay-enter");
    PlaywrightAssertions.assertThat(page.locator("#message"))
        .hasText("Acct ID can NOT be empty...");
    page.fill("#accountId", "2");
    page.click("#billpay-enter");
    PlaywrightAssertions.assertThat(page.locator("#currentBalance")).hasText("158.00");
    PlaywrightAssertions.assertThat(page.locator("#message"))
        .hasText("Confirm to make a bill payment...");
    page.fill("#confirm", "x");
    page.click("#billpay-enter");
    PlaywrightAssertions.assertThat(page.locator("#message"))
        .hasText("Invalid value. Valid values are (Y/N)...");
    page.fill("#confirm", "Y");
    page.click("#billpay-enter");
    PlaywrightAssertions.assertThat(page.locator("#message"))
        .containsText("Payment successful. Your Transaction ID is 0000000996722788.");
    PlaywrightAssertions.assertThat(page.locator("#currentBalance")).hasText("0.00");

    page.fill("#accountId", "2");
    page.fill("#confirm", "Y");
    page.click("#billpay-enter");
    PlaywrightAssertions.assertThat(page.locator("#message")).hasText("You have nothing to pay...");

    // F3 exits to sign-on
    page.click("#billpay-exit");
    PlaywrightAssertions.assertThat(page.locator("#signon-screen")).isVisible();
    assertThat(page.locator("#message").textContent())
        .isEqualTo("Thank you for using CardDemo application...");
    page.close();
  }
}
