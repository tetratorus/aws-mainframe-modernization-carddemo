package com.carddemo.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransactionIdGeneratorTest {

  private final TransactionRepository repo = mock(TransactionRepository.class);
  private final TransactionIdGenerator generator = new TransactionIdGenerator(repo);

  private static Transaction withId(String id) {
    return new Transaction(
        id, "01", 1, "S", "D", BigDecimal.ONE, 1L, null, null, null, "C", null, null);
  }

  @Test
  void incrementsHighestExistingId() {
    when(repo.findFirstByOrderByTranIdDesc()).thenReturn(Optional.of(withId("0000000000000300")));
    assertThat(generator.next()).isEqualTo("0000000000000301");
  }

  @Test
  void startsAtOneWhenEmpty() {
    when(repo.findFirstByOrderByTranIdDesc()).thenReturn(Optional.empty());
    assertThat(generator.next()).isEqualTo("0000000000000001");
  }

  @Test
  void truncatesLikePic9of16() {
    assertThat(TransactionIdGenerator.format(new BigInteger("10000000000000000")))
        .isEqualTo("0000000000000000");
  }
}
