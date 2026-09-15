package com.carddemo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CobolFormatsTest {

  @ParameterizedTest
  @CsvSource({
    "00000001940{, 194.00", // acctdata.txt row 1, ACCT-CURR-BAL
    "00000020200{, 2020.00",
    "0000005047G, 504.77", // dailytran.txt row 1 TRAN-AMT (S9(9)V99)
    "00000000012A, 1.21",
    "00000000012J, -1.21",
    "00000000000}, 0.00",
    "000000000300, 3.00"
  })
  void parsesZonedDecimalSigns(String field, String expected) {
    assertThat(CobolFormats.zonedDecimal(field, 2)).isEqualByComparingTo(new BigDecimal(expected));
  }

  @Test
  void rejectsInvalidSignCharacter() {
    assertThatThrownBy(() -> CobolFormats.zonedDecimal("0000X", 2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parsesLegacyTimestampAndBlankFields() {
    assertThat(CobolFormats.timestamp26("2022-06-10 19:27:53.000000"))
        .isEqualTo(LocalDateTime.of(2022, 6, 10, 19, 27, 53));
    assertThat(CobolFormats.timestamp26("                          ")).isNull();
    assertThat(CobolFormats.date10("2014-11-20")).isEqualTo(java.time.LocalDate.of(2014, 11, 20));
    assertThat(CobolFormats.date10("          ")).isNull();
  }
}
