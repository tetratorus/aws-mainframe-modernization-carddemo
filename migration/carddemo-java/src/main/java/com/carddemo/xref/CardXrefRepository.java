package com.carddemo.xref;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardXrefRepository extends JpaRepository<CardXref, String> {

  /**
   * Alternate-index read (CXACAIX) keyed by account: VSAM returns the first record for the key, so
   * the lowest card number is used for determinism.
   */
  Optional<CardXref> findFirstByAcctIdOrderByCardNumAsc(long acctId);
}
