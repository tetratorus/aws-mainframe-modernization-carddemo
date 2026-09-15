package com.carddemo.transaction;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
  Optional<Transaction> findFirstByOrderByTranIdDesc();

  /** Transaction-scoped mutex for TRAN-ID allocation; released automatically at commit/rollback. */
  @Query(value = "SELECT pg_advisory_xact_lock(:key)", nativeQuery = true)
  Object lockTranIdAllocation(long key);
}
